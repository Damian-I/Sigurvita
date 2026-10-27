package com.zemmy.sigurvita.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.CustomTapZone
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderBackground
import com.zemmy.sigurvita.core.model.ReaderOrientation
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.core.model.TapZoneLayout
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import com.zemmy.sigurvita.core.util.orderBubbles
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    val magnifiedBubble: com.zemmy.sigurvita.core.model.MagnifiedBubble? = null,
    /** Speech-bubble navigation mode: edge taps and volume keys step through bubbles. Session-only, never persisted. */
    val bubbleNavActive: Boolean = false,
    val prevChapter: ChapterRef? = null,
    val nextChapter: ChapterRef? = null
)

/** A chapter the reader can jump to, resolved from the active context (reading list or series order). */
data class ChapterRef(
    val chapterId: Int,
    val totalPages: Int,
    val seriesId: Int,
    val volumeId: Int,
    val readingListId: Int
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

    private var sessionReadingModeOverride: ReadingMode? = null

    private val progress = ReaderProgressTracker(
        scope = viewModelScope,
        state = _uiState,
        readerRepository = readerRepository,
        downloadRepository = downloadRepository,
        preferencesRepository = preferencesRepository
    )

    // Flush any pending debounced progress immediately (called on chapter exit).
    suspend fun flushProgressSync() = progress.flushSync()

    fun flushProgress() = progress.flush()

    private var markedPageJob: Job? = null
    private var loadChapterJob: Job? = null

    private val detection = ReaderDetectionScheduler(
        app = app,
        scope = viewModelScope,
        state = _uiState,
        nextChapterOf = { s -> s.nextChapter ?: findNeighbors(s.chapterId, s.seriesId, s.readingListId).second }
    )

    private val bubbleNav = ReaderBubbleNavigator(
        app = app,
        scope = viewModelScope,
        state = _uiState,
        detection = detection,
        nextPage = { nextPage() },
        previousPage = { previousPage() }
    )
    fun loadChapter(
        chapterId: Int,
        totalPages: Int,
        seriesId: Int,
        volumeId: Int,
        readingListId: Int = 0,
        openAtLastPage: Boolean = false,
        forceFirstPage: Boolean = false
    ) {
        // Flush progress for the previous chapter before loading a new one
        flushProgress()
        // A newer load supersedes any load still in flight
        loadChapterJob?.cancel()
        // Reset cached library ID for new chapter context
        progress.libraryId = 0
        detection.cancelPageJobs()
        bubbleNav.onNewChapter()
        _uiState.update {
            it.copy(
                bubblesByPage = emptyMap(),
                panelMapsByPage = emptyMap(),
                magnifiedBubble = null
            )
        }

        markedPageJob?.cancel()
        markedPageJob = viewModelScope.launch {
            app.markedPageRepository.getMarkedPageIndicesForChapter(chapterId).collect { indices ->
                _uiState.update { it.copy(markedPageIndices = indices.toSet()) }
            }
        }

        loadChapterJob = viewModelScope.launch {
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
                            progress.libraryId = library.id
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
            // The fetch blocks above swallow cancellation, so stop here if this load was superseded or the reader closed
            ensureActive()
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

            if (targetSeriesId > 0 && !effectiveSettings.isIncognitoMode) {
                if (readingListId > 0) {
                    // Reuse the title already saved for this series + list; otherwise do one lookup for
                    // this list only (not progress for every list on the server).
                    val savedTitle = try {
                        preferencesRepository.seriesReadContextsFlow.first()[targetSeriesId]
                            ?.takeIf { it.originType == "READING_LIST" && it.readingListId == readingListId }
                            ?.readingListTitle
                            ?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null }
                    val listTitle = savedTitle ?: try {
                        val listRes = app.readingListRepository.getReadingLists(setOf(readingListId))
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

            _uiState.update {
                it.copy(
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
            }
            scheduleAutoHideControls()
            refreshNeighbors(chapterId, targetSeriesId, readingListId)

            val pageList = readerRepository.getPages(chapterId, totalPages)
            val savedPageNum = readerRepository.getProgress(chapterId)

            val hasSavedProgress = !openAtLastPage && !forceFirstPage && savedPageNum > 1 && pageList.isNotEmpty()
            val activeIndex = if (openAtLastPage && pageList.isNotEmpty()) {
                pageList.size - 1
            } else if (hasSavedProgress) {
                if (savedPageNum - 1 in pageList.indices) savedPageNum - 1 else 0
            } else 0

            val updatedChapter = finalChapter.copy(
                pages = if (pageList.isNotEmpty()) pageList.size else totalPages,
                pagesRead = activeIndex + 1
            )

            _uiState.update {
                it.copy(
                    pages = pageList,
                    currentPageIndex = activeIndex,
                    currentChapter = updatedChapter,
                    isLoading = false,
                    showResumePrompt = hasSavedProgress,
                    promptPageNum = if (hasSavedProgress) savedPageNum else 0
                )
            }

            detection.startPreload(activeIndex)
        }
    }

    fun ensureBubbles(pageIdx: Int) = detection.ensureBubbles(pageIdx)
    fun updateSpeechBubbleDetectionEnabled(enabled: Boolean) {
        resetBubbleNavMode()
        if (!enabled) {
            detection.cancelPageJobs()
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(speechBubbleDetectionEnabled = false),
                    bubblesByPage = emptyMap(),
                    magnifiedBubble = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(speechBubbleDetectionEnabled = true)
                )
            }
            for (offset in 0..3) {
                ensureBubbles(_uiState.value.currentPageIndex + offset)
            }
        }
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleDetectionEnabled(enabled)
        }
    }

    fun updateSpeechBubbleBoxesEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                settings = it.settings.copy(speechBubbleBoxesEnabled = enabled)
            )
        }
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleBoxesEnabled(enabled)
        }
    }

    fun updateSpeechBubbleConfidence(confidence: Float) {
        resetBubbleNavMode()
        detection.cancelPageJobs()

        _uiState.update {
            it.copy(
                settings = it.settings.copy(speechBubbleConfidence = confidence),
                bubblesByPage = emptyMap()
            )
        }
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleConfidence(confidence)
        }
        app.bubbleRepository.clearChapter(_uiState.value.chapterId)
        for (offset in 0..3) {
            ensureBubbles(_uiState.value.currentPageIndex + offset)
        }
    }

    fun updateScreenSize(widthPx: Int, heightPx: Int) = bubbleNav.updateScreenSize(widthPx, heightPx)

    @Suppress("UNUSED_PARAMETER")
    fun onBubbleLongPress(
        pageIndex: Int,
        bubbleId: Int,
        centerOffsetPx: androidx.compose.ui.geometry.Offset,
        bubbleSizePx: androidx.compose.ui.geometry.Size
    ) = bubbleNav.onBubbleLongPress(pageIndex, bubbleId)

    fun resetBubbleNavMode() = bubbleNav.resetBubbleNavMode()

    fun updateBubbleNavActive(enabled: Boolean) = bubbleNav.updateBubbleNavActive(enabled)

    fun toggleBubbleNavMode(): Boolean = bubbleNav.toggleBubbleNavMode()

    fun dismissBubble() = bubbleNav.resetBubbleNavMode()

    @Suppress("UNUSED_PARAMETER")
    fun onBubbleHover(
        pageIndex: Int,
        bubbleId: Int?,
        centerOffsetPx: androidx.compose.ui.geometry.Offset?,
        bubbleSizePx: androidx.compose.ui.geometry.Size?
    ) = bubbleNav.onBubbleHover(pageIndex, bubbleId)

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
        _uiState.update { it.copy(showResumePrompt = false) }
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
            _uiState.update { it.copy(controlsVisible = false) }
        }
    }

    fun cancelAutoHideControls() {
        autoHideControlsJob?.cancel()
    }

    fun toggleControls() {
        val currentVisible = _uiState.value.controlsVisible
        if (currentVisible) {
            cancelAutoHideControls()
            _uiState.update { it.copy(controlsVisible = false) }
        } else {
            _uiState.update { it.copy(controlsVisible = true) }
            scheduleAutoHideControls()
        }
    }

    fun toggleChapterRead(chapter: Chapter) {
        val newReadState = !chapter.isRead
        val updatedChapter = chapter.copy(
            isRead = newReadState,
            pagesRead = if (newReadState) chapter.pages else chapter.pagesRead
        )
        _uiState.update { it.copy(currentChapter = updatedChapter) }

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
                _uiState.update {
                    it.copy(
                        currentChapter = currentCh.copy(isDownloaded = false, localPath = null)
                    )
                }
            }
        }
    }

    fun onPageChanged(index: Int) {
        if (_uiState.value.showResumePrompt) return
        if (index in _uiState.value.pages.indices) {
            val totalPages = _uiState.value.pages.size
            val currentCh = _uiState.value.currentChapter
            val updatedCh = currentCh?.copy(
                pagesRead = index + 1,
                isRead = (index + 1 >= totalPages && totalPages > 0)
            )

            // Lets volume-key bubble navigation tell "continue" from "reverse" (single-page steps only)
            bubbleNav.onPageChanged(previousIndex = _uiState.value.currentPageIndex, newIndex = index)

            _uiState.update {
                it.copy(
                    currentPageIndex = index,
                    currentChapter = updatedCh ?: currentCh
                )
            }

            if (_uiState.value.controlsVisible) {
                scheduleAutoHideControls()
            }

            detection.onPageChanged(index)
            progress.onPageShown(index)
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

    private var neighborsJob: Job? = null

    /**
     * Previous/next chapter for the active context. Same rule as Home's On Deck:
     * reading list (nav argument, or the series' saved READING_LIST context), otherwise series order.
     * Returns (previous, next); null at a boundary or when the lookup fails.
     */
    private suspend fun findNeighbors(
        chapterId: Int,
        seriesId: Int,
        readingListId: Int
    ): Pair<ChapterRef?, ChapterRef?> {
        val effectiveReadingListId = if (readingListId > 0) {
            readingListId
        } else if (seriesId > 0) {
            val savedContext = preferencesRepository.seriesReadContextsFlow.first()[seriesId]
            if (savedContext?.originType == "READING_LIST" && savedContext.readingListId > 0) {
                savedContext.readingListId
            } else 0
        } else 0

        if (effectiveReadingListId > 0) {
            val items = readingListRepository.getReadingListItems(effectiveReadingListId, resolveDetails = false).getOrNull()
                ?: return Pair(null, null)
            val currentIndex = items.indexOfFirst { it.chapterId == chapterId }
            if (currentIndex < 0) return Pair(null, null)

            fun refAt(index: Int): ChapterRef? {
                val item = items.getOrNull(index) ?: return null
                val id = item.chapterId ?: return null
                if (id <= 0) return null
                return ChapterRef(id, item.pages, item.seriesId, item.volumeId ?: 0, effectiveReadingListId)
            }
            return Pair(refAt(currentIndex - 1), refAt(currentIndex + 1))
        }

        if (seriesId > 0) {
            val chapters = seriesRepository.getSeriesVolumes(seriesId).getOrNull()?.flatMap { it.chapters }
                ?: return Pair(null, null)
            val currentIndex = chapters.indexOfFirst { it.id == chapterId }
            if (currentIndex < 0) return Pair(null, null)

            fun refAt(index: Int): ChapterRef? {
                val chapter = chapters.getOrNull(index) ?: return null
                return ChapterRef(chapter.id, chapter.pages, seriesId, chapter.volumeId, 0)
            }
            return Pair(refAt(currentIndex - 1), refAt(currentIndex + 1))
        }

        return Pair(null, null)
    }

    /** Resolves prev/next for the chapter that was just loaded; the overlay buttons read these from state. */
    private fun refreshNeighbors(chapterId: Int, seriesId: Int, readingListId: Int) {
        neighborsJob?.cancel()
        _uiState.update { it.copy(prevChapter = null, nextChapter = null) }
        neighborsJob = viewModelScope.launch {
            try {
                val (prev, next) = findNeighbors(chapterId, seriesId, readingListId)
                if (_uiState.value.chapterId == chapterId) {
                    _uiState.update { it.copy(prevChapter = prev, nextChapter = next) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Leave the buttons disabled if the lookup fails.
            }
        }
    }

    private fun openChapter(
        ref: ChapterRef,
        openAtLastPage: Boolean = false,
        forceFirstPage: Boolean = false
    ) {
        loadChapter(
            chapterId = ref.chapterId,
            totalPages = ref.totalPages,
            seriesId = ref.seriesId,
            volumeId = ref.volumeId,
            readingListId = ref.readingListId,
            openAtLastPage = openAtLastPage,
            forceFirstPage = forceFirstPage
        )
    }

    /** Overlay "next chapter" button. */
    fun onNextChapterClick() {
        val next = _uiState.value.nextChapter ?: return
        bubbleNav.pageArrivalDirection = 1
        openChapter(next)
    }

    /** Overlay "previous chapter" button: opens page 1 of the previous chapter. */
    fun onPreviousChapterClick() {
        val prev = _uiState.value.prevChapter ?: return
        bubbleNav.pageArrivalDirection = 1
        openChapter(prev, forceFirstPage = true)
    }

    /** Auto-advance after the last page. */
    fun loadNextChapter() {
        val state = _uiState.value
        viewModelScope.launch {
            val next = state.nextChapter
                ?: findNeighbors(state.chapterId, state.seriesId, state.readingListId).second
                ?: return@launch
            bubbleNav.pageArrivalDirection = 1
            openChapter(next)
        }
    }

    /** Swipe/tap back from page 1: opens the previous chapter at its last page. */
    fun loadPreviousChapter() {
        val state = _uiState.value
        viewModelScope.launch {
            val prev = state.prevChapter
                ?: findNeighbors(state.chapterId, state.seriesId, state.readingListId).first
                ?: return@launch
            bubbleNav.pageArrivalDirection = -1
            openChapter(prev, openAtLastPage = true)
        }
    }

    fun updateReadingMode(mode: ReadingMode) {
        // Bubble navigation does not exist in webtoon mode; do not leave the mode (and its switch) on.
        if (mode == ReadingMode.WEBTOON) resetBubbleNavMode()
        sessionReadingModeOverride = mode
        val currentSettings = _uiState.value.settings
        val isRtl = (mode == ReadingMode.RTL)
        val reSortedBubblesByPage = _uiState.value.bubblesByPage.mapValues { (_, pageBubbles) ->
            pageBubbles.copy(bubbles = orderBubbles(pageBubbles, null, isRtl))
        }
        _uiState.update {
            it.copy(
                settings = currentSettings.copy(readingMode = mode),
                bubblesByPage = reSortedBubblesByPage
            )
        }
    }

    fun updateFitMode(mode: FitMode) {
        val currentSettings = _uiState.value.settings
        _uiState.update { it.copy(settings = currentSettings.copy(fitMode = mode)) }
        viewModelScope.launch {
            preferencesRepository.updateFitMode(mode)
        }
    }

    fun updateTouchEdgeNav(enabled: Boolean) {
        val currentSettings = _uiState.value.settings
        _uiState.update { it.copy(settings = currentSettings.copy(touchEdgeNavigationEnabled = enabled)) }
        viewModelScope.launch {
            preferencesRepository.updateTouchEdgeNavigation(enabled)
        }
    }
    fun updateTapZoneLayout(layout: TapZoneLayout) {
        _uiState.update { it.copy(settings = it.settings.copy(tapZoneLayout = layout)) }
        viewModelScope.launch { preferencesRepository.updateTapZoneLayout(layout) }
    }

    fun updateShowTapZones(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(showTapZones = enabled)) }
        viewModelScope.launch { preferencesRepository.updateShowTapZones(enabled) }
    }

    fun updateCustomTapZones(zones: List<CustomTapZone>) {
        _uiState.update { it.copy(settings = it.settings.copy(customTapZones = zones)) }
        viewModelScope.launch { preferencesRepository.updateCustomTapZones(zones) }
    }

    fun updateCustomZonesMirrorInRtl(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(customZonesMirrorInRtl = enabled)) }
        viewModelScope.launch { preferencesRepository.updateCustomZonesMirrorInRtl(enabled) }
    }
    fun updateKeepScreenOn(keep: Boolean) {
        val currentSettings = _uiState.value.settings
        _uiState.update { it.copy(settings = currentSettings.copy(keepScreenOn = keep)) }
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
        _uiState.update {
            it.copy(
                settings = currentSettings.copy(panelAwareOrderEnabled = enabled),
                bubblesByPage = reSortedBubblesByPage
            )
        }
        viewModelScope.launch {
            preferencesRepository.updatePanelAwareOrder(enabled)
        }
    }

    fun updatePanelDebugOverlay(enabled: Boolean) {
        val currentSettings = _uiState.value.settings
        _uiState.update {
            it.copy(
                settings = currentSettings.copy(panelDebugOverlayEnabled = enabled)
            )
        }
        viewModelScope.launch {
            preferencesRepository.updatePanelDebugOverlay(enabled)
        }
    }

    fun updateShowPageNumber(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(showPageNumber = enabled)) }
        viewModelScope.launch { preferencesRepository.updateShowPageNumber(enabled) }
    }

    fun updateOrientation(orientation: ReaderOrientation) {
        _uiState.update { it.copy(settings = it.settings.copy(orientation = orientation)) }
        viewModelScope.launch { preferencesRepository.updateReaderOrientation(orientation) }
    }

    fun updateReaderBackground(background: ReaderBackground) {
        _uiState.update { it.copy(settings = it.settings.copy(readerBackground = background)) }
        viewModelScope.launch { preferencesRepository.updateReaderBackground(background) }
    }

    fun updateVolumeKeyNavigation(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(volumeKeyNavigationEnabled = enabled)) }
        viewModelScope.launch { preferencesRepository.updateVolumeKeyNavigation(enabled) }
    }

    fun updateVolumeKeyInvert(invert: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(volumeKeyInvert = invert)) }
        viewModelScope.launch { preferencesRepository.updateVolumeKeyInvert(invert) }
    }

    /** Whether the volume keys are ours to handle; `MainActivity` checks this before consuming a key event. */
    val isVolumeKeyNavigationEnabled: Boolean
        get() = _uiState.value.settings.volumeKeyNavigationEnabled

    fun handleVolumeKey(isVolumeUp: Boolean): Boolean = bubbleNav.handleVolumeKey(isVolumeUp)

    /** A tap in one of the page-transition zones. [isNext] already accounts for the reading direction. */
    fun onEdgeTapNavigate(isNext: Boolean) = bubbleNav.onEdgeTapNavigate(isNext)

    /** Stops background detection when the reader screen is left. Results already computed stay cached. */
    fun onReaderClosed() {
        loadChapterJob?.cancel()
        detection.onReaderClosed()
        bubbleNav.pageArrivalDirection = 0
    }
}
