package com.zemmy.sigurvita.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.repository.DownloadRepository
import com.zemmy.sigurvita.core.data.repository.LibraryRepository
import com.zemmy.sigurvita.core.data.repository.ReaderRepository
import com.zemmy.sigurvita.core.data.repository.SeriesRepository
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReaderUiState(
    val pages: List<ReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val isLoading: Boolean = true,
    val controlsVisible: Boolean = true,
    val settings: ReaderSettings = ReaderSettings(),
    val chapterId: Int = 0,
    val seriesId: Int = 0,
    val volumeId: Int = 0
)

class ReaderViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val api = NetworkClient.createKavitaApi(preferencesRepository)
    private val readerRepository = ReaderRepository(api, app, preferencesRepository, app.database)
    private val seriesRepository = SeriesRepository(api, preferencesRepository)
    private val libraryRepository = LibraryRepository(api, app.database, preferencesRepository)
    private val downloadRepository = DownloadRepository(app.database, app)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun loadChapter(chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int) {
        viewModelScope.launch {
            val userSettings = preferencesRepository.readerSettingsFlow.first()

            var effectiveReadingMode = userSettings.readingMode
            try {
                if (seriesId > 0) {
                    val seriesRes = seriesRepository.getSeriesDetail(seriesId)
                    if (seriesRes.isSuccess) {
                        val series = seriesRes.getOrNull()
                        val libraryRes = libraryRepository.getLibraries()
                        val library = libraryRes.getOrNull()?.find { it.id == series?.libraryId }
                        if (library != null) {
                            val isManga = library.type == 0 || library.name.lowercase().contains("manga")
                            effectiveReadingMode = if (isManga) ReadingMode.RTL else ReadingMode.LTR
                        }
                    }
                }
            } catch (_: Exception) {}

            val effectiveSettings = userSettings.copy(readingMode = effectiveReadingMode)

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                settings = effectiveSettings,
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId
            )

            val pageList = readerRepository.getPages(chapterId, totalPages)
            val savedPageNum = readerRepository.getProgress(chapterId)
            val initialIndex = if (savedPageNum in 1..pageList.size) {
                savedPageNum - 1
            } else 0

            _uiState.value = _uiState.value.copy(
                pages = pageList,
                currentPageIndex = initialIndex,
                isLoading = false
            )
        }
    }

    fun onPageChanged(index: Int) {
        if (index in 0 until _uiState.value.pages.size) {
            _uiState.value = _uiState.value.copy(currentPageIndex = index)
            viewModelScope.launch {
                readerRepository.saveProgress(
                    seriesId = _uiState.value.seriesId,
                    volumeId = _uiState.value.volumeId,
                    chapterId = _uiState.value.chapterId,
                    pageNum = index + 1
                )

                if (index >= _uiState.value.pages.size - 1) {
                    val settings = _uiState.value.settings
                    if (settings.autoDeleteOption == AutoDeleteOption.IMMEDIATELY) {
                        downloadRepository.deleteLocalChapter(_uiState.value.chapterId)
                    }
                }
            }
        }
    }

    fun nextPage() {
        val current = _uiState.value.currentPageIndex
        if (current < _uiState.value.pages.size - 1) {
            onPageChanged(current + 1)
        }
    }

    fun previousPage() {
        val current = _uiState.value.currentPageIndex
        if (current > 0) {
            onPageChanged(current - 1)
        }
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(controlsVisible = !_uiState.value.controlsVisible)
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
}
