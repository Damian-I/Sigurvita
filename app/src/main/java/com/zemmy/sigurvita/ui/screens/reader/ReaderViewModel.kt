package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import com.zemmy.sigurvita.core.util.orderBubbles
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class ReaderUiState(
    val pages: List<ReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val isLoading: Boolean = true,
    val controlsVisible: Boolean = true,
    val settings: ReaderSettings = ReaderSettings(),
    val chapterId: Int = 0,
    val seriesId: Int = 0,
    val volumeId: Int = 0,
    val readingListId: Int = 0,
    val libraryDefaultMode: ReadingMode = ReadingMode.LTR,
    val showResumePrompt: Boolean = false,
    val promptPageNum: Int = 0,
    val markedPageIndices: Set<Int> = emptySet(),
    val chapterTitle: String = "",
    val seriesName: String = "",
    val libraryName: String = "Manga",
    val currentChapter: Chapter? = null,
    val seriesCoverUrl: String? = null,
    val seriesMetadata: SeriesMetadataDto? = null,
    val bubblesByPage: Map<Int, com.zemmy.sigurvita.core.model.PageBubbles> = emptyMap(),
    val panelMapsByPage: Map<Int, com.zemmy.sigurvita.core.util.PanelMap> = emptyMap(),
    val magnifiedBubble: com.zemmy.sigurvita.core.model.MagnifiedBubble? = null
)

class ReaderViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val readerRepository = app.readerRepository
    private val seriesRepository = app.seriesRepository
    private val readingListRepository = app.readingListRepository
    private val libraryRepository = app.libraryRepository
    private val downloadRepository = app.downloadRepository

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val progressSaveFlow = MutableSharedFlow<ProgressEvent>(extraBufferCapacity = 1)
    private var cachedLibraryId: Int = 0
    private var progressJob: Job? = null
    private var sessionReadingModeOverride: ReadingMode? = null

    private data class ProgressEvent(
        val seriesId: Int,
        val volumeId: Int,
        val chapterId: Int,
        val pageNum: Int,
        val totalPages: Int
    )

    init {
        startProgressDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun startProgressDebounce() {
        progressJob = viewModelScope.launch {
            progressSaveFlow
                .debounce(3.seconds)
                .collect { event ->
                    readerRepository.saveProgress(
                        seriesId = event.seriesId,
                        volumeId = event.volumeId,
                        chapterId = event.chapterId,
                        pageNum = event.pageNum,
                        totalPages = event.totalPages,
                        resolvedLibraryId = cachedLibraryId
                    )
                }
        }
    }

    // Flush any pending debounced progress immediately (called on chapter exit).
    suspend fun flushProgressSync() {
        val state = _uiState.value
        if (state.pages.isNotEmpty() && state.chapterId > 0) {
            readerRepository.saveProgress(
                seriesId = state.seriesId,
                volumeId = state.volumeId,
                chapterId = state.chapterId,
                pageNum = state.currentPageIndex + 1,
                totalPages = state.pages.size,
                resolvedLibraryId = cachedLibraryId
            )
        }
    }

    fun flushProgress() {
        viewModelScope.launch {
            flushProgressSync()
        }
    }

    private var markedPageJob: Job? = null
    private var bubbleDetectionJobs = mutableMapOf<Int, Job>()
    private var completedBubblePageIndex: Int = -1

    fun loadChapter(
        chapterId: Int,
        totalPages: Int,
        seriesId: Int,
        volumeId: Int,
        readingListId: Int = 0,
        openAtLastPage: Boolean = false
    ) {
        // Flush progress for the previous chapter before loading a new one
        flushProgress()
        // Reset cached library ID for new chapter context
        cachedLibraryId = 0

        bubbleDetectionJobs.values.forEach { it.cancel() }
        bubbleDetectionJobs.clear()
        completedBubblePageIndex = -1
        _uiState.value = _uiState.value.copy(bubblesByPage = emptyMap())

        markedPageJob?.cancel()
        markedPageJob = viewModelScope.launch {
            app.markedPageRepository.getMarkedPageIndicesForChapter(chapterId).collect { indices ->
                _uiState.value = _uiState.value.copy(markedPageIndices = indices.toSet())
            }
        }

        viewModelScope.launch {
            val userSettings = preferencesRepository.readerSettingsFlow.first()

            val targetSeriesId = seriesId
            var targetVolumeId = volumeId
            var fetchedSeriesName = ""
            var fetchedChapterTitle = ""
            var fetchedLibraryName = "Manga"
            var fetchedCoverUrl: String? = null
            var fetchedMetadata: SeriesMetadataDto? = null
            var fetchedChapter: Chapter? = null
            var isManga = false
            var defaultMode = ReadingMode.LTR

            // 1. Resolve targetSeriesId if not provided in arguments
            if (targetSeriesId <= 0) {
                try {
                    val cRes = seriesRepository.getChapterDto(chapterId)
                    if (cRes.isSuccess && cRes.getOrNull() != null) {
                        val cDto = cRes.getOrNull()!!
                        if (targetVolumeId <= 0) targetVolumeId = cDto.volumeId
                    }
                } catch (_: Exception) {}
            }

            // 2. Fetch Series and Volume/Chapter using the exact same implementation as SeriesDetailViewModel
            try {
                if (targetSeriesId > 0) {
                    val seriesRes = seriesRepository.getSeriesDetail(targetSeriesId)
                    if (seriesRes.isSuccess && seriesRes.getOrNull() != null) {
                        val series = seriesRes.getOrNull()!!
                        fetchedSeriesName = series.name
                        fetchedCoverUrl = series.coverUrl

                        val libraryRes = libraryRepository.getLibraries()
                        val library = libraryRes.getOrNull()?.find { it.id == series.libraryId }
                        if (library != null) {
                            cachedLibraryId = library.id
                            fetchedLibraryName = library.name
                            isManga = library.isManga
                            defaultMode = if (isManga) ReadingMode.RTL else ReadingMode.LTR
                        }
                    }

                    val metaRes = seriesRepository.getSeriesMetadataDetails(targetSeriesId)
                    if (metaRes.isSuccess) {
                        fetchedMetadata = metaRes.getOrNull()
                    }

                    val volumesRes = seriesRepository.getSeriesVolumes(targetSeriesId)
                    if (volumesRes.isSuccess && volumesRes.getOrNull() != null) {
                        val volumes = volumesRes.getOrNull()!!
                        val matchingChapter = volumes.flatMap { it.chapters }.find { it.id == chapterId }
                        if (matchingChapter != null) {
                            fetchedChapterTitle = formatChapterDisplayTitle(matchingChapter.title, matchingChapter.chapterNumber, isManga = isManga)
                            fetchedChapter = matchingChapter
                        }
                    }
                }
            } catch (_: Exception) {}

            if (fetchedChapterTitle.isBlank()) {
                try {
                    val cRes = seriesRepository.getChapterDto(chapterId)
                    if (cRes.isSuccess && cRes.getOrNull() != null) {
                        val cDto = cRes.getOrNull()!!
                        fetchedChapterTitle = formatChapterDisplayTitle(cDto.titleName ?: cDto.title ?: "", cDto.number ?: "", isManga = isManga)
                    }
                } catch (_: Exception) {}
            }

            val downloadTask = try {
                app.database.downloadDao().getDownloadByChapterId(chapterId)
            } catch (_: Exception) { null }
            val isDownloadedLocal = downloadTask?.status == DownloadStatus.COMPLETED

            val finalChapter = fetchedChapter ?: Chapter(
                id = chapterId,
                volumeId = targetVolumeId,
                seriesId = targetSeriesId,
                chapterNumber = "",
                title = fetchedChapterTitle,
                summary = null,
                pages = totalPages,
                pagesRead = 0,
                isRead = false,
                isDownloaded = isDownloadedLocal,
                localPath = downloadTask?.localPath,
                coverUrl = null
            )

            val activeReadingMode = sessionReadingModeOverride ?: defaultMode
            val effectiveSettings = userSettings.copy(
                readingMode = activeReadingMode,
                isIncognitoMode = userSettings.isIncognitoMode
            )

            if (targetSeriesId > 0) {
                if (readingListId > 0) {
                    val listTitle = try {
                        val listRes = app.readingListRepository.getReadingLists()
                        listRes.getOrNull()?.find { it.id == readingListId }?.title ?: ""
                    } catch (_: Exception) { "" }
                    preferencesRepository.saveSeriesReadContext(
                        seriesId = targetSeriesId,
                        originType = "READING_LIST",
                        readingListId = readingListId,
                        readingListTitle = listTitle
                    )
                } else {
                    preferencesRepository.saveSeriesReadContext(
                        seriesId = targetSeriesId,
                        originType = "LIBRARY"
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                settings = effectiveSettings,
                libraryDefaultMode = defaultMode,
                chapterId = chapterId,
                seriesId = targetSeriesId,
                volumeId = targetVolumeId,
                readingListId = readingListId,
                showResumePrompt = false,
                chapterTitle = fetchedChapterTitle,
                seriesName = fetchedSeriesName,
                libraryName = fetchedLibraryName,
                currentChapter = finalChapter,
                seriesCoverUrl = fetchedCoverUrl,
                seriesMetadata = fetchedMetadata,
                controlsVisible = true
            )
            scheduleAutoHideControls()

            val pageList = readerRepository.getPages(chapterId, totalPages)
            val savedPageNum = readerRepository.getProgress(chapterId)

            val hasSavedProgress = !openAtLastPage && savedPageNum > 1 && pageList.isNotEmpty()

            val activeIndex = if (openAtLastPage && pageList.isNotEmpty()) {
                pageList.size - 1
            } else if (hasSavedProgress) {
                if (savedPageNum - 1 in pageList.indices) savedPageNum - 1 else 0
            } else 0

            val updatedChapter = finalChapter.copy(
                pages = if (pageList.isNotEmpty()) pageList.size else totalPages,
                pagesRead = activeIndex + 1
            )

            _uiState.value = _uiState.value.copy(
                pages = pageList,
                currentPageIndex = activeIndex,
                currentChapter = updatedChapter,
                isLoading = false,
                showResumePrompt = hasSavedProgress,
                promptPageNum = if (hasSavedProgress) savedPageNum else 0
            )

            ensureBubbles(activeIndex)
            ensureBubbles(activeIndex + 1)
        }
    }

    fun ensureBubbles(pageIdx: Int) {
        val state = _uiState.value
        if (!state.settings.speechBubbleDetectionEnabled) return
        if (state.settings.readingMode == ReadingMode.WEBTOON) return
        if (pageIdx !in state.pages.indices) return
        if (state.bubblesByPage.containsKey(pageIdx) || bubbleDetectionJobs.containsKey(pageIdx)) return

        val page = state.pages[pageIdx]
        val chapterId = state.chapterId

        bubbleDetectionJobs[pageIdx] = viewModelScope.launch {
            val isRtl = (_uiState.value.settings.readingMode == ReadingMode.RTL)
            val isPanelAware = _uiState.value.settings.panelAwareOrderEnabled

            val bitmap = com.zemmy.sigurvita.core.ml.PageBitmapLoader.decodeForInference(app, page)

            // Run panel and bubble detection concurrently — each uses its own single-thread dispatcher
            val (panelMap, pageBubbles) = coroutineScope {
                val panelDeferred = if (isPanelAware && bitmap != null) {
                    async { app.panelRepository.getPanelMap(chapterId, page, bitmap) }
                } else null

                val bubbleDeferred = if (bitmap != null) {
                    async { app.bubbleRepository.getPageBubbles(chapterId, page, state.settings.speechBubbleConfidence) }
                } else null

                Pair(
                    panelDeferred?.await(),
                    bubbleDeferred?.await()
                )
            }

            if (pageBubbles != null) {
                val sortedBubbles = pageBubbles.copy(
                    bubbles = orderBubbles(pageBubbles, panelMap, isRtl, isPanelAware)
                )
                _uiState.value = _uiState.value.copy(
                    bubblesByPage = _uiState.value.bubblesByPage + (pageIdx to sortedBubbles),
                    panelMapsByPage = if (panelMap != null) _uiState.value.panelMapsByPage + (pageIdx to panelMap) else _uiState.value.panelMapsByPage
                )
            }
            bubbleDetectionJobs.remove(pageIdx)
        }
    }

    fun updateSpeechBubbleDetectionEnabled(enabled: Boolean) {
        completedBubblePageIndex = -1
        if (!enabled) {
            bubbleDetectionJobs.values.forEach { it.cancel() }
            bubbleDetectionJobs.clear()
            _uiState.value = _uiState.value.copy(
                settings = _uiState.value.settings.copy(speechBubbleDetectionEnabled = false),
                bubblesByPage = emptyMap(),
                magnifiedBubble = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                settings = _uiState.value.settings.copy(speechBubbleDetectionEnabled = true)
            )
            ensureBubbles(_uiState.value.currentPageIndex)
            ensureBubbles(_uiState.value.currentPageIndex + 1)
        }
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleDetectionEnabled(enabled)
        }
    }

    fun updateSpeechBubbleBoxesEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(speechBubbleBoxesEnabled = enabled)
        )
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleBoxesEnabled(enabled)
        }
    }

    fun updateSpeechBubbleConfidence(confidence: Float) {
        completedBubblePageIndex = -1
        bubbleDetectionJobs.values.forEach { it.cancel() }
        bubbleDetectionJobs.clear()

        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(speechBubbleConfidence = confidence),
            bubblesByPage = emptyMap()
        )
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleConfidence(confidence)
        }
        app.bubbleRepository.clearChapter(_uiState.value.chapterId)
        ensureBubbles(_uiState.value.currentPageIndex)
        ensureBubbles(_uiState.value.currentPageIndex + 1)
    }

    private var screenWidthPx: Int = 1080
    private var screenHeightPx: Int = 2400

    fun updateScreenSize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            screenWidthPx = widthPx
            screenHeightPx = heightPx
        }
    }

    private data class PageLayout(
        val originX: Float,
        val originY: Float,
        val dispW: Float,
        val dispH: Float
    )

    private fun calculatePageScreenLayout(
        pageWidth: Int,
        pageHeight: Int,
        screenWidthPx: Float,
        screenHeightPx: Float,
        fitMode: FitMode,
        readingMode: ReadingMode
    ): PageLayout {
        val boxW = if (screenWidthPx > 0f) screenWidthPx else 1080f
        val boxH = if (screenHeightPx > 0f) screenHeightPx else 2400f
        val pageW = if (pageWidth > 0) pageWidth.toFloat() else boxW
        val pageH = if (pageHeight > 0) pageHeight.toFloat() else boxH

        val contentScale = when {
            readingMode == ReadingMode.WEBTOON -> ContentScale.FillWidth
            fitMode == FitMode.FIT_WIDTH -> ContentScale.FillWidth
            fitMode == FitMode.FIT_HEIGHT -> ContentScale.FillHeight
            else -> ContentScale.Fit
        }

        val s = when (contentScale) {
            ContentScale.FillWidth -> boxW / pageW
            ContentScale.FillHeight -> boxH / pageH
            else -> minOf(boxW / pageW, boxH / pageH)
        }

        val dispW = pageW * s
        val dispH = pageH * s
        val originX = (boxW - dispW) / 2f
        val originY = (boxH - dispH) / 2f

        return PageLayout(originX, originY, dispW, dispH)
    }

    private fun magnifyBubbleObject(
        pageIndex: Int,
        bubble: com.zemmy.sigurvita.core.model.DetectedBubble,
        pageBubbles: com.zemmy.sigurvita.core.model.PageBubbles
    ) {
        val state = _uiState.value
        val page = state.pages.getOrNull(pageIndex) ?: return

        val layout = calculatePageScreenLayout(
            pageWidth = pageBubbles.pageWidth,
            pageHeight = pageBubbles.pageHeight,
            screenWidthPx = screenWidthPx.toFloat(),
            screenHeightPx = screenHeightPx.toFloat(),
            fitMode = state.settings.fitMode,
            readingMode = state.settings.readingMode
        )

        viewModelScope.launch {
            val cropResult = app.bubbleRepository.cropBubble(app, page, bubble, pageBubbles.letterbox, state.settings.speechBubbleBoxesEnabled, state.chapterId)
            if (cropResult != null) {
                val anchorX = layout.originX + cropResult.anchorNormX * layout.dispW
                val anchorY = layout.originY + cropResult.anchorNormY * layout.dispH
                val cropWOnScreen = (cropResult.cropRightNorm - cropResult.cropLeftNorm) * layout.dispW
                val cropHOnScreen = (cropResult.cropBottomNorm - cropResult.cropTopNorm) * layout.dispH

                _uiState.value = _uiState.value.copy(
                    magnifiedBubble = com.zemmy.sigurvita.core.model.MagnifiedBubble(
                        pageIndex = pageIndex,
                        bubbleId = bubble.id,
                        bitmap = cropResult.bitmap,
                        anchorScreenPx = androidx.compose.ui.geometry.Offset(anchorX, anchorY),
                        cropSizeOnScreen = androidx.compose.ui.geometry.Size(cropWOnScreen, cropHOnScreen),
                        tipRelOffset = androidx.compose.ui.geometry.Offset(cropResult.relTipX, cropResult.relTipY),
                        isLiteralBox = cropResult.isLiteralBox
                    )
                )
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBubbleLongPress(
        pageIndex: Int,
        bubbleId: Int,
        centerOffsetPx: androidx.compose.ui.geometry.Offset,
        bubbleSizePx: androidx.compose.ui.geometry.Size
    ) {
        val state = _uiState.value
        val pageBubbles = state.bubblesByPage[pageIndex] ?: return
        val bubble = pageBubbles.bubbles.find { it.id == bubbleId } ?: return
        val page = state.pages.getOrNull(pageIndex) ?: return

        val layout = calculatePageScreenLayout(
            pageWidth = pageBubbles.pageWidth,
            pageHeight = pageBubbles.pageHeight,
            screenWidthPx = screenWidthPx.toFloat(),
            screenHeightPx = screenHeightPx.toFloat(),
            fitMode = state.settings.fitMode,
            readingMode = state.settings.readingMode
        )

        viewModelScope.launch {
            val cropResult = app.bubbleRepository.cropBubble(app, page, bubble, pageBubbles.letterbox, state.settings.speechBubbleBoxesEnabled, state.chapterId)
            if (cropResult != null) {
                val anchorX = layout.originX + cropResult.anchorNormX * layout.dispW
                val anchorY = layout.originY + cropResult.anchorNormY * layout.dispH
                val cropWOnScreen = (cropResult.cropRightNorm - cropResult.cropLeftNorm) * layout.dispW
                val cropHOnScreen = (cropResult.cropBottomNorm - cropResult.cropTopNorm) * layout.dispH

                _uiState.value = _uiState.value.copy(
                    magnifiedBubble = com.zemmy.sigurvita.core.model.MagnifiedBubble(
                        pageIndex = pageIndex,
                        bubbleId = bubbleId,
                        bitmap = cropResult.bitmap,
                        anchorScreenPx = androidx.compose.ui.geometry.Offset(anchorX, anchorY),
                        cropSizeOnScreen = androidx.compose.ui.geometry.Size(cropWOnScreen, cropHOnScreen),
                        tipRelOffset = androidx.compose.ui.geometry.Offset(cropResult.relTipX, cropResult.relTipY),
                        isLiteralBox = cropResult.isLiteralBox
                    )
                )
            }
        }
    }

    fun dismissBubble() {
        _uiState.value = _uiState.value.copy(magnifiedBubble = null)
    }

    fun onBubbleHover(
        pageIndex: Int,
        bubbleId: Int?,
        centerOffsetPx: androidx.compose.ui.geometry.Offset?,
        bubbleSizePx: androidx.compose.ui.geometry.Size?
    ) {
        if (bubbleId == null) {
            if (_uiState.value.magnifiedBubble != null) {
                _uiState.value = _uiState.value.copy(magnifiedBubble = null)
            }
            return
        }

        if (_uiState.value.magnifiedBubble?.bubbleId == bubbleId && _uiState.value.magnifiedBubble?.pageIndex == pageIndex) {
            return
        }

        onBubbleLongPress(pageIndex, bubbleId, centerOffsetPx ?: androidx.compose.ui.geometry.Offset.Zero, bubbleSizePx ?: androidx.compose.ui.geometry.Size.Zero)
    }

    fun toggleMarkCurrentPage() {
        val state = _uiState.value
        val pageIdx = state.currentPageIndex
        if (state.chapterId <= 0 || pageIdx !in state.pages.indices) return

        val imageUrl = state.pages[pageIdx].imageUrl
        val chapterTitle = state.chapterTitle.ifBlank { "Chapter ${state.chapterId}" }
        val seriesName = state.seriesName.ifBlank { "Series ${state.seriesId}" }

        viewModelScope.launch {
            app.markedPageRepository.toggleMarkPage(
                chapterId = state.chapterId,
                seriesId = state.seriesId,
                volumeId = state.volumeId,
                pageIndex = pageIdx,
                totalPages = state.pages.size,
                seriesName = seriesName,
                chapterTitle = chapterTitle,
                imageUrl = imageUrl
            )
        }
    }

    fun dismissResumePrompt(startFromBeginning: Boolean) {
        val state = _uiState.value
        val promptPage = state.promptPageNum
        _uiState.value = state.copy(showResumePrompt = false)
        if (startFromBeginning) {
            onPageChanged(0)
        } else {
            val resumeIndex = if (promptPage in 1..state.pages.size) promptPage - 1 else 0
            onPageChanged(resumeIndex)
        }
    }

    private var autoHideControlsJob: Job? = null

    fun scheduleAutoHideControls() {
        autoHideControlsJob?.cancel()
        autoHideControlsJob = viewModelScope.launch {
            delay(5.seconds)
            _uiState.value = _uiState.value.copy(controlsVisible = false)
        }
    }

    fun cancelAutoHideControls() {
        autoHideControlsJob?.cancel()
    }

    fun toggleControls() {
        val currentVisible = _uiState.value.controlsVisible
        if (currentVisible) {
            cancelAutoHideControls()
            _uiState.value = _uiState.value.copy(controlsVisible = false)
        } else {
            _uiState.value = _uiState.value.copy(controlsVisible = true)
            scheduleAutoHideControls()
        }
    }

    fun toggleChapterRead(chapter: Chapter) {
        val newReadState = !chapter.isRead
        val updatedChapter = chapter.copy(
            isRead = newReadState,
            pagesRead = if (newReadState) chapter.pages else chapter.pagesRead
        )
        _uiState.value = _uiState.value.copy(currentChapter = updatedChapter)

        viewModelScope.launch {
            if (newReadState) {
                seriesRepository.markChapterRead(chapter.id, chapter.seriesId)
            } else {
                seriesRepository.markChapterUnread(chapter.id, chapter.seriesId)
            }
        }
    }

    fun downloadChapter(chapter: Chapter) {
        val state = _uiState.value
        viewModelScope.launch {
            downloadRepository.enqueueDownload(
                chapterId = chapter.id,
                seriesId = state.seriesId,
                volumeId = chapter.volumeId,
                title = chapter.title.ifBlank { "Issue ${chapter.chapterNumber}" },
                coverUrl = chapter.coverUrl ?: state.seriesCoverUrl,
                totalPages = chapter.pages,
                libraryName = state.libraryName,
                seriesName = state.seriesName,
                chapterNumber = chapter.chapterNumber
            )
        }
    }

    fun deleteLocalChapter(chapterId: Int) {
        viewModelScope.launch {
            downloadRepository.deleteLocalChapter(chapterId)
            val currentCh = _uiState.value.currentChapter
            if (currentCh != null && currentCh.id == chapterId) {
                _uiState.value = _uiState.value.copy(
                    currentChapter = currentCh.copy(isDownloaded = false, localPath = null)
                )
            }
        }
    }

    fun onPageChanged(index: Int) {
        if (_uiState.value.showResumePrompt) return
        if (index in _uiState.value.pages.indices) {
            if (index != completedBubblePageIndex) {
                completedBubblePageIndex = -1
            }
            val totalPages = _uiState.value.pages.size
            val currentCh = _uiState.value.currentChapter
            val updatedCh = currentCh?.copy(
                pagesRead = index + 1,
                isRead = (index + 1 >= totalPages && totalPages > 0)
            )

            _uiState.value = _uiState.value.copy(
                currentPageIndex = index,
                currentChapter = updatedCh ?: currentCh
            )

            if (_uiState.value.controlsVisible) {
                scheduleAutoHideControls()
            }

            ensureBubbles(index)
            ensureBubbles(index + 1)
            progressSaveFlow.tryEmit(
                ProgressEvent(
                    seriesId = _uiState.value.seriesId,
                    volumeId = _uiState.value.volumeId,
                    chapterId = _uiState.value.chapterId,
                    pageNum = index + 1,
                    totalPages = _uiState.value.pages.size
                )
            )

            // Auto-delete logic still runs immediately on last page
            viewModelScope.launch {
                if (index >= totalPages - 1 && totalPages > 0) {
                    // Flush progress immediately for the last page
                    readerRepository.saveProgress(
                        seriesId = _uiState.value.seriesId,
                        volumeId = _uiState.value.volumeId,
                        chapterId = _uiState.value.chapterId,
                        pageNum = index + 1,
                        totalPages = totalPages,
                        resolvedLibraryId = cachedLibraryId
                    )

                    val settings = _uiState.value.settings
                    val currentChapterId = _uiState.value.chapterId
                    when (settings.autoDeleteOption) {
                        AutoDeleteOption.IMMEDIATELY -> {
                            downloadRepository.deleteLocalChapter(currentChapterId)
                        }
                        AutoDeleteOption.AFTER_1_ISSUE,
                        AutoDeleteOption.AFTER_2_ISSUES,
                        AutoDeleteOption.AFTER_5_ISSUES -> {
                            preferencesRepository.addCompletedChapter(currentChapterId)
                            val history = preferencesRepository.completedChapterHistoryFlow.first()
                            val keepOffset = when (settings.autoDeleteOption) {
                                AutoDeleteOption.AFTER_1_ISSUE -> 1
                                AutoDeleteOption.AFTER_2_ISSUES -> 2
                                AutoDeleteOption.AFTER_5_ISSUES -> 5
                                else -> 1
                            }
                            val cutoffIndex = history.size - 1 - keepOffset
                            if (cutoffIndex >= 0) {
                                val toDelete = history.subList(0, cutoffIndex + 1)
                                toDelete.forEach { chapterIdToDelete ->
                                    downloadRepository.deleteLocalChapter(chapterIdToDelete)
                                }
                                preferencesRepository.removeCompletedChapters(toDelete)
                            }
                        }
                        AutoDeleteOption.NEVER -> {}
                    }
                }
            }
        }
    }

    fun nextPage() {
        val current = _uiState.value.currentPageIndex
        if (current < _uiState.value.pages.size - 1) {
            onPageChanged(current + 1)
        } else if (current >= _uiState.value.pages.size - 1 && _uiState.value.pages.isNotEmpty()) {
            loadNextChapter()
        }
    }

    fun previousPage() {
        val current = _uiState.value.currentPageIndex
        if (current > 0) {
            onPageChanged(current - 1)
        } else if (_uiState.value.pages.isNotEmpty()) {
            loadPreviousChapter()
        }
    }

    fun loadNextChapter() {
        val state = _uiState.value
        viewModelScope.launch {
            val effectiveReadingListId = if (state.readingListId > 0) {
                state.readingListId
            } else if (state.seriesId > 0) {
                val savedContext = preferencesRepository.seriesReadContextsFlow.first()[state.seriesId]
                if (savedContext?.originType == "READING_LIST" && savedContext.readingListId > 0) {
                    savedContext.readingListId
                } else 0
            } else 0

            if (effectiveReadingListId > 0) {
                val listRes = readingListRepository.getReadingListItems(effectiveReadingListId)
                if (listRes.isSuccess) {
                    val items = listRes.getOrNull() ?: emptyList()
                    val currentIndex = items.indexOfFirst { it.chapterId == state.chapterId }
                    if (currentIndex in 0 until items.size - 1) {
                        val nextItem = items[currentIndex + 1]
                        if (nextItem.chapterId != null && nextItem.chapterId > 0) {
                            loadChapter(
                                chapterId = nextItem.chapterId,
                                totalPages = nextItem.pages,
                                seriesId = nextItem.seriesId,
                                volumeId = nextItem.volumeId ?: 0,
                                readingListId = effectiveReadingListId,
                                openAtLastPage = false
                            )
                            return@launch
                        }
                    }
                }
            } else if (state.seriesId > 0) {
                val volumesRes = seriesRepository.getSeriesVolumes(state.seriesId)
                if (volumesRes.isSuccess) {
                    val allChapters = volumesRes.getOrDefault(emptyList()).flatMap { it.chapters }
                    val currentIndex = allChapters.indexOfFirst { it.id == state.chapterId }
                    if (currentIndex in 0 until allChapters.size - 1) {
                        val nextChapter = allChapters[currentIndex + 1]
                        loadChapter(
                            chapterId = nextChapter.id,
                            totalPages = nextChapter.pages,
                            seriesId = state.seriesId,
                            volumeId = nextChapter.volumeId,
                            readingListId = 0,
                            openAtLastPage = false
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun loadPreviousChapter() {
        val state = _uiState.value
        viewModelScope.launch {
            val effectiveReadingListId = if (state.readingListId > 0) {
                state.readingListId
            } else if (state.seriesId > 0) {
                val savedContext = preferencesRepository.seriesReadContextsFlow.first()[state.seriesId]
                if (savedContext?.originType == "READING_LIST" && savedContext.readingListId > 0) {
                    savedContext.readingListId
                } else 0
            } else 0

            if (effectiveReadingListId > 0) {
                val listRes = readingListRepository.getReadingListItems(effectiveReadingListId)
                if (listRes.isSuccess) {
                    val items = listRes.getOrNull() ?: emptyList()
                    val currentIndex = items.indexOfFirst { it.chapterId == state.chapterId }
                    if (currentIndex in 1 until items.size) {
                        val prevItem = items[currentIndex - 1]
                        if (prevItem.chapterId != null && prevItem.chapterId > 0) {
                            loadChapter(
                                chapterId = prevItem.chapterId,
                                totalPages = prevItem.pages,
                                seriesId = prevItem.seriesId,
                                volumeId = prevItem.volumeId ?: 0,
                                readingListId = effectiveReadingListId,
                                openAtLastPage = true
                            )
                            return@launch
                        }
                    }
                }
            } else if (state.seriesId > 0) {
                val volumesRes = seriesRepository.getSeriesVolumes(state.seriesId)
                if (volumesRes.isSuccess) {
                    val allChapters = volumesRes.getOrDefault(emptyList()).flatMap { it.chapters }
                    val currentIndex = allChapters.indexOfFirst { it.id == state.chapterId }
                    if (currentIndex in 1 until allChapters.size) {
                        val prevChapter = allChapters[currentIndex - 1]
                        loadChapter(
                            chapterId = prevChapter.id,
                            totalPages = prevChapter.pages,
                            seriesId = state.seriesId,
                            volumeId = prevChapter.volumeId,
                            readingListId = 0,
                            openAtLastPage = true
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun updateReadingMode(mode: ReadingMode) {
        sessionReadingModeOverride = mode
        val currentSettings = _uiState.value.settings
        val isRtl = (mode == ReadingMode.RTL)
        val reSortedBubblesByPage = _uiState.value.bubblesByPage.mapValues { (_, pageBubbles) ->
            pageBubbles.copy(bubbles = orderBubbles(pageBubbles, null, isRtl))
        }
        _uiState.value = _uiState.value.copy(
            settings = currentSettings.copy(readingMode = mode),
            bubblesByPage = reSortedBubblesByPage
        )
    }

    fun updateFitMode(mode: FitMode) {
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(settings = currentSettings.copy(fitMode = mode))
        viewModelScope.launch {
            preferencesRepository.updateFitMode(mode)
        }
    }

    fun updateTouchEdgeNav(enabled: Boolean) {
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(settings = currentSettings.copy(touchEdgeNavigationEnabled = enabled))
        viewModelScope.launch {
            preferencesRepository.updateTouchEdgeNavigation(enabled)
        }
    }

    fun updateKeepScreenOn(keep: Boolean) {
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(settings = currentSettings.copy(keepScreenOn = keep))
        viewModelScope.launch {
            preferencesRepository.updateKeepScreenOn(keep)
        }
    }

    fun updatePanelAwareOrder(enabled: Boolean) {
        val currentSettings = _uiState.value.settings
        val isRtl = (currentSettings.readingMode == ReadingMode.RTL)
        val reSortedBubblesByPage = _uiState.value.bubblesByPage.mapValues { (pageIdx, pageBubbles) ->
            val pMap = _uiState.value.panelMapsByPage[pageIdx]
            pageBubbles.copy(bubbles = orderBubbles(pageBubbles, pMap, isRtl, enabled))
        }
        _uiState.value = _uiState.value.copy(
            settings = currentSettings.copy(panelAwareOrderEnabled = enabled),
            bubblesByPage = reSortedBubblesByPage
        )
        viewModelScope.launch {
            preferencesRepository.updatePanelAwareOrder(enabled)
        }
    }

    fun updatePanelDebugOverlay(enabled: Boolean) {
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(
            settings = currentSettings.copy(panelDebugOverlayEnabled = enabled)
        )
        viewModelScope.launch {
            preferencesRepository.updatePanelDebugOverlay(enabled)
        }
    }

    fun handleVolumeKey(isVolumeUp: Boolean): Boolean {
        if (!_uiState.value.settings.volumeKeyNavigationEnabled) return false

        val state = _uiState.value
        val invert = state.settings.volumeKeyInvert
        val isNext = if (invert) isVolumeUp else !isVolumeUp

        val currentMag = state.magnifiedBubble
        if (currentMag != null) {
            val pageIdx = state.currentPageIndex
            val pageBubbles = state.bubblesByPage[pageIdx]
            val bubbles = pageBubbles?.bubbles ?: emptyList()
            val currentIdx = bubbles.indexOfFirst { it.id == currentMag.bubbleId }

            if (isNext) {
                if (currentIdx != -1 && currentIdx < bubbles.size - 1 && pageBubbles != null) {
                    val nextBubble = bubbles[currentIdx + 1]
                    magnifyBubbleObject(pageIdx, nextBubble, pageBubbles)
                    return true
                } else {
                    completedBubblePageIndex = pageIdx
                    _uiState.value = _uiState.value.copy(magnifiedBubble = null)
                    return true
                }
            } else {
                if (currentIdx > 0 && pageBubbles != null) {
                    val prevBubble = bubbles[currentIdx - 1]
                    magnifyBubbleObject(pageIdx, prevBubble, pageBubbles)
                    return true
                } else {
                    _uiState.value = _uiState.value.copy(magnifiedBubble = null)
                    return true
                }
            }
        }

        if (isNext) {
            if (state.settings.speechBubbleDetectionEnabled && state.settings.readingMode != ReadingMode.WEBTOON) {
                val pageIdx = state.currentPageIndex
                if (completedBubblePageIndex == pageIdx) {
                    nextPage()
                    return true
                }

                val pageBubbles = state.bubblesByPage[pageIdx]
                if (pageBubbles != null) {
                    val bubbles = pageBubbles.bubbles
                    if (bubbles.isNotEmpty()) {
                        magnifyBubbleObject(pageIdx, bubbles.first(), pageBubbles)
                    } else {
                        completedBubblePageIndex = pageIdx
                        nextPage()
                    }
                } else if (bubbleDetectionJobs.containsKey(pageIdx)) {
                    viewModelScope.launch {
                        bubbleDetectionJobs[pageIdx]?.join()
                        if (_uiState.value.currentPageIndex != pageIdx) return@launch
                        val pb = _uiState.value.bubblesByPage[pageIdx]
                        val bList = pb?.bubbles ?: emptyList()
                        if (bList.isNotEmpty() && completedBubblePageIndex != pageIdx) {
                            magnifyBubbleObject(pageIdx, bList.first(), pb!!)
                        } else {
                            completedBubblePageIndex = pageIdx
                            nextPage()
                        }
                    }
                } else {
                    nextPage()
                }
            } else {
                nextPage()
            }
        } else {
            previousPage()
        }
        return true
    }

    override fun onCleared() {
        flushProgress()
        super.onCleared()
    }
}
