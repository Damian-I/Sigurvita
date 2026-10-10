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
                        if (pageData != null) {
                            val result = com.zemmy.sigurvita.core.ml.DetectionsCacheHelper.toPanelMap(pageData)
                            if (result != null) {
                                cache.put(key, result)
                                return@withContext result
                            }
                        }
                    }
                }
            }

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
