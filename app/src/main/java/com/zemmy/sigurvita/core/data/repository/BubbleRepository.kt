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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap

private const val TAG = "BubbleRepository"

private val normPointsCache = LruCache<Int, List<Pair<Float, Float>>>(300)

// ~0.33 probability. 128 = 0.5. Lower keeps thin tails, higher tightens the outline. Try 70..110.
private const val CONTOUR_LEVEL = 90f

/** Morphological closing (3x3): bridges gaps up to 2 cells so thin tails don't fragment. */
private fun closeMask(src: BooleanArray, sz: Int): BooleanArray {
    val dilated = BooleanArray(src.size)
    for (y in 0 until sz) {
        for (x in 0 until sz) {
            var on = false
            loop@ for (dy in -1..1) {
                val yy = y + dy
                if (yy !in 0..<sz) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx !in 0..<sz) continue
                    if (src[yy * sz + xx]) { on = true; break@loop }
                }
            }
            dilated[y * sz + x] = on
        }
    }
    val out = BooleanArray(src.size)
    for (y in 0 until sz) {
        for (x in 0 until sz) {
            var all = true
            loop@ for (dy in -1..1) {
                val yy = y + dy
                if (yy !in 0..<sz) continue // outside the grid counts as set, so edges don't erode
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx !in 0..<sz) continue
                    if (!dilated[yy * sz + xx]) { all = false; break@loop }
                }
            }
            out[y * sz + x] = all
        }
    }
    return out
}

/**
 * Extracts a smoothed list of normalized page coordinates `(normX, normY)` (0.0..1.0)
 * along the speech bubble contour using 48 radial raycasts and 3-point moving average smoothing,
 * or sharp rectangular corner points for square/rectangular boxes.
 */
fun extractBubbleNormalizedPoints(
    bubble: DetectedBubble,
    lb: LetterboxParams
): List<Pair<Float, Float>> {
    val cacheKey = System.identityHashCode(bubble)
    val cached = normPointsCache.get(cacheKey)
    if (cached != null) return cached

    val mask = bubble.mask
    val sz = mask.size
    if (sz <= 0 || mask.probs.size < sz * sz) return emptyList()

    val level = CONTOUR_LEVEL
    val total = sz * sz
    val raw = FloatArray(total) { (mask.probs[it].toInt() and 0xFF).toFloat() }

    // 1. Threshold, bridge small gaps (thin tails often fragment), keep the largest component
    val strong = BooleanArray(total) { raw[it] >= level }
    val bridged = closeMask(strong, sz)

    val label = IntArray(total)
    val stack = IntArray(total)
    var bestLabel = 0
    var bestSize = 0
    var nextLabel = 1
    for (start in 0 until total) {
        if (!bridged[start] || label[start] != 0) continue
        var top = 0
        stack[top++] = start
        label[start] = nextLabel
        var size = 0
        while (top > 0) {
            val idx = stack[--top]
            size++
            val x = idx % sz
            val y = idx / sz
            if (x > 0) { val n = idx - 1; if (bridged[n] && label[n] == 0) { label[n] = nextLabel; stack[top++] = n } }
            if (x < sz - 1) { val n = idx + 1; if (bridged[n] && label[n] == 0) { label[n] = nextLabel; stack[top++] = n } }
            if (y > 0) { val n = idx - sz; if (bridged[n] && label[n] == 0) { label[n] = nextLabel; stack[top++] = n } }
            if (y < sz - 1) { val n = idx + sz; if (bridged[n] && label[n] == 0) { label[n] = nextLabel; stack[top++] = n } }
        }
        if (size > bestSize) { bestSize = size; bestLabel = nextLabel }
        nextLabel++
    }
    if (bestSize < 4) return emptyList()
    val inside = BooleanArray(total) { label[it] == bestLabel }

    // 2. Fill holes: flood the outside from the grid border, anything unreached is a hole
    val outside = BooleanArray(total)
    var sp = 0
    fun seed(i: Int) {
        if (!inside[i] && !outside[i]) { outside[i] = true; stack[sp++] = i }
    }
    for (i in 0 until sz) { seed(i); seed((sz - 1) * sz + i); seed(i * sz); seed(i * sz + sz - 1) }
    while (sp > 0) {
        val idx = stack[--sp]
        val x = idx % sz
        val y = idx / sz
        if (x > 0) seed(idx - 1)
        if (x < sz - 1) seed(idx + 1)
        if (y > 0) seed(idx - sz)
        if (y < sz - 1) seed(idx + sz)
    }
    for (i in 0 until total) if (!inside[i] && !outside[i]) inside[i] = true

    // Soft field used for sub-cell edge interpolation
    val field = FloatArray(total) { i ->
        when {
            inside[i] -> if (raw[i] >= level) raw[i] else 170f
            raw[i] >= level -> 0f // belongs to a discarded component
            else -> raw[i]
        }
    }

    var minX = sz; var maxX = -1; var minY = sz; var maxY = -1; var count = 0
    for (y in 0 until sz) {
        for (x in 0 until sz) {
            if (inside[y * sz + x]) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                count++
            }
        }
    }
    if (count < 4 || maxX <= minX || maxY <= minY) return emptyList()

    val wGrid = maxX - minX + 1
    val hGrid = maxY - minY + 1
    val fillRatio = count.toFloat() / (wGrid * hGrid).toFloat()
    val scaleGridToLb = lb.inputSize.toFloat() / sz.toFloat()

    // Cell-edge coordinates (cell i spans [i, i+1)) -> page-normalized
    fun gridToNorm(gx: Float, gy: Float): Pair<Float, Float> {
        val lbX = gx * scaleGridToLb
        val lbY = gy * scaleGridToLb
        val normX = ((lbX - lb.padX) / (lb.originalWidth * lb.gain)).coerceIn(0f, 1f)
        val normY = ((lbY - lb.padY) / (lb.originalHeight * lb.gain)).coerceIn(0f, 1f)
        return Pair(normX, normY)
    }

    // Literal rectangle only for genuinely rectangular masks (captions, narration boxes)
    if (wGrid >= 4 && hGrid >= 4 && fillRatio >= 0.93f) {
        val (normLeft, normTop) = gridToNorm(minX.toFloat(), minY.toFloat())
        val (normRight, normBottom) = gridToNorm(maxX.toFloat() + 1f, maxY.toFloat() + 1f)
        val rectPoints = listOf(
            Pair(normLeft, normTop),
            Pair(normRight, normTop),
            Pair(normRight, normBottom),
            Pair(normLeft, normBottom)
        )
        normPointsCache.put(cacheKey, rectPoints)
        return rectPoints
    }

    // 3. Marching squares with linear interpolation, padded so contours always close
    fun f(x: Int, y: Int): Float =
        if (x < 0 || y < 0 || x >= sz || y >= sz) 0f else field[y * sz + x]

    fun t(a: Float, b: Float): Float {
        val d = b - a
        return if (kotlin.math.abs(d) < 1e-6f) 0.5f else ((level - a) / d).coerceIn(0f, 1f)
    }

    val w = sz + 3
    fun hId(x: Int, y: Int): Long = (((y + 1).toLong() * w + (x + 1)) shl 1)
    fun vId(x: Int, y: Int): Long = (((y + 1).toLong() * w + (x + 1)) shl 1) or 1L

    val pts = HashMap<Long, Pair<Float, Float>>()
    val adj = HashMap<Long, MutableList<Long>>()
    fun link(a: Long, b: Long) {
        adj.getOrPut(a) { mutableListOf() }.add(b)
        adj.getOrPut(b) { mutableListOf() }.add(a)
    }

    for (y in (minY - 1)..maxY) {
        for (x in (minX - 1)..maxX) {
            val f0 = f(x, y)
            val f1 = f(x + 1, y)
            val f2 = f(x + 1, y + 1)
            val f3 = f(x, y + 1)
            val ci = (if (f0 >= level) 8 else 0) or (if (f1 >= level) 4 else 0) or
                    (if (f2 >= level) 2 else 0) or (if (f3 >= level) 1 else 0)
            if (ci == 0 || ci == 15) continue

            val topId = hId(x, y)
            val bottomId = hId(x, y + 1)
            val leftId = vId(x, y)
            val rightId = vId(x + 1, y)
            pts[topId] = Pair(x + t(f0, f1), y.toFloat())
            pts[bottomId] = Pair(x + t(f3, f2), (y + 1).toFloat())
            pts[leftId] = Pair(x.toFloat(), y + t(f0, f3))
            pts[rightId] = Pair((x + 1).toFloat(), y + t(f1, f2))

            when (ci) {
                1, 14 -> link(leftId, bottomId)
                2, 13 -> link(bottomId, rightId)
                3, 12 -> link(leftId, rightId)
                4, 11 -> link(topId, rightId)
                5 -> { link(leftId, topId); link(bottomId, rightId) }
                6, 9 -> link(topId, bottomId)
                7, 8 -> link(leftId, topId)
                10 -> { link(topId, rightId); link(leftId, bottomId) }
            }
        }
    }
    if (adj.isEmpty()) return emptyList()

    // Trace every loop, keep the longest
    val visited = HashSet<Long>()
    var best: List<Long> = emptyList()
    for (startId in adj.keys) {
        if (startId in visited) continue
        val loop = ArrayList<Long>()
        var prev = -1L
        var cur = startId
        while (true) {
            visited.add(cur)
            loop.add(cur)
            val next = adj[cur]?.firstOrNull { it != prev && it !in visited } ?: break
            prev = cur
            cur = next
        }
        if (loop.size > best.size) best = loop
    }

    val loopPts = best.mapNotNull { pts[it] }
    if (loopPts.size < 3) return emptyList()

    // 4. Light smoothing, then map to page space. +0.5: proto samples sit at cell centers.
    val n = loopPts.size
    val normPoints = ArrayList<Pair<Float, Float>>(n)
    for (i in 0 until n) {
        val prev = loopPts[(i - 1 + n) % n]
        val curr = loopPts[i]
        val next = loopPts[(i + 1) % n]
        val smX = prev.first * 0.15f + curr.first * 0.7f + next.first * 0.15f
        val smY = prev.second * 0.15f + curr.second * 0.7f + next.second * 0.15f
        normPoints.add(gridToNorm(smX + 0.5f, smY + 0.5f))
    }

    normPointsCache.put(cacheKey, normPoints)
    return normPoints
}

class BubbleRepository(
    private val context: Context,
    private val detector1: BubbleDetector = BubbleDetector(context),
    private val detector2: BubbleDetector = BubbleDetector(context)
) {

    // Cache up to 100 pages in memory
    private val cache = LruCache<Pair<Pair<Int, Int>, Float>, PageBubbles>(100)
    // High-resolution (2000px) bitmap cache for bubble magnifier (2 pages)
    private val highResCache = LruCache<Pair<Int, Int>, Bitmap>(2)

    private val detectorMutex = Mutex()
    private var useDetector1 = true

    private suspend fun <T> withDetector(block: suspend (BubbleDetector) -> T): T {
        val selectedDetector = detectorMutex.withLock {
            if (useDetector1) {
                useDetector1 = false
                detector1
            } else {
                useDetector1 = true
                detector2
            }
        }
        return block(selectedDetector)
    }

    suspend fun getPageBubbles(chapterId: Int, page: ReaderPage, confidence: Float = 0.10f): PageBubbles? = withContext(Dispatchers.IO) {
        val key = Pair(Pair(chapterId, page.index), confidence)
        val cached = cache.get(key)
        if (cached != null) {
            return@withContext cached
        }

        try {
            // Check sidecar detections.json if reading a local chapter
            if (page.isLocal || page.imageUrl.startsWith("/") || page.imageUrl.startsWith("file://")) {
                val filePath = page.imageUrl.removePrefix("file://")
                val localFile = java.io.File(filePath)
                val dir = if (localFile.isDirectory) localFile else localFile.parentFile
                if (dir != null && dir.exists()) {
                    val cacheFile = java.io.File(dir, "detections.json")
                    if (cacheFile.exists()) {
                        val sidecarCache = com.zemmy.sigurvita.core.ml.DetectionsCacheHelper.loadCache(cacheFile)
                        val pageData = sidecarCache?.pages?.find { it.pageIndex == page.index }
                        if (sidecarCache != null && pageData != null &&
                            sidecarCache.modelVersion == com.zemmy.sigurvita.core.ml.CURRENT_DETECTION_VERSION) {
                            val result = com.zemmy.sigurvita.core.ml.DetectionsCacheHelper.toPageBubbles(pageData, page.index)
                            cache.put(key, result)
                            return@withContext result
                        }
                    }
                }
            }

            val bitmap = PageBitmapLoader.decodeForInference(context, page, targetSize = 1024) ?: return@withContext null
            val result = withDetector { det -> det.detect(bitmap, page.index, confidence) }
            cache.put(key, result)
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect speech bubbles for chapter $chapterId page ${page.index}", e)
            return@withContext null
        }
    }

data class BubbleCropResult(
    val bitmap: Bitmap,
    val cropLeftNorm: Float,
    val cropTopNorm: Float,
    val cropRightNorm: Float,
    val cropBottomNorm: Float,
    val anchorNormX: Float,
    val anchorNormY: Float,
    val relTipX: Float,
    val relTipY: Float,
    val isLiteralBox: Boolean
)

    /**
     * Crops the bubble region out of the page at high resolution for the magnifier overlay.
     * Optionally draws the smooth outline if [speechBubbleBoxesEnabled] is true.
     */
    suspend fun cropBubble(
        context: Context,
        page: ReaderPage,
        bubble: DetectedBubble,
        letterbox: LetterboxParams,
        speechBubbleBoxesEnabled: Boolean,
        chapterId: Int = 0
    ): BubbleCropResult? = withContext(Dispatchers.IO) {
        try {
            val key = Pair(chapterId, page.index)
            var fullBitmap = if (chapterId > 0) highResCache.get(key) else null
            if (fullBitmap == null || fullBitmap.isRecycled) {
                fullBitmap = PageBitmapLoader.decodeForInference(context, page, targetSize = 2000) ?: return@withContext null
                if (chapterId > 0) {
                    highResCache.put(key, fullBitmap)
                }
            }
            val w = fullBitmap.width.toFloat()
            val h = fullBitmap.height.toFloat()

            val normPoints = extractBubbleNormalizedPoints(bubble, letterbox)

            val minNormX = if (normPoints.isNotEmpty()) normPoints.minOf { it.first } else bubble.box.left
            val maxNormX = if (normPoints.isNotEmpty()) normPoints.maxOf { it.first } else bubble.box.right
            val minNormY = if (normPoints.isNotEmpty()) normPoints.minOf { it.second } else bubble.box.top
            val maxNormY = if (normPoints.isNotEmpty()) normPoints.maxOf { it.second } else bubble.box.bottom

            val bw = maxNormX - minNormX
            val bh = maxNormY - minNormY

            val marginX = (bw * 0.08f * w).toInt().coerceAtLeast(16)
            val marginY = (bh * 0.08f * h).toInt().coerceAtLeast(16)

            val left = (minNormX * w - marginX).toInt().coerceIn(0, fullBitmap.width - 1)
            val top = (minNormY * h - marginY).toInt().coerceIn(0, fullBitmap.height - 1)
            val right = (maxNormX * w + marginX).toInt().coerceIn(left + 1, fullBitmap.width)
            val bottom = (maxNormY * h + marginY).toInt().coerceIn(top + 1, fullBitmap.height)

            val cropW = right - left
            val cropH = bottom - top
            if (cropW <= 0 || cropH <= 0) return@withContext null

            val isLiteralBox = normPoints.size == 4

            val cropLeftNorm = left.toFloat() / w
            val cropTopNorm = top.toFloat() / h
            val cropRightNorm = right.toFloat() / w
            val cropBottomNorm = bottom.toFloat() / h
            val cropWNorm = cropRightNorm - cropLeftNorm
            val cropHNorm = cropBottomNorm - cropTopNorm

            var anchorNormX = minNormX
            var anchorNormY = minNormY

            if (!isLiteralBox && normPoints.size >= 5) {
                val bh = maxNormY - minNormY

                // Check for a bottom tail protruding below the main bubble body
                val bottomThreshold = minNormY + 0.85f * bh
                val bottomTailPt = normPoints.filter { it.second > bottomThreshold }
                    .maxByOrNull { it.second }

                if (bottomTailPt != null && (bottomTailPt.second - bottomThreshold) > 0.02f * bh) {
                    anchorNormX = bottomTailPt.first
                    anchorNormY = bottomTailPt.second
                } else {
                    // Check for a top tail protruding above the main bubble body
                    val topThreshold = minNormY + 0.15f * bh
                    val topTailPt = normPoints.filter { it.second < topThreshold }
                        .minByOrNull { it.second }

                    if (topTailPt != null && (topThreshold - topTailPt.second) > 0.02f * bh) {
                        anchorNormX = topTailPt.first
                        anchorNormY = topTailPt.second
                    }
                }
            }

            val relTipX = ((anchorNormX - cropLeftNorm) / cropWNorm).coerceIn(0f, 1f)
            val relTipY = ((anchorNormY - cropTopNorm) / cropHNorm).coerceIn(0f, 1f)

            val masked = maskAndOutlineBubbleDirect(
                fullBitmap = fullBitmap,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                pageW = w,
                pageH = h,
                normPoints = normPoints,
                drawOutline = speechBubbleBoxesEnabled
            )

            return@withContext BubbleCropResult(
                bitmap = masked,
                cropLeftNorm = cropLeftNorm,
                cropTopNorm = cropTopNorm,
                cropRightNorm = cropRightNorm,
                cropBottomNorm = cropBottomNorm,
                anchorNormX = anchorNormX,
                anchorNormY = anchorNormY,
                relTipX = relTipX,
                relTipY = relTipY,
                isLiteralBox = isLiteralBox
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop bubble", e)
            return@withContext null
        }
    }

    private fun maskAndOutlineBubbleDirect(
        fullBitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        pageW: Float,
        pageH: Float,
        normPoints: List<Pair<Float, Float>>,
        drawOutline: Boolean
    ): Bitmap {
        val cropW = right - left
        val cropH = bottom - top
        if (cropW <= 0 || cropH <= 0) return fullBitmap

        val maskedBmp = createBitmap(cropW, cropH)
        val canvas = android.graphics.Canvas(maskedBmp)
        val srcRect = android.graphics.Rect(left, top, right, bottom)
        val dstRect = android.graphics.RectF(0f, 0f, cropW.toFloat(), cropH.toFloat())

        if (normPoints.size >= 3) {
            val localPoints = normPoints.map { (normX, normY) ->
                Pair(normX * pageW - left, normY * pageH - top)
            }

            // Inflate path outward from centroid to ensure speech bubble edges and tails are never clipped
            val cx = localPoints.map { it.first }.average().toFloat()
            val cy = localPoints.map { it.second }.average().toFloat()
            val expandPx = (minOf(cropW, cropH) * 0.015f).coerceIn(2f, 6f)

            val inflatedPoints = localPoints.map { (px, py) ->
                val dx = px - cx
                val dy = py - cy
                val dist = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-3f)
                Pair(px + (dx / dist) * expandPx, py + (dy / dist) * expandPx)
            }

            val path = android.graphics.Path()
            val start = inflatedPoints[0]
            path.moveTo(start.first, start.second)

            if (inflatedPoints.size == 4) {
                path.lineTo(inflatedPoints[1].first, inflatedPoints[1].second)
                path.lineTo(inflatedPoints[2].first, inflatedPoints[2].second)
                path.lineTo(inflatedPoints[3].first, inflatedPoints[3].second)
            } else {
                for (i in inflatedPoints.indices) {
                    val p0 = inflatedPoints[i]
                    val p1 = inflatedPoints[(i + 1) % inflatedPoints.size]
                    val midX = (p0.first + p1.first) / 2f
                    val midY = (p0.second + p1.second) / 2f
                    path.quadTo(p0.first, p0.second, midX, midY)
                }
            }
            path.close()

            val antiAliasPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

            canvas.clipPath(path)
            canvas.drawBitmap(fullBitmap, srcRect, dstRect, antiAliasPaint)

            if (drawOutline) {
                val strokeScale = (minOf(cropW, cropH) / 150f).coerceIn(1.5f, 4.0f)

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
            }
        } else {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(fullBitmap, srcRect, dstRect, paint)
        }

        return maskedBmp
    }
    fun clearChapter(chapterId: Int) {
        val keysToRemove = cache.snapshot().keys.filter { it.first.first == chapterId }
        for (k in keysToRemove) {
            cache.remove(k)
        }
        val highResKeysToRemove = highResCache.snapshot().keys.filter { it.first == chapterId }
        for (k in highResKeysToRemove) {
            highResCache.remove(k)
        }
    }
}