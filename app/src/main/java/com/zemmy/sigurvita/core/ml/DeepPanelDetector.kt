package com.zemmy.sigurvita.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.util.PanelInfo
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "DeepPanelDetector"

/**
 * Tunable minimum area ratio for panel detection.
 * DeepPanel Android defaults to 3% (0.03), but 0.8% (0.008) preserves small inset panels.
 */
const val MIN_PANEL_AREA_RATIO = 0.008f

class DeepPanelDetector(private val context: Context) {

    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var interpreter: Interpreter? = null
    private val modelInputImageSize = 224

    // Reusable buffers to avoid per-frame GC pressure
    private var reuseInputBuffer: ByteBuffer? = null
    private var reusePixels: IntArray? = null
    private var reuseLetterboxBitmap: Bitmap? = null
    private var reusePrediction: Array<Array<Array<FloatArray>>>? = null
    private var reuseRawMatrix: IntArray? = null
    private var reuseLabeledMatrix: IntArray? = null
    private var reuseParentArray: IntArray? = null

    private suspend fun getInterpreter(): Interpreter = withContext(singleThreadDispatcher) {
        if (interpreter == null) {
            val fileDescriptor = context.assets.openFd("deeppanel.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                val cores = Runtime.getRuntime().availableProcessors()
                setNumThreads(minOf(cores, 4))
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "DeepPanel interpreter loaded successfully")
        }
        interpreter!!
    }

    suspend fun detect(bitmap: Bitmap): PanelMap? = withContext(singleThreadDispatcher) {
        try {
            val interp = getInterpreter()
            val origW = bitmap.width
            val origH = bitmap.height

            val gain = min(modelInputImageSize.toFloat() / origW, modelInputImageSize.toFloat() / origH)
            val padX = (modelInputImageSize - origW * gain) / 2f
            val padY = (modelInputImageSize - origH * gain) / 2f

            val letterbox = LetterboxParams(
                inputSize = modelInputImageSize,
                originalWidth = origW,
                originalHeight = origH,
                gain = gain,
                padX = padX,
                padY = padY
            )

            // Letterbox original bitmap to 224x224 black background
            val letterboxedBitmap = if (reuseLetterboxBitmap != null && reuseLetterboxBitmap!!.width == modelInputImageSize && reuseLetterboxBitmap!!.height == modelInputImageSize && !reuseLetterboxBitmap!!.isRecycled) {
                reuseLetterboxBitmap!!
            } else {
                createBitmap(modelInputImageSize, modelInputImageSize).also { reuseLetterboxBitmap = it }
            }
            val canvas = Canvas(letterboxedBitmap)
            val paint = Paint().apply { color = Color.BLACK }
            canvas.drawRect(0f, 0f, modelInputImageSize.toFloat(), modelInputImageSize.toFloat(), paint)

            val matrix = Matrix()
            matrix.setRectToRect(
                RectF(0f, 0f, origW.toFloat(), origH.toFloat()),
                RectF(0f, 0f, modelInputImageSize.toFloat(), modelInputImageSize.toFloat()),
                Matrix.ScaleToFit.CENTER
            )
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

            // Preprocess float32 RGB / 255
            val bufferSize = 1 * modelInputImageSize * modelInputImageSize * 3 * 4
            val inputBuffer = if (reuseInputBuffer != null && reuseInputBuffer!!.capacity() == bufferSize) {
                reuseInputBuffer!!.also { it.rewind() }
            } else {
                ByteBuffer.allocateDirect(bufferSize).also { reuseInputBuffer = it }
            }
            inputBuffer.order(ByteOrder.nativeOrder())
            inputBuffer.rewind()

            val pixelCount = modelInputImageSize * modelInputImageSize
            val pixels = if (reusePixels != null && reusePixels!!.size == pixelCount) {
                reusePixels!!
            } else {
                IntArray(pixelCount).also { reusePixels = it }
            }
            letterboxedBitmap.getPixels(pixels, 0, modelInputImageSize, 0, 0, modelInputImageSize, modelInputImageSize)

            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((pixel and 0xFF) / 255f)
            }

            // Output tensor shape: [1, 224, 224, 3] (0=bg, 1=border, 2=content)
            val prediction = if (reusePrediction != null) {
                reusePrediction!!
            } else {
                Array(1) { Array(modelInputImageSize) { Array(modelInputImageSize) { FloatArray(3) } } }.also { reusePrediction = it }
            }
            interp.run(inputBuffer, prediction)

            // Step 1: Content mask with flat array indices
            val rawMatrix = if (reuseRawMatrix != null && reuseRawMatrix!!.size == pixelCount) {
                reuseRawMatrix!!.also { it.fill(0) }
            } else {
                IntArray(pixelCount).also { reuseRawMatrix = it }
            }

            val minValidX = (padX - 1f).toInt().coerceAtLeast(0)
            val maxValidX = (modelInputImageSize - padX + 1f).toInt().coerceAtMost(modelInputImageSize - 1)
            val minValidY = (padY - 1f).toInt().coerceAtLeast(0)
            val maxValidY = (modelInputImageSize - padY + 1f).toInt().coerceAtMost(modelInputImageSize - 1)

            for (j in 0 until modelInputImageSize) {
                for (i in 0 until modelInputImageSize) {
                    val idx = j * modelInputImageSize + i
                    if (i !in minValidX..maxValidX || j < minValidY || j > maxValidY) {
                        rawMatrix[idx] = 0
                        continue
                    }
                    val pixelScores = prediction[0][j][i]
                    val bg = pixelScores[0]
                    val border = pixelScores[1]
                    val content = pixelScores[2]

                    rawMatrix[idx] = if (content > bg && content > border) 1 else 0
                }
            }

            // Step 2: 4-connected Hoshen-Kopelman component labeling
            val maxLabels = (modelInputImageSize * modelInputImageSize) / 2
            val parent = if (reuseParentArray != null && reuseParentArray!!.size == maxLabels) {
                reuseParentArray!!.also { for (k in it.indices) it[k] = k }
            } else {
                IntArray(maxLabels) { it }.also { reuseParentArray = it }
            }

            fun ufFind(x: Int): Int {
                var root = x
                while (parent[root] != root) root = parent[root]
                var curr = x
                while (curr != root) {
                    val nxt = parent[curr]
                    parent[curr] = root
                    curr = nxt
                }
                return root
            }

            fun ufUnion(x: Int, y: Int): Int {
                val rx = ufFind(x)
                val ry = ufFind(y)
                if (rx != ry) parent[rx] = ry
                return ry
            }

            var nextLabel = 1
            val labeled = if (reuseLabeledMatrix != null && reuseLabeledMatrix!!.size == pixelCount) {
                reuseLabeledMatrix!!.also { it.fill(0) }
            } else {
                IntArray(pixelCount).also { reuseLabeledMatrix = it }
            }

            for (j in 0 until modelInputImageSize) {
                for (i in 0 until modelInputImageSize) {
                    val idx = j * modelInputImageSize + i
                    if (rawMatrix[idx] != 0) {
                        val left = if (i == 0) 0 else labeled[j * modelInputImageSize + (i - 1)]
                        val up = if (j == 0) 0 else labeled[(j - 1) * modelInputImageSize + i]

                        when ((if (up != 0) 1 else 0) + (if (left != 0) 1 else 0)) {
                            0 -> {
                                val l = nextLabel++
                                parent[l] = l
                                labeled[idx] = l
                            }
                            1 -> {
                                labeled[idx] = if (up != 0) up else left
                            }
                            2 -> {
                                labeled[idx] = ufUnion(up, left)
                            }
                        }
                    }
                }
            }

            // Count pixels per canonical label
            val labelCounts = IntArray(nextLabel)
            for (idx in 0 until pixelCount) {
                val l = labeled[idx]
                if (l != 0) {
                    val canonical = ufFind(l)
                    labelCounts[canonical]++
                }
            }

            // Step 3: Area filter threshold
            val minPixels = (modelInputImageSize * modelInputImageSize * MIN_PANEL_AREA_RATIO).toInt()
            val canonicalToNew = IntArray(nextLabel)
            var currentNormLabel = 0

            for (c in 1 until nextLabel) {
                if (labelCounts[c] >= minPixels) {
                    canonicalToNew[c] = ++currentNormLabel
                }
            }

            val finalLabels = ShortArray(pixelCount)
            for (idx in 0 until pixelCount) {
                val rawL = labeled[idx]
                if (rawL != 0) {
                    val canonical = ufFind(rawL)
                    val newL = canonicalToNew[canonical]
                    if (newL > 0) {
                        finalLabels[idx] = newL.toShort()
                    }
                }
            }

            // Step 3b: Split merged panels along full-resolution gutters
            val (refinedLabels, totalPanelLabels) = splitByFullResGutters(
                bitmap, letterbox, finalLabels, currentNormLabel
            )

            // Step 4: Compute bounds and build PanelInfo list
            val minXMap = IntArray(totalPanelLabels + 1) { Int.MAX_VALUE }
            val maxXMap = IntArray(totalPanelLabels + 1) { Int.MIN_VALUE }
            val minYMap = IntArray(totalPanelLabels + 1) { Int.MAX_VALUE }
            val maxYMap = IntArray(totalPanelLabels + 1) { Int.MIN_VALUE }
            val sumXMap = FloatArray(totalPanelLabels + 1)
            val sumYMap = FloatArray(totalPanelLabels + 1)
            val countMap = IntArray(totalPanelLabels + 1)

            for (j in 0 until modelInputImageSize) {
                for (i in 0 until modelInputImageSize) {
                    val l = refinedLabels[j * modelInputImageSize + i].toInt()
                    if (l in 1..totalPanelLabels) {
                        if (i < minXMap[l]) minXMap[l] = i
                        if (i > maxXMap[l]) maxXMap[l] = i
                        if (j < minYMap[l]) minYMap[l] = j
                        if (j > maxYMap[l]) maxYMap[l] = j

                        sumXMap[l] += i.toFloat()
                        sumYMap[l] += j.toFloat()
                        countMap[l]++
                    }
                }
            }

            val panelInfos = mutableListOf<PanelInfo>()
            for (l in 1..totalPanelLabels) {
                if (countMap[l] == 0) continue
                val minGx = minXMap[l]
                val maxGx = maxXMap[l]
                val minGy = minYMap[l]
                val maxGy = maxYMap[l]

                val (normLeft, normTop) = letterbox.letterboxToNorm(minGx.toFloat(), minGy.toFloat())
                val (normRight, normBottom) = letterbox.letterboxToNorm((maxGx + 1).toFloat(), (maxGy + 1).toFloat())

                val normWidth = normRight - normLeft
                val normHeight = normBottom - normTop

                // Filter out thin phantom edge strips or extreme border artifacts
                if (normWidth < 0.03f || normHeight < 0.03f) continue
                if (normWidth / normHeight > 12f || normHeight / normWidth > 12f) continue

                val count = countMap[l]
                val avgGx = sumXMap[l] / count
                val avgGy = sumYMap[l] / count
                val (cx, cy) = letterbox.letterboxToNorm(avgGx, avgGy)

                val boxArea = normWidth * normHeight

                panelInfos.add(
                    PanelInfo(
                        label = panelInfos.size + 1,
                        box = NormRect(normLeft, normTop, normRight, normBottom),
                        cx = cx,
                        cy = cy,
                        area = boxArea
                    )
                )
            }

            Log.i(TAG, "Detected ${panelInfos.size} panels for ${origW}x${origH} page")
            PanelMap(
                gridW = modelInputImageSize,
                gridH = modelInputImageSize,
                labels = refinedLabels,
                letterbox = letterbox,
                panels = panelInfos
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error running DeepPanel detector", e)
            null
        }
    }

    private fun splitByFullResGutters(
        bitmap: Bitmap,
        letterbox: LetterboxParams,
        labels: ShortArray,
        labelCount: Int
    ): Pair<ShortArray, Int> {
        val n = modelInputImageSize
        val (gray, w, h) = toWorkingGray(bitmap)
        val bg = estimateMarginGray(gray, w, h)
        Log.i(TAG, "page margin gray = $bg")
        val out = labels.clone()
        var total = labelCount

        for (l in 1..labelCount) {
            var minX = n; var maxX = -1; var minY = n; var maxY = -1
            for (j in 0 until n) for (i in 0 until n) {
                if (labels[j * n + i].toInt() == l) {
                    if (i < minX) minX = i
                    if (i > maxX) maxX = i
                    if (j < minY) minY = j
                    if (j > maxY) maxY = j
                }
            }
            if (maxX < 0) continue

            val (nl, nt) = letterbox.letterboxToNorm(minX.toFloat(), minY.toFloat())
            val (nr, nb) = letterbox.letterboxToNorm((maxX + 1).toFloat(), (maxY + 1).toFloat())
            val rl = (nl * w).toInt().coerceIn(0, w - 1)
            val rt = (nt * h).toInt().coerceIn(0, h - 1)
            val rr = (nr * w).toInt().coerceIn(rl + 1, w)
            val rb = (nb * h).toInt().coerceIn(rt + 1, h)

            val cutter = GutterCutter(gray, w, h, bg)
            cutter.run(rl, rt, rr, rb, 0)
            val leaves = cutter.leaves
            if (leaves.size < 2) continue
            Log.i(TAG, "Label $l split into ${leaves.size} panels by full-res gutters")
            if (!leaves.all { isPlausiblePanel(gray, w, h, bg, it) }) {
                Log.i(TAG, "Label $l: gutter split rejected (implausible parts)")
                continue
            }
            val leafLabel = IntArray(leaves.size) { if (it == 0) l else ++total }
            for (j in minY..maxY) for (i in minX..maxX) {
                if (labels[j * n + i].toInt() != l) continue
                val (cx, cy) = letterbox.letterboxToNorm(i + 0.5f, j + 0.5f)
                val px = (cx * w).toInt(); val py = (cy * h).toInt()
                var assigned = 0   // 0 = gutter pixel, dropped
                for (k in leaves.indices) {
                    val r = leaves[k]
                    if (px >= r[0] && px < r[2] && py >= r[1] && py < r[3]) { assigned = leafLabel[k]; break }
                }
                out[j * n + i] = assigned.toShort()
            }
        }
        return Pair(out, total)
    }

    /** Repeated-halving downscale to ~WORK_WIDTH (avoids bilinear aliasing of thin gutters). */
    private fun toWorkingGray(src: Bitmap): Triple<IntArray, Int, Int> {
        var cur = src
        while (cur.width / 2 >= WORK_WIDTH) {
            val next = cur.scale(cur.width / 2, cur.height / 2)
            if (cur !== src) cur.recycle()
            cur = next
        }
        if (cur.width > WORK_WIDTH) {
            val next = cur.scale(WORK_WIDTH, (cur.height * WORK_WIDTH.toFloat() / cur.width).toInt())
            if (cur !== src) cur.recycle()
            cur = next
        }
        val w = cur.width; val h = cur.height
        val px = IntArray(w * h)
        cur.getPixels(px, 0, w, 0, 0, w, h)
        if (cur !== src) cur.recycle()
        val gray = IntArray(w * h) {
            val p = px[it]
            (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)).toInt()
        }
        return Triple(gray, w, h)
    }


    /** Median gray of the outer 1% frame = page margin / gutter color. */
    private fun estimateMarginGray(g: IntArray, w: Int, h: Int): Int {
        val m = max(2, (w * 0.01f).toInt())
        val hist = IntArray(256)
        var cnt = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (x < m || x >= w - m || y < m || y >= h - m) { hist[g[y * w + x].coerceIn(0, 255)]++; cnt++ }
        }
        var acc = 0
        for (v in 0..255) { acc += hist[v]; if (acc * 2 >= cnt) return v }
        return 0
    }

    private fun isPlausiblePanel(g: IntArray, w: Int, h: Int, bg: Int, r: IntArray): Boolean {
        val pw = r[2] - r[0]; val ph = r[3] - r[1]
        if (pw < w * 0.05f || ph < h * 0.035f) return false
        if (pw.toFloat() * ph < w.toFloat() * h * 0.012f) return false
        var near = 0; var cnt = 0
        for (y in r[1] until r[3] step 2) for (x in r[0] until r[2] step 2) {
            cnt++
            if (abs(g[y * w + x] - bg) <= GUTTER_TOL) near++
        }
        return cnt > 0 && near.toFloat() / cnt < 0.90f   // mostly margin color = not a panel
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        singleThreadDispatcher.close()
    }
}

private const val WORK_WIDTH = 720
private const val GUTTER_TOL = 8            // |gray - pageMarginGray| must be <= this
private const val GUTTER_FRAC = 0.97f       // share of a row/col that must match the margin color
private const val GUTTER_MIN_FRAC = 0.005f  // min gutter thickness, fraction of the cut axis
private const val PART_MIN_FRAC = 0.035f    // drop pieces thinner than this fraction of the page

private class GutterCutter(
    private val g: IntArray, private val w: Int, private val h: Int, private val bg: Int
) {
    val leaves = mutableListOf<IntArray>()   // [left, top, right, bottom)

    fun run(l: Int, t: Int, r: Int, b: Int, depth: Int) {
        val parts = if (depth > 8) null else (cut(l, t, r, b, true) ?: cut(l, t, r, b, false))
        if (parts == null) { leaves += intArrayOf(l, t, r, b); return }
        for (p in parts) run(p[0], p[1], p[2], p[3], depth + 1)
    }

    private fun isGutter(i: Int, horizontal: Boolean, l: Int, t: Int, r: Int, b: Int): Boolean {
        var near = 0
        val n: Int
        if (horizontal) { n = r - l; for (x in l until r) if (abs(g[i * w + x] - bg) <= GUTTER_TOL) near++ }
        else { n = b - t; for (y in t until b) if (abs(g[y * w + i] - bg) <= GUTTER_TOL) near++ }
        return near >= GUTTER_FRAC * n
    }

    /** null = no gutter found on this axis. Leading/trailing gutter runs are trimmed as margins. */
    private fun cut(l: Int, t: Int, r: Int, b: Int, horizontal: Boolean): List<IntArray>? {
        val len = if (horizontal) b - t else r - l
        val span = if (horizontal) r - l else b - t
        if (len < 8 || span < 8) return null
        val base = if (horizontal) t else l
        val minGap = max(4, (len * GUTTER_MIN_FRAC).toInt())
        val minPart = max(8, ((if (horizontal) h else w) * PART_MIN_FRAC).toInt())

        val runs = ArrayList<IntArray>()
        var s = -1
        for (i in 0 until len) {
            if (isGutter(base + i, horizontal, l, t, r, b)) { if (s < 0) s = i }
            else if (s >= 0) { if (i - s >= minGap) runs += intArrayOf(s, i); s = -1 }
        }
        if (s >= 0 && len - s >= minGap) runs += intArrayOf(s, len)
        if (runs.isEmpty()) return null

        val parts = ArrayList<IntArray>()
        var pos = 0
        for (run in runs + intArrayOf(len, len)) {
            if (run[0] - pos >= minPart) {
                parts += if (horizontal) intArrayOf(l, t + pos, r, t + run[0])
                else intArrayOf(l + pos, t, l + run[0], b)
            }
            pos = run[1]
        }
        return parts.ifEmpty { null }
    }
}