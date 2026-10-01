package com.zemmy.sigurvita.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
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
    val promptPageNum: Int = 0
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

    fun loadChapter(chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int, readingListId: Int = 0) {
        // Flush progress for the previous chapter before loading a new one
        flushProgress()
        // Reset cached library ID for new chapter context
        cachedLibraryId = 0

        viewModelScope.launch {
            val userSettings = preferencesRepository.readerSettingsFlow.first()

            var effectiveReadingMode = userSettings.readingMode
            var defaultMode = ReadingMode.LTR
            try {
                if (seriesId > 0) {
                    val seriesRes = seriesRepository.getSeriesDetail(seriesId)
                    if (seriesRes.isSuccess) {
                        val series = seriesRes.getOrNull()
                        val libraryRes = libraryRepository.getLibraries()
                        val library = libraryRes.getOrNull()?.find { it.id == series?.libraryId }
                        if (library != null) {
                            cachedLibraryId = library.id
                            val isManga = library.type == 0 || library.name.lowercase().contains("manga")
                            defaultMode = if (isManga) ReadingMode.RTL else ReadingMode.LTR
                            effectiveReadingMode = defaultMode
                        }
                    }
                }
            } catch (_: Exception) {}

            val effectiveSettings = userSettings.copy(readingMode = effectiveReadingMode)

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                settings = effectiveSettings,
                libraryDefaultMode = defaultMode,
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId,
                readingListId = readingListId,
                showResumePrompt = false
            )

            val pageList = readerRepository.getPages(chapterId, totalPages)
            val savedPageNum = readerRepository.getProgress(chapterId)

            val hasSavedProgress = savedPageNum > 1 && pageList.isNotEmpty()

            if (hasSavedProgress) {
                _uiState.value = _uiState.value.copy(
                    pages = pageList,
                    currentPageIndex = 0,
                    isLoading = false,
                    showResumePrompt = true,
                    promptPageNum = savedPageNum
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    pages = pageList,
                    currentPageIndex = 0,
                    isLoading = false,
                    showResumePrompt = false,
                    promptPageNum = 0
                )
            }
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
        if (index in 0 until _uiState.value.pages.size) {
            _uiState.value = _uiState.value.copy(currentPageIndex = index)
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
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(settings = currentSettings.copy(readingMode = mode))
        viewModelScope.launch {
            preferencesRepository.updateReadingMode(mode)
        }
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
