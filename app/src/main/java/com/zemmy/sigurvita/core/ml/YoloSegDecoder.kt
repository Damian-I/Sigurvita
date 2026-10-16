package com.zemmy.sigurvita.core.ml

import android.util.Log
import com.zemmy.sigurvita.core.model.BubbleMask
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min


private const val TOUCH_LEVEL = 128
private const val MERGE_ENABLED = true // set false to see raw, unmerged detections
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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Candidate

        if (cx != other.cx) return false
        if (cy != other.cy) return false
        if (w != other.w) return false
        if (h != other.h) return false
        if (score != other.score) return false
        if (classId != other.classId) return false
        if (!maskCoeffs.contentEquals(other.maskCoeffs)) return false
        if (leftPx != other.leftPx) return false
        if (topPx != other.topPx) return false
        if (rightPx != other.rightPx) return false
        return bottomPx == other.bottomPx
    }

    override fun hashCode(): Int {
        var result = cx.hashCode()
        result = 31 * result + cy.hashCode()
        result = 31 * result + w.hashCode()
        result = 31 * result + h.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + classId.hashCode()
        result = 31 * result + maskCoeffs.contentHashCode()
        result = 31 * result + leftPx.hashCode()
        result = 31 * result + topPx.hashCode()
        result = 31 * result + rightPx.hashCode()
        result = 31 * result + bottomPx.hashCode()
        return result
    }
}

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

        val minConf = config.confThreshold
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

            if (maxScore >= minConf) {
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

        // Decode Prototype Mask with lower cutoff (0.25) to preserve full rectangular edges
        val isProtoNHWC = protoArray[0].size > 32 // e.g. 160x160x32
        val protoSize = if (isProtoNHWC) protoArray[0].size else protoArray[0][0].size

        val rawBubbles = mutableListOf<DetectedBubble>()

        for ((idx, cand) in nmsKept.withIndex()) {
            val maskProbs = ByteArray(protoSize * protoSize)

            val minX = ((cand.leftPx * protoSize).toInt() - 1).coerceIn(0, protoSize - 1)
            val maxX = ((cand.rightPx * protoSize).toInt() + 1).coerceIn(0, protoSize - 1)
            val minY = ((cand.topPx * protoSize).toInt() - 1).coerceIn(0, protoSize - 1)
            val maxY = ((cand.bottomPx * protoSize).toInt() + 1).coerceIn(0, protoSize - 1)

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
                    maskProbs[py * protoSize + px] = (prob * 255f).toInt().toByte()
                }
            }

            val (normLeft, normTop) = letterbox.letterboxToNorm(cand.leftPx * letterbox.inputSize, cand.topPx * letterbox.inputSize)
            val (normRight, normBottom) = letterbox.letterboxToNorm(cand.rightPx * letterbox.inputSize, cand.bottomPx * letterbox.inputSize)

            val normRect = NormRect(
                left = normLeft,
                top = normTop,
                right = normRight,
                bottom = normBottom
            )

            rawBubbles.add(
                DetectedBubble(
                    id = idx,
                    box = normRect,
                    score = cand.score,
                    classId = cand.classId,
                    mask = BubbleMask(protoSize, maskProbs)
                )
            )
        }

        // Merge overlapping/connected bubbles so extended speech bubbles get a single unified outline
        return if (MERGE_ENABLED) mergeConnectedBubbles(rawBubbles) else rawBubbles
    }

    /**
     * Merges overlapping or mask-connected bubble detections into single unified bubbles.
     */
    private fun mergeConnectedBubbles(bubbles: List<DetectedBubble>): List<DetectedBubble> {
        if (bubbles.size <= 1) return bubbles

        val list = bubbles.toMutableList()
        var mergedAny = true

        while (mergedAny) {
            mergedAny = false
            var i = 0
            while (i < list.size) {
                var j = i + 1
                while (j < list.size) {
                    val b1 = list[i]
                    val b2 = list[j]

                    val iou = calculateRectIoU(b1.box, b2.box)
                    val minAreaOverlap = calculateIntersectionOverMinArea(b1.box, b2.box)
                    val masksTouch = doMasksTouch(b1.box, b2.box, b1.mask, b2.mask)

                    if (iou >= 0.18f || minAreaOverlap >= 0.35f || masksTouch) {
                        val mergedBox = NormRect(
                            left = min(b1.box.left, b2.box.left),
                            top = min(b1.box.top, b2.box.top),
                            right = max(b1.box.right, b2.box.right),
                            bottom = max(b1.box.bottom, b2.box.bottom)
                        )

                        val sz = b1.mask.size
                        val mergedProbs = ByteArray(sz * sz)
                        for (k in 0 until sz * sz) {
                            val p1 = b1.mask.probs[k].toInt() and 0xFF
                            val p2 = b2.mask.probs[k].toInt() and 0xFF
                            mergedProbs[k] = max(p1, p2).toByte()
                        }

                        val mergedBubble = DetectedBubble(
                            id = b1.id,
                            box = mergedBox,
                            score = max(b1.score, b2.score),
                            classId = b1.classId,
                            mask = BubbleMask(sz, mergedProbs)
                        )

                        list[i] = mergedBubble
                        list.removeAt(j)
                        mergedAny = true
                    } else {
                        j++
                    }
                }
                i++
            }
        }

        return list.mapIndexed { index, b -> b.copy(id = index) }
    }

    private fun doMasksTouch(r1: NormRect, r2: NormRect, m1: BubbleMask, m2: BubbleMask): Boolean {
        val sz = m1.size
        if (sz != m2.size || sz == 0) return false

        // Fast normalized proximity gate (~2 mask pixels = 2/160 = 0.0125)
        val margin = 2f / sz
        if (r1.left > r2.right + margin || r2.left > r1.right + margin ||
            r1.top > r2.bottom + margin || r2.top > r1.bottom + margin) {
            return false
        }

        var sharedPixels = 0

        // Convert normalized bounding boxes to mask pixel coordinates
        val minX1 = (r1.left * sz).toInt().coerceIn(0, sz - 1)
        val maxX1 = (r1.right * sz).toInt().coerceIn(0, sz - 1)
        val minY1 = (r1.top * sz).toInt().coerceIn(0, sz - 1)
        val maxY1 = (r1.bottom * sz).toInt().coerceIn(0, sz - 1)

        val minX2 = (r2.left * sz).toInt().coerceIn(0, sz - 1)
        val maxX2 = (r2.right * sz).toInt().coerceIn(0, sz - 1)
        val minY2 = (r2.top * sz).toInt().coerceIn(0, sz - 1)
        val maxY2 = (r2.bottom * sz).toInt().coerceIn(0, sz - 1)

        val startX = maxOf(0, minOf(minX1, minX2) - 1)
        val endX = minOf(sz - 1, maxOf(maxX1, maxX2) + 1)
        val startY = maxOf(0, minOf(minY1, minY2) - 1)
        val endY = minOf(sz - 1, maxOf(maxY1, maxY2) + 1)

        for (y in startY..endY) {
            val rowOffset = y * sz
            for (x in startX..endX) {
                val p1 = m1.probs[rowOffset + x].toInt() and 0xFF
                if (p1 < TOUCH_LEVEL) continue

                val p2Center = m2.probs[rowOffset + x].toInt() and 0xFF
                val p2Up = if (y > 0) m2.probs[(y - 1) * sz + x].toInt() and 0xFF else 0
                val p2Down = if (y < sz - 1) m2.probs[(y + 1) * sz + x].toInt() and 0xFF else 0
                val p2Left = if (x > 0) m2.probs[rowOffset + x - 1].toInt() and 0xFF else 0
                val p2Right = if (x < sz - 1) m2.probs[rowOffset + x + 1].toInt() and 0xFF else 0

                if (p2Center >= TOUCH_LEVEL || p2Up >= TOUCH_LEVEL || p2Down >= TOUCH_LEVEL ||
                        p2Left >= TOUCH_LEVEL || p2Right >= TOUCH_LEVEL) {
                    sharedPixels++
                    if (sharedPixels >= 6) return true
                }
            }
        }
        return false
    }

    private fun calculateRectIoU(r1: NormRect, r2: NormRect): Float {
        val interLeft = max(r1.left, r2.left)
        val interTop = max(r1.top, r2.top)
        val interRight = min(r1.right, r2.right)
        val interBottom = min(r1.bottom, r2.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val area1 = (r1.right - r1.left) * (r1.bottom - r1.top)
        val area2 = (r2.right - r2.left) * (r2.bottom - r2.top)
        val unionArea = area1 + area2 - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun calculateIntersectionOverMinArea(r1: NormRect, r2: NormRect): Float {
        val interLeft = max(r1.left, r2.left)
        val interTop = max(r1.top, r2.top)
        val interRight = min(r1.right, r2.right)
        val interBottom = min(r1.bottom, r2.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val area1 = (r1.right - r1.left) * (r1.bottom - r1.top)
        val area2 = (r2.right - r2.left) * (r2.bottom - r2.top)
        val minArea = min(area1, area2)

        return if (minArea > 0f) interArea / minArea else 0f
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

