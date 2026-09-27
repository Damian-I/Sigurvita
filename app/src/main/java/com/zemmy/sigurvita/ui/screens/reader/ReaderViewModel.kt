package com.zemmy.sigurvita.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.repository.ReaderRepository
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
    private val readerRepository = ReaderRepository(
        NetworkClient.createKavitaApi(preferencesRepository),
        app,
        preferencesRepository
    )

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun loadChapter(chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int) {
        viewModelScope.launch {
            val settings = preferencesRepository.readerSettingsFlow.first()
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                settings = settings,
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId
            )

            val pageList = readerRepository.getPages(chapterId, totalPages)
            _uiState.value = _uiState.value.copy(
                pages = pageList,
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
        val mode = _uiState.value.settings.readingMode

        val isNext = if (invert) isVolumeUp else !isVolumeUp

        // For RTL (Right to Left Manga), Volume Down = Next Page
        if (mode == ReadingMode.RTL) {
            if (isNext) nextPage() else previousPage()
        } else {
            if (isNext) nextPage() else previousPage()
        }
        return true
    }
}
