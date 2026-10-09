package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.zemmy.sigurvita.core.ml.DeepPanelDetector
import com.zemmy.sigurvita.core.ml.PageBitmapLoader
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PanelRepository"

class PanelRepository(
    private val context: Context,
    private val detector: DeepPanelDetector = DeepPanelDetector(context)
) {
    // Cache up to 30 pages of panel layouts in memory, keyed by (chapterId, pageIndex)
    private val cache = LruCache<Pair<Int, Int>, PanelMap>(30)

    suspend fun getPanelMap(chapterId: Int, page: ReaderPage, sharedBitmap: Bitmap? = null): PanelMap? = withContext(Dispatchers.IO) {
        val key = Pair(chapterId, page.index)
        val cached = cache.get(key)
        if (cached != null) {
            return@withContext cached
        }

        try {
            val bitmap = sharedBitmap ?: PageBitmapLoader.decodeForInference(context, page) ?: return@withContext null
            val result = detector.detect(bitmap)
            if (result != null) {
                cache.put(key, result)
            }
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect panel map for chapter $chapterId page ${page.index}", e)
            return@withContext null
        }
    }

    fun clearChapter(chapterId: Int) {
        val keysToRemove = cache.snapshot().keys.filter { it.first == chapterId }
        for (k in keysToRemove) {
            cache.remove(k)
        }
    }
}
