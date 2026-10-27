package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.zemmy.sigurvita.core.ml.DetectionStore
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

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

    // Inference cannot be interrupted, so finished work must outlive a cancelled caller and be shared by later callers.
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bubblesInFlight = ConcurrentHashMap<Triple<Int, Int, Int>, Deferred<PageBubbles?>>()
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
        val key = Triple(chapterId, page.index, confKey(confidence))
        val work = bubblesInFlight.computeIfAbsent(key) {
            workScope.async {
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    val result = bubbleRepository.getPageBubbles(chapterId, page, confidence)
                    if (result != null) {
                        Log.i(TAG, "TIMING bubbles page=${page.index} ${SystemClock.elapsedRealtime() - t0}ms")
                        bubbleMem.put(key, result)
                        DetectionStore.saveBubbles(context, chapterId, page.index, confidence, result)
                    }
                    result
                } finally {
                    bubblesInFlight.remove(key)
                }
            }
        }
        return work.await()
    }

    suspend fun getPanelMap(chapterId: Int, page: ReaderPage, sharedBitmap: Bitmap? = null): PanelMap? {
        peekPanelMap(chapterId, page.index)?.let { return it }
        // Panel inference is short and uses the caller's bitmap, so it stays in the caller but is not cancelled midway
        return withContext(NonCancellable + Dispatchers.IO) {
            val t0 = SystemClock.elapsedRealtime()
            val result = panelRepository.getPanelMap(chapterId, page, sharedBitmap) ?: return@withContext null
            Log.i(TAG, "TIMING panels page=${page.index} ${SystemClock.elapsedRealtime() - t0}ms")
            panelMem.put(Pair(chapterId, page.index), result)
            DetectionStore.savePanelMap(context, chapterId, page.index, result)
            result
        }
    }
}
