package com.zemmy.sigurvita.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    val bubblesByPage: Map<Int, com.zemmy.sigurvita.core.model.PageBubbles> = emptyMap(),
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

    fun loadChapter(chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int, readingListId: Int = 0) {
        // Flush progress for the previous chapter before loading a new one
        flushProgress()
        // Reset cached library ID for new chapter context
        cachedLibraryId = 0

        bubbleDetectionJobs.values.forEach { it.cancel() }
        bubbleDetectionJobs.clear()
        _uiState.value = _uiState.value.copy(bubblesByPage = emptyMap())

        markedPageJob?.cancel()
        markedPageJob = viewModelScope.launch {
            app.markedPageRepository.getMarkedPageIndicesForChapter(chapterId).collect { indices ->
                _uiState.value = _uiState.value.copy(markedPageIndices = indices.toSet())
            }
        }

        viewModelScope.launch {
            val userSettings = preferencesRepository.readerSettingsFlow.first()

            var targetSeriesId = seriesId
            var targetVolumeId = volumeId
            var fetchedSeriesName = ""
            var fetchedChapterTitle = ""
            var isManga = false
            var defaultMode = ReadingMode.LTR

            // 1. Resolve targetSeriesId if not provided in arguments
            if (targetSeriesId <= 0) {
                try {
                    val cachedChapter = app.database.chapterDao().getChapterById(chapterId)
                    if (cachedChapter != null && cachedChapter.seriesId > 0) {
                        targetSeriesId = cachedChapter.seriesId
                        if (targetVolumeId <= 0) targetVolumeId = cachedChapter.volumeId
                    } else {
                        val cRes = app.kavitaApi.getChapterById(chapterId)
                        if (cRes.isSuccessful && cRes.body() != null) {
                            val cDto = cRes.body()!!
                            if (targetVolumeId <= 0) targetVolumeId = cDto.volumeId
                        }
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

                        val libraryRes = libraryRepository.getLibraries()
                        val library = libraryRes.getOrNull()?.find { it.id == series.libraryId }
                        if (library != null) {
                            cachedLibraryId = library.id
                            isManga = library.isManga
                            defaultMode = if (isManga) ReadingMode.RTL else ReadingMode.LTR
                        }
                    }

                    val volumesRes = seriesRepository.getSeriesVolumes(targetSeriesId)
                    if (volumesRes.isSuccess && volumesRes.getOrNull() != null) {
                        val volumes = volumesRes.getOrNull()!!
                        val matchingChapter = volumes.flatMap { it.chapters }.find { it.id == chapterId }
                        if (matchingChapter != null) {
                            fetchedChapterTitle = formatChapterDisplayTitle(matchingChapter.title, matchingChapter.chapterNumber, isManga = isManga)
                        }
                    }
                }
            } catch (_: Exception) {}

            if (fetchedChapterTitle.isBlank()) {
                try {
                    val cachedChapter = app.database.chapterDao().getChapterById(chapterId)
                    if (cachedChapter != null) {
                        fetchedChapterTitle = formatChapterDisplayTitle(cachedChapter.title, cachedChapter.chapterNumber, isManga = isManga)
                    } else {
                        val cRes = app.kavitaApi.getChapterById(chapterId)
                        if (cRes.isSuccessful && cRes.body() != null) {
                            val cDto = cRes.body()!!
                            fetchedChapterTitle = formatChapterDisplayTitle(cDto.titleName ?: cDto.title ?: "", cDto.number ?: "", isManga = isManga)
                        }
                    }
                } catch (_: Exception) {}
            }

            val activeReadingMode = sessionReadingModeOverride ?: defaultMode
            val effectiveSettings = userSettings.copy(
                readingMode = activeReadingMode,
                isIncognitoMode = userSettings.isIncognitoMode
            )

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
                seriesName = fetchedSeriesName
            )

            val pageList = readerRepository.getPages(chapterId, totalPages)
            val savedPageNum = readerRepository.getProgress(chapterId)

            val hasSavedProgress = savedPageNum > 1 && pageList.isNotEmpty()

            val activeIndex = if (hasSavedProgress) {
                if (savedPageNum - 1 in pageList.indices) savedPageNum - 1 else 0
            } else 0

            _uiState.value = _uiState.value.copy(
                pages = pageList,
                currentPageIndex = activeIndex,
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
            val pageBubbles = app.bubbleRepository.getPageBubbles(chapterId, page, state.settings.speechBubbleConfidence)
            if (pageBubbles != null) {
                _uiState.value = _uiState.value.copy(
                    bubblesByPage = _uiState.value.bubblesByPage + (pageIdx to pageBubbles)
                )
            }
            bubbleDetectionJobs.remove(pageIdx)
        }
    }

    fun updateSpeechBubbleDetectionEnabled(enabled: Boolean) {
        if (!enabled) {
            bubbleDetectionJobs.values.forEach { it.cancel() }
            bubbleDetectionJobs.clear()
            _uiState.value = _uiState.value.copy(
                settings = _uiState.value.settings.copy(speechBubbleDetectionEnabled = enabled),
                bubblesByPage = emptyMap(),
                magnifiedBubble = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                settings = _uiState.value.settings.copy(speechBubbleDetectionEnabled = enabled)
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

        viewModelScope.launch {
            val cropBmp = app.bubbleRepository.cropBubble(app, page, bubble, pageBubbles.letterbox, state.settings.speechBubbleBoxesEnabled)
            if (cropBmp != null) {
                _uiState.value = _uiState.value.copy(
                    magnifiedBubble = com.zemmy.sigurvita.core.model.MagnifiedBubble(
                        pageIndex = pageIndex,
                        bubbleId = bubbleId,
                        bitmap = cropBmp,
                        centerOffsetPx = centerOffsetPx,
                        bubbleSizePx = bubbleSizePx
                    )
                )
            }
        }
    }

    fun dismissBubble() {
        _uiState.value = _uiState.value.copy(magnifiedBubble = null)
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

    fun onPageChanged(index: Int) {
        if (_uiState.value.showResumePrompt) return
        if (index in _uiState.value.pages.indices) {
            _uiState.value = _uiState.value.copy(currentPageIndex = index)
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
                val totalPages = _uiState.value.pages.size
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
        }
    }

    fun loadNextChapter() {
        val state = _uiState.value
        viewModelScope.launch {
            if (state.readingListId > 0) {
                val listRes = readingListRepository.getReadingListItems(state.readingListId)
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
                                readingListId = state.readingListId
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
                            readingListId = 0
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(controlsVisible = !_uiState.value.controlsVisible)
    }

    fun updateReadingMode(mode: ReadingMode) {
        sessionReadingModeOverride = mode
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(settings = currentSettings.copy(readingMode = mode))
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

    fun handleVolumeKey(isVolumeUp: Boolean): Boolean {
        if (!_uiState.value.settings.volumeKeyNavigationEnabled) return false

        val invert = _uiState.value.settings.volumeKeyInvert
        val isNext = if (invert) isVolumeUp else !isVolumeUp

        if (isNext) {
            nextPage()
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
