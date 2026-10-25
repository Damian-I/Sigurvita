package com.zemmy.sigurvita.ui.screens.reader

import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.ml.PageBitmapLoader
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.core.util.PanelMap
import com.zemmy.sigurvita.core.util.orderBubbles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private const val PREFETCH_CONCURRENCY = 3
private const val PRELOAD_DEBOUNCE_MS = 200L

/**
 * Runs speech-bubble / panel detection for the reader: one job per page, a debounced preload that starts from the
 * page being read, image prefetch for streamed pages, and a look-ahead into the next chapter.
 *
 * Moved out of [ReaderViewModel] without behaviour changes, except that the job map is a [ConcurrentHashMap]
 * (it is touched from both the main thread and the IO preload workers) and the next-chapter lookup now reuses the
 * ViewModel's neighbour resolution ([nextChapterOf]) instead of repeating it.
 *
 * Results are written into [state] (`bubblesByPage` / `panelMapsByPage`), which stays owned by the ViewModel.
 */
class ReaderDetectionScheduler(
    private val app: SigurvitaApp,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ReaderUiState>,
    private val nextChapterOf: suspend (ReaderUiState) -> ChapterRef?
) {
    private val bubbleDetectionJobs = ConcurrentHashMap<Int, Job>()
    private var chapterPreloadJob: Job? = null
    private var nextChapterPreloadJob: Job? = null
    private var preloadDebounceJob: Job? = null
    private var preloadedNextChapterId: Int = 0
    private var prefetchedChapterId: Int = 0
    private val prefetchedPages: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun hasJob(pageIdx: Int): Boolean = bubbleDetectionJobs.containsKey(pageIdx)

    fun jobFor(pageIdx: Int): Job? = bubbleDetectionJobs[pageIdx]

    /** Cancels every per-page job and the pending debounced preload (chapter change, setting change). */
    fun cancelPageJobs() {
        bubbleDetectionJobs.values.forEach { it.cancel() }
        bubbleDetectionJobs.clear()
        preloadDebounceJob?.cancel()
    }

    /** Stops all background detection when the reader is left. Results already computed stay cached. */
    fun onReaderClosed() {
        preloadDebounceJob?.cancel()
        chapterPreloadJob?.cancel()
        nextChapterPreloadJob?.cancel()
        bubbleDetectionJobs.values.forEach { it.cancel() }
        bubbleDetectionJobs.clear()
        // Allow the next-chapter preload to start again if this chapter is reopened
        preloadedNextChapterId = 0
    }

    /** Page turn: drop jobs far from the reader, then restart the preload once the reader stops on this page. */
    fun onPageChanged(index: Int) {
        // Cancel any stale detection jobs for pages behind current position
        val keysToCancel = bubbleDetectionJobs.keys.filter { it < index || it > index + 3 }
        keysToCancel.forEach { pageIdx ->
            bubbleDetectionJobs.remove(pageIdx)?.cancel()
        }

        preloadDebounceJob?.cancel()
        val debounceChapterId = state.value.chapterId
        preloadDebounceJob = scope.launch {
            delay(PRELOAD_DEBOUNCE_MS.milliseconds)
            if (state.value.chapterId == debounceChapterId &&
                state.value.currentPageIndex == index
            ) {
                startPreload(index)
            }
        }
    }

    fun ensureBubbles(pageIdx: Int) {
        val current = state.value
        if (!current.settings.speechBubbleDetectionEnabled) return
        if (current.settings.readingMode == ReadingMode.WEBTOON) return
        if (pageIdx !in current.pages.indices) return
        if (current.bubblesByPage.containsKey(pageIdx) || bubbleDetectionJobs.containsKey(pageIdx)) return

        val page = current.pages[pageIdx]
        val chapterId = current.chapterId
        val confidence = current.settings.speechBubbleConfidence

        bubbleDetectionJobs[pageIdx] = scope.launch {
            val isRtl = (state.value.settings.readingMode == ReadingMode.RTL)
            val isPanelAware = state.value.settings.panelAwareOrderEnabled

            val (pageBubbles, panelMap) = loadDetections(chapterId, page, confidence, isPanelAware)

            if (pageBubbles != null) {
                val sortedBubbles = pageBubbles.copy(
                    bubbles = orderBubbles(pageBubbles, panelMap, isRtl, isPanelAware)
                )
                state.update {
                    it.copy(
                        bubblesByPage = it.bubblesByPage + (pageIdx to sortedBubbles),
                        panelMapsByPage = if (panelMap != null) it.panelMapsByPage + (pageIdx to panelMap) else it.panelMapsByPage
                    )
                }
            }
            bubbleDetectionJobs.remove(pageIdx)
        }
    }

    /**
     * Memory -> disk -> models. The page bitmap is only decoded if something is actually missing.
     * Panel and bubble detection still run concurrently when both are missing.
     */
    private suspend fun loadDetections(
        chapterId: Int,
        page: ReaderPage,
        confidence: Float,
        isPanelAware: Boolean
    ): Pair<PageBubbles?, PanelMap?> {
        val detections = app.detectionRepository
        val cachedBubbles = detections.peekBubbles(chapterId, page.index, confidence)
        val cachedPanels = if (isPanelAware) detections.peekPanelMap(chapterId, page.index) else null
        val needBubbles = cachedBubbles == null
        val needPanels = isPanelAware && cachedPanels == null
        if (!needBubbles && !needPanels) return Pair(cachedBubbles, cachedPanels)

        val t0 = android.os.SystemClock.elapsedRealtime()
        val bitmap = PageBitmapLoader.decodeForInference(app, page)
            ?: return Pair(cachedBubbles, cachedPanels)
        android.util.Log.i(
            "DetectTiming",
            "decode page=${page.index} local=${page.isLocal} ${bitmap.width}x${bitmap.height} " +
                    "${android.os.SystemClock.elapsedRealtime() - t0}ms"
        )

        val result = coroutineScope {
            val panelDeferred = if (needPanels) {
                async { detections.getPanelMap(chapterId, page, bitmap) }
            } else null
            val bubbleDeferred = if (needBubbles) {
                async { detections.getPageBubbles(chapterId, page, confidence) }
            } else null

            Pair(
                bubbleDeferred?.await() ?: cachedBubbles,
                panelDeferred?.await() ?: cachedPanels
            )
        }
        android.util.Log.i(
            "DetectTiming",
            "total page=${page.index} ${android.os.SystemClock.elapsedRealtime() - t0}ms"
        )
        return result
    }

    /** Restarts the chapter preload from [fromIndex]: current page alone first, then a two-worker queue. */
    fun startPreload(fromIndex: Int) {
        chapterPreloadJob?.cancel()
        chapterPreloadJob = scope.launch(Dispatchers.IO) {
            val current = state.value
            if (!current.settings.speechBubbleDetectionEnabled) return@launch
            if (current.settings.readingMode == ReadingMode.WEBTOON) return@launch

            val pages = current.pages
            val chapterId = current.chapterId
            if (pages.isEmpty() || chapterId <= 0) return@launch

            if (prefetchedChapterId != chapterId) {
                prefetchedPages.clear()
                prefetchedChapterId = chapterId
            }

            // Phase 1: warm the image disk cache for streamed pages only
            launch {
                val gate = Semaphore(PREFETCH_CONCURRENCY)
                pages.subList(fromIndex, pages.size)
                    .filter { !it.isLocal && it.index !in prefetchedPages }
                    .map { page ->
                        async {
                            gate.withPermit {
                                if (prefetchPage(page)) prefetchedPages.add(page.index)
                            }
                        }
                    }
                    .awaitAll()
            }

            // Current page first: run it alone before queuing neighbours
            if (!state.value.bubblesByPage.containsKey(fromIndex)) {
                val existing = bubbleDetectionJobs[fromIndex]
                if (existing != null && existing.isActive) {
                    existing.join()
                } else {
                    ensureBubbles(fromIndex)
                    bubbleDetectionJobs[fromIndex]?.join()
                }
                if (state.value.chapterId != chapterId) return@launch
            }

            // Phase 2: dual-worker detection queue from the current page onward
            val pageChannel = Channel<Int>(Channel.UNLIMITED)
            for (i in fromIndex until pages.size) {
                if (!state.value.bubblesByPage.containsKey(i)) {
                    pageChannel.trySend(i)
                }
            }
            pageChannel.close()

            coroutineScope {
                repeat(2) {
                    launch {
                        for (i in pageChannel) {
                            if (!state.value.settings.speechBubbleDetectionEnabled) break
                            if (state.value.chapterId != chapterId) break

                            if (i >= pages.size - 10) {
                                preloadNextChapter()
                            }

                            val job = bubbleDetectionJobs[i]
                            if (job != null && job.isActive) {
                                job.join()
                            } else {
                                ensureBubbles(i)
                                bubbleDetectionJobs[i]?.join()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun prefetchPage(page: ReaderPage): Boolean {
        return try {
            val request = ImageRequest.Builder(app)
                .data(page.imageUrl)
                .size(64, 64)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()
            app.imageLoader.execute(request) is SuccessResult
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Detects the first pages of the next chapter so they are cached (memory + disk) before the reader gets there. */
    private fun preloadNextChapter() {
        val current = state.value
        val currentChapterId = current.chapterId
        if (currentChapterId <= 0) return
        if (preloadedNextChapterId == currentChapterId) return
        if (!current.settings.speechBubbleDetectionEnabled) return
        if (current.settings.readingMode == ReadingMode.WEBTOON) return

        preloadedNextChapterId = currentChapterId

        nextChapterPreloadJob?.cancel()
        nextChapterPreloadJob = scope.launch(Dispatchers.IO) {
            val next = nextChapterOf(current) ?: return@launch
            if (next.chapterId <= 0) return@launch

            val nextPages = app.readerRepository.getPages(next.chapterId, next.totalPages)
            if (nextPages.isEmpty()) return@launch

            val pagesToPreload = nextPages.take(6)
            val isPanelAware = current.settings.panelAwareOrderEnabled
            val confidence = current.settings.speechBubbleConfidence

            for (page in pagesToPreload) {
                if (!state.value.settings.speechBubbleDetectionEnabled) break
                try {
                    loadDetections(next.chapterId, page, confidence, isPanelAware)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Ignore background preload failures
                }
            }
        }
    }
}
