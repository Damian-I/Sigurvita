package com.zemmy.sigurvita.core.ml

import android.util.Log
import com.zemmy.sigurvita.core.model.BubbleMask
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

private data class Candidate(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val score: Float,
    val classId: Int,
    val maskCoeffs: FloatArray,
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float
)

class YoloSegDecoder(private val config: BubbleDetectorConfig = BubbleDetectorConfig()) {

    fun decode(
        detArray: Array<Array<FloatArray>>, // [1, 37, 8400]
        protoArray: Array<Array<Array<FloatArray>>>, // [1, 160, 160, 32]
        letterbox: LetterboxParams
    ): List<DetectedBubble> {
        val channels = detArray[0].size
        val numAnchors = detArray[0][0].size
        val numCoeffs = 32
        val nc = channels - 4 - numCoeffs

        val candidates = mutableListOf<Candidate>()

        var absMaxScore = -999f

        for (a in 0 until numAnchors) {
            var maxScore = 0f
            var maxClass = 0
            for (c in 0 until nc) {
                val score = detArray[0][4 + c][a]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = c
                }
                if (score > absMaxScore) absMaxScore = score
            }

            if (maxScore >= config.confThreshold) {
                val cx = detArray[0][0][a].coerceIn(0f, 1f)
                val cy = detArray[0][1][a].coerceIn(0f, 1f)
                val w = detArray[0][2][a].coerceIn(0f, 1f)
                val h = detArray[0][3][a].coerceIn(0f, 1f)

                val left = (cx - w / 2f).coerceIn(0f, 1f)
                val top = (cy - h / 2f).coerceIn(0f, 1f)
                val right = (cx + w / 2f).coerceIn(0f, 1f)
                val bottom = (cy + h / 2f).coerceIn(0f, 1f)

                val coeffs = FloatArray(numCoeffs) { k ->
                    detArray[0][4 + nc + k][a]
                }

                candidates.add(
                    Candidate(
                        cx = cx, cy = cy, w = w, h = h,
                        score = maxScore, classId = maxClass,
                        maskCoeffs = coeffs,
                        leftPx = left, topPx = top, rightPx = right, bottomPx = bottom
                    )
                )
            }
        }

        Log.i("YoloSegDecoder", "Max candidate score across $numAnchors anchors: absMaxScore=$absMaxScore, candidatesAboveThreshold=${candidates.size}")

        // Non-Maximum Suppression (NMS)
        candidates.sortByDescending { it.score }
        val nmsKept = mutableListOf<Candidate>()

        for (cand in candidates) {
            var overlap = false
            for (kept in nmsKept) {
                if (calculateIoU(cand, kept) >= config.iouThreshold) {
                    overlap = true
                    break
                }
            }
            if (!overlap) {
                nmsKept.add(cand)
                if (nmsKept.size >= config.maxDetections) break
            }
        }

        // Decode Prototype Mask
        val isProtoNHWC = protoArray[0].size > 32 // e.g. 160x160x32
        val protoSize = if (isProtoNHWC) protoArray[0].size else protoArray[0][0].size

        val detectedBubbles = mutableListOf<DetectedBubble>()

        for ((idx, cand) in nmsKept.withIndex()) {
            val maskProbs = ByteArray(protoSize * protoSize)

            val minX = (cand.leftPx * protoSize).toInt().coerceIn(0, protoSize - 1)
            val maxX = (cand.rightPx * protoSize).toInt().coerceIn(0, protoSize - 1)
            val minY = (cand.topPx * protoSize).toInt().coerceIn(0, protoSize - 1)
            val maxY = (cand.bottomPx * protoSize).toInt().coerceIn(0, protoSize - 1)

            for (py in minY..maxY) {
                for (px in minX..maxX) {
                    var sum = 0f
                    if (isProtoNHWC) {
                        val cellCoeffs = protoArray[0][py][px]
                        for (k in 0 until numCoeffs) {
                            sum += cand.maskCoeffs[k] * cellCoeffs[k]
                        }
                    } else {
                        for (k in 0 until numCoeffs) {
                            sum += cand.maskCoeffs[k] * protoArray[0][k][py][px]
                        }
                    }

                    val prob = 1f / (1f + exp(-sum))
                    if (prob >= config.maskThreshold) {
                        maskProbs[py * protoSize + px] = (prob * 255f).toInt().toByte()
                    }
                }
            }

            // Convert letterbox normalized coords (0..1 in model input) to page-normalized coords (0..1 in original page)
            val (normLeft, normTop) = letterbox.letterboxToNorm(cand.leftPx * letterbox.inputSize, cand.topPx * letterbox.inputSize)
            val (normRight, normBottom) = letterbox.letterboxToNorm(cand.rightPx * letterbox.inputSize, cand.bottomPx * letterbox.inputSize)

            val normRect = NormRect(
                left = normLeft,
                top = normTop,
                right = normRight,
                bottom = normBottom
            )

            detectedBubbles.add(
                DetectedBubble(
                    id = idx,
                    box = normRect,
                    score = cand.score,
                    classId = cand.classId,
                    mask = BubbleMask(protoSize, maskProbs)
                )
            )
        }

        return detectedBubbles
    }

    private fun calculateIoU(c1: Candidate, c2: Candidate): Float {
        val interLeft = max(c1.leftPx, c2.leftPx)
        val interTop = max(c1.topPx, c2.topPx)
        val interRight = min(c1.rightPx, c2.rightPx)
        val interBottom = min(c1.bottomPx, c2.bottomPx)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val area1 = (c1.rightPx - c1.leftPx) * (c1.bottomPx - c1.topPx)
        val area2 = (c2.rightPx - c2.leftPx) * (c2.bottomPx - c2.topPx)

        val unionArea = area1 + area2 - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }
}

