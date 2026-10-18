package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.zemmy.sigurvita.core.ml.DetectionStore
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import android.os.SystemClock

private const val TAG = "DetectionRepository"

/**
 * Lookup order for every page: in-memory LruCache -> disk ([DetectionStore]) -> run the models
 * through [BubbleRepository] / [PanelRepository], then persist the result.
 *
 * The peek* functions never decode a bitmap or run a model, so callers can check for a cached
 * result BEFORE paying for a page decode.
 */
class DetectionRepository(
    private val context: Context,
    private val bubbleRepository: BubbleRepository,
    private val panelRepository: PanelRepository
) {
    // Holds the same object instances as the repositories' own caches, so memory is not doubled per entry.
    private val bubbleMem = LruCache<Triple<Int, Int, Int>, PageBubbles>(100)
    private val panelMem = LruCache<Pair<Int, Int>, PanelMap>(100)

    private fun confKey(confidence: Float) = (confidence * 1000f).roundToInt()

    suspend fun peekBubbles(chapterId: Int, pageIndex: Int, confidence: Float): PageBubbles? {
        val key = Triple(chapterId, pageIndex, confKey(confidence))
        bubbleMem.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            DetectionStore.loadBubbles(context, chapterId, pageIndex, confidence)?.also {
                Log.d(TAG, "bubbles disk hit chapter=$chapterId page=$pageIndex")
                bubbleMem.put(key, it)
            }
        }
    }

    suspend fun peekPanelMap(chapterId: Int, pageIndex: Int): PanelMap? {
        val key = Pair(chapterId, pageIndex)
        panelMem.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            DetectionStore.loadPanelMap(context, chapterId, pageIndex)?.also {
                Log.d(TAG, "panels disk hit chapter=$chapterId page=$pageIndex")
                panelMem.put(key, it)
            }
        }
    }

    suspend fun getPageBubbles(chapterId: Int, page: ReaderPage, confidence: Float): PageBubbles? {
        peekBubbles(chapterId, page.index, confidence)?.let { return it }
        val t0 = SystemClock.elapsedRealtime()
        val result = bubbleRepository.getPageBubbles(chapterId, page, confidence) ?: return null
        Log.i(TAG, "TIMING bubbles page=${page.index} ${SystemClock.elapsedRealtime() - t0}ms")
        bubbleMem.put(Triple(chapterId, page.index, confKey(confidence)), result)
        withContext(Dispatchers.IO) {
            DetectionStore.saveBubbles(context, chapterId, page.index, confidence, result)
        }
        return result
    }

    suspend fun getPanelMap(chapterId: Int, page: ReaderPage, sharedBitmap: Bitmap? = null): PanelMap? {
        peekPanelMap(chapterId, page.index)?.let { return it }
        val t0 = SystemClock.elapsedRealtime()
        val result = panelRepository.getPanelMap(chapterId, page, sharedBitmap) ?: return null
        Log.i(TAG, "TIMING panels page=${page.index} ${SystemClock.elapsedRealtime() - t0}ms")
        panelMem.put(Pair(chapterId, page.index), result)
        withContext(Dispatchers.IO) {
            DetectionStore.savePanelMap(context, chapterId, page.index, result)
        }
        return result
    }
}
