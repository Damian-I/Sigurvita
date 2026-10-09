package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.zemmy.sigurvita.core.ml.BubbleDetector
import com.zemmy.sigurvita.core.ml.PageBitmapLoader
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "BubbleRepository"

/**
 * Extracts a smoothed list of normalized page coordinates `(normX, normY)` (0.0..1.0)
 * along the speech bubble contour using 48 radial raycasts and 3-point moving average smoothing,
 * or sharp rectangular corner points for square/rectangular boxes.
 */
fun extractBubbleNormalizedPoints(
    bubble: DetectedBubble,
    lb: LetterboxParams,
    numSteps: Int = 48
): List<Pair<Float, Float>> {
    val mask = bubble.mask
    val sz = mask.size
    if (sz <= 0 || mask.probs.size < sz * sz) return emptyList()

    val maskThreshold = 50 // prob >= 50 (~0.20 probability)

    var minX = sz
    var maxX = -1
    var minY = sz
    var maxY = -1
    var count = 0
    var sumX = 0f
    var sumY = 0f

    for (y in 0 until sz) {
        for (x in 0 until sz) {
            val prob = mask.probs[y * sz + x].toInt() and 0xFF
            if (prob >= maskThreshold) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                sumX += x
                sumY += y
                count++
            }
        }
    }

    if (count < 4 || maxX <= minX || maxY <= minY) return emptyList()

    val wGrid = maxX - minX + 1
    val hGrid = maxY - minY + 1
    val boxArea = wGrid * hGrid
    val fillRatio = count.toFloat() / boxArea.toFloat()

    val scaleGridToLb = lb.inputSize.toFloat() / sz.toFloat()

    fun gridToNorm(gx: Float, gy: Float): Pair<Float, Float> {
        val lbX = gx * scaleGridToLb
        val lbY = gy * scaleGridToLb
        val normX = ((lbX - lb.padX) / (lb.originalWidth * lb.gain)).coerceIn(0f, 1f)
        val normY = ((lbY - lb.padY) / (lb.originalHeight * lb.gain)).coerceIn(0f, 1f)
        return Pair(normX, normY)
    }

    // Check if the bubble is a rectangular / square box (high fill ratio inside grid bounding box)
    if (wGrid >= 4 && hGrid >= 4 && fillRatio >= 0.83f) {
        val (normLeft, normTop) = gridToNorm(minX.toFloat(), minY.toFloat())
        val (normRight, normBottom) = gridToNorm(maxX.toFloat() + 1f, maxY.toFloat() + 1f)

        return listOf(
            Pair(normLeft, normTop),
            Pair(normRight, normTop),
            Pair(normRight, normBottom),
            Pair(normLeft, normBottom)
        )
    }

    // Otherwise calculate 48-ray radial contour for curved / oval bubbles
    val cx = sumX / count
    val cy = sumY / count

    val rawPoints = ArrayList<Pair<Float, Float>>(numSteps)
    for (i in 0 until numSteps) {
        val angle = i * (2f * Math.PI.toFloat() / numSteps)
        val dx = kotlin.math.cos(angle.toDouble()).toFloat()
        val dy = kotlin.math.sin(angle.toDouble()).toFloat()

        var maxR = 0f
        var r = 0.5f
        val maxDist = sz.toFloat()

        while (r < maxDist) {
            val px = (cx + dx * r).toInt()
            val py = (cy + dy * r).toInt()
            if (px !in 0 until sz || py !in 0 until sz) break
            val prob = mask.probs[py * sz + px].toInt() and 0xFF
            if (prob >= maskThreshold) {
                maxR = r
            }
            r += 0.5f
        }

        if (maxR > 0f) {
            val gridX = cx + dx * maxR
            val gridY = cy + dy * maxR
            rawPoints.add(Pair(gridX, gridY))
        }
    }

    if (rawPoints.size < 3) return emptyList()

    // Smooth radial points with 3-point moving average
    val n = rawPoints.size
    val normPoints = ArrayList<Pair<Float, Float>>(n)

    for (i in 0 until n) {
        val prev = rawPoints[(i - 1 + n) % n]
        val curr = rawPoints[i]
        val next = rawPoints[(i + 1) % n]

        val smGridX = prev.first * 0.25f + curr.first * 0.5f + next.first * 0.25f
        val smGridY = prev.second * 0.25f + curr.second * 0.5f + next.second * 0.25f

        normPoints.add(gridToNorm(smGridX, smGridY))
    }

    return normPoints
}

class BubbleRepository(
    private val context: Context,
    private val detector: BubbleDetector = BubbleDetector(context)
) {

    // Cache up to 30 pages in memory
    private val cache = LruCache<Pair<Pair<Int, Int>, Float>, PageBubbles>(30)

    suspend fun getPageBubbles(chapterId: Int, page: ReaderPage, confidence: Float = 0.10f): PageBubbles? = withContext(Dispatchers.IO) {
        val key = Pair(Pair(chapterId, page.index), confidence)
        val cached = cache.get(key)
        if (cached != null) {
            return@withContext cached
        }

        try {
            val bitmap = PageBitmapLoader.decodeForInference(context, page) ?: return@withContext null
            val result = detector.detect(bitmap, page.index, confidence)
            cache.put(key, result)
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect speech bubbles for chapter $chapterId page ${page.index}", e)
            return@withContext null
        }
    }

    /**
     * Crops the bubble region out of the page at high resolution for the magnifier overlay.
     * Optionally draws the smooth outline if [speechBubbleBoxesEnabled] is true.
     */
    suspend fun cropBubble(
        context: Context,
        page: ReaderPage,
        bubble: DetectedBubble,
        letterbox: LetterboxParams,
        speechBubbleBoxesEnabled: Boolean
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val fullBitmap = PageBitmapLoader.decodeForInference(context, page, targetSize = 2000) ?: return@withContext null
            val w = fullBitmap.width.toFloat()
            val h = fullBitmap.height.toFloat()

            val normPoints = extractBubbleNormalizedPoints(bubble, letterbox)

            val minNormX = if (normPoints.isNotEmpty()) normPoints.minOf { it.first } else bubble.box.left
            val maxNormX = if (normPoints.isNotEmpty()) normPoints.maxOf { it.first } else bubble.box.right
            val minNormY = if (normPoints.isNotEmpty()) normPoints.minOf { it.second } else bubble.box.top
            val maxNormY = if (normPoints.isNotEmpty()) normPoints.maxOf { it.second } else bubble.box.bottom

            val bw = maxNormX - minNormX
            val bh = maxNormY - minNormY

            // 4% margin around the bubble bounds so text/border is not clipped
            val marginX = (bw * 0.04f * w).toInt().coerceAtLeast(6)
            val marginY = (bh * 0.04f * h).toInt().coerceAtLeast(6)

            val left = (minNormX * w - marginX).toInt().coerceIn(0, fullBitmap.width - 1)
            val top = (minNormY * h - marginY).toInt().coerceIn(0, fullBitmap.height - 1)
            val right = (maxNormX * w + marginX).toInt().coerceIn(left + 1, fullBitmap.width)
            val bottom = (maxNormY * h + marginY).toInt().coerceIn(top + 1, fullBitmap.height)

            val cropW = right - left
            val cropH = bottom - top
            if (cropW <= 0 || cropH <= 0) return@withContext null

            val crop = Bitmap.createBitmap(fullBitmap, left, top, cropW, cropH)
            if (speechBubbleBoxesEnabled) {
                return@withContext applyBubbleOutline(crop, left, top, w, h, normPoints)
            } else {
                return@withContext crop
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop bubble", e)
            return@withContext null
        }
    }

    private fun applyBubbleOutline(
        crop: Bitmap,
        cropLeft: Int,
        cropTop: Int,
        pageW: Float,
        pageH: Float,
        normPoints: List<Pair<Float, Float>>
    ): Bitmap {
        val w = crop.width
        val h = crop.height
        if (normPoints.size < 3) return crop

        val localPoints = normPoints.map { (normX, normY) ->
            Pair(normX * pageW - cropLeft, normY * pageH - cropTop)
        }

        val path = android.graphics.Path()
        val start = localPoints[0]
        path.moveTo(start.first, start.second)

        if (localPoints.size == 4) {
            path.lineTo(localPoints[1].first, localPoints[1].second)
            path.lineTo(localPoints[2].first, localPoints[2].second)
            path.lineTo(localPoints[3].first, localPoints[3].second)
        } else {
            for (i in localPoints.indices) {
                val p0 = localPoints[i]
                val p1 = localPoints[(i + 1) % localPoints.size]
                val midX = (p0.first + p1.first) / 2f
                val midY = (p0.second + p1.second) / 2f
                path.quadTo(p0.first, p0.second, midX, midY)
            }
        }
        path.close()

        val out = Bitmap.createBitmap(crop, 0, 0, w, h, null, true)
        val canvas = android.graphics.Canvas(out)

        val strokeScale = (minOf(w, h) / 150f).coerceIn(1.5f, 4.0f)

        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            color = android.graphics.Color.BLACK
            strokeWidth = 3.0f * strokeScale
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        canvas.drawPath(path, strokePaint)

        strokePaint.color = android.graphics.Color.argb(255, 255, 229, 0)
        strokePaint.strokeWidth = 1.8f * strokeScale
        canvas.drawPath(path, strokePaint)

        return out
    }
    fun clearChapter(chapterId: Int) {
        val keysToRemove = cache.snapshot().keys.filter { it.first.first == chapterId }
        for (k in keysToRemove) {
            cache.remove(k)
        }
    }
}