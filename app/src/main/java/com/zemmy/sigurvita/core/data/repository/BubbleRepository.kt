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
     * Crops the bubble's bounding box out of the page, then whites out every pixel
     * outside the bubble's segmentation mask so neighbouring artwork/bubbles are hidden.
     */
    suspend fun cropBubble(
        context: Context,
        page: ReaderPage,
        bubble: DetectedBubble,
        letterbox: LetterboxParams
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val fullBitmap = PageBitmapLoader.decodeForInference(context, page, targetSize = 2000) ?: return@withContext null
            val W = fullBitmap.width.toFloat()
            val H = fullBitmap.height.toFloat()

            val bw = bubble.box.right - bubble.box.left
            val bh = bubble.box.bottom - bubble.box.top

            // 5% margin around the bubble box so no text/border is clipped
            val marginX = (bw * 0.05f * W).toInt().coerceAtLeast(4)
            val marginY = (bh * 0.05f * H).toInt().coerceAtLeast(4)

            val left = (bubble.box.left * W - marginX).toInt().coerceIn(0, fullBitmap.width - 1)
            val top = (bubble.box.top * H - marginY).toInt().coerceIn(0, fullBitmap.height - 1)
            val right = (bubble.box.right * W + marginX).toInt().coerceIn(left + 1, fullBitmap.width)
            val bottom = (bubble.box.bottom * H + marginY).toInt().coerceIn(top + 1, fullBitmap.height)

            val cropW = right - left
            val cropH = bottom - top
            if (cropW <= 0 || cropH <= 0) return@withContext null

            val crop = Bitmap.createBitmap(fullBitmap, left, top, cropW, cropH)
            return@withContext applyBubbleMask(crop, left, top, W, H, bubble, letterbox)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop bubble", e)
            return@withContext null
        }
    }

    /**
     * Blends everything outside the bubble mask to white, with a soft edge.
     * [cropLeft]/[cropTop] are the crop's position inside the full decoded page ([pageW] x [pageH] px).
     * The mask lives on a sz x sz grid in letterbox space, so each crop pixel is mapped
     * page px -> page-normalised -> letterbox px -> mask grid and sampled bilinearly.
     */
    private fun applyBubbleMask(
        crop: Bitmap,
        cropLeft: Int,
        cropTop: Int,
        pageW: Float,
        pageH: Float,
        bubble: DetectedBubble,
        lb: LetterboxParams
    ): Bitmap {
        val w = crop.width
        val h = crop.height
        val mask = bubble.mask
        val sz = mask.size
        val probs = mask.probs
        if (sz <= 0 || probs.size < sz * sz) return crop

        val pixels = IntArray(w * h)
        crop.getPixels(pixels, 0, w, 0, 0, w, h)

        fun cell(gx: Int, gy: Int): Float {
            if (gx !in 0 until sz || gy !in 0 until sz) return 0f
            return (probs[gy * sz + gx].toInt() and 0xFF) / 255f
        }

        val lbW = lb.originalWidth * lb.gain
        val lbH = lb.originalHeight * lb.gain
        val gridPerLbPx = sz / lb.inputSize.toFloat()

        // Soft edge: fully hidden below LOW, fully visible above HIGH.
        // LOW is deliberately low so text touching the bubble border isn't clipped.
        val low = 0.15f
        val high = 0.40f

        for (y in 0 until h) {
            val normY = (cropTop + y + 0.5f) / pageH
            val gyF = (normY * lbH + lb.padY) * gridPerLbPx - 0.5f
            val gy0 = Math.floor(gyF.toDouble()).toInt()
            val fy = gyF - gy0
            for (x in 0 until w) {
                val normX = (cropLeft + x + 0.5f) / pageW
                val gxF = (normX * lbW + lb.padX) * gridPerLbPx - 0.5f
                val gx0 = Math.floor(gxF.toDouble()).toInt()
                val fx = gxF - gx0

                val p = cell(gx0, gy0) * (1 - fx) * (1 - fy) +
                        cell(gx0 + 1, gy0) * fx * (1 - fy) +
                        cell(gx0, gy0 + 1) * (1 - fx) * fy +
                        cell(gx0 + 1, gy0 + 1) * fx * fy

                val a = ((p - low) / (high - low)).coerceIn(0f, 1f)
                if (a >= 1f) continue

                val src = pixels[y * w + x]
                val r = (((src shr 16) and 0xFF) * a + 255 * (1 - a)).toInt()
                val g = (((src shr 8) and 0xFF) * a + 255 * (1 - a)).toInt()
                val b = ((src and 0xFF) * a + 255 * (1 - a)).toInt()
                pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun clearChapter(chapterId: Int) {
        val keysToRemove = cache.snapshot().keys.filter { it.first.first == chapterId }
        for (k in keysToRemove) {
            cache.remove(k)
        }
    }
}