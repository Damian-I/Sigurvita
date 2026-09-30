package com.zemmy.sigurvita.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.repository.LibraryRepository
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.Series
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val libraries: List<Library> = emptyList(),
    val selectedLibraryId: Int = 0,
    val selectedLibrarySeries: List<Series> = emptyList(),
    val onDeckSeries: List<Series> = emptyList(),
    val recentlyUpdatedSeries: List<Series> = emptyList(),
    val isIncognitoMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val libraryRepository = LibraryRepository(
        NetworkClient.createKavitaApi(preferencesRepository),
        app.database,
        preferencesRepository
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    isIncognitoMode = settings.isIncognitoMode
                )
            }
        }
        loadData()
    }

    fun loadData() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val librariesRes = libraryRepository.getLibraries()
            val onDeckRes = libraryRepository.getOnDeckSeries()
            val recentlyUpdatedRes = libraryRepository.getRecentlyUpdatedSeries()
            val initialSeriesRes = libraryRepository.getSeriesForLibrary(0)

            if (librariesRes.isSuccess || onDeckRes.isSuccess || initialSeriesRes.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    libraries = librariesRes.getOrDefault(emptyList()),
                    selectedLibraryId = 0,
                    selectedLibrarySeries = initialSeriesRes.getOrDefault(emptyList()),
                    onDeckSeries = onDeckRes.getOrDefault(emptyList()),
                    recentlyUpdatedSeries = recentlyUpdatedRes.getOrDefault(emptyList()),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load content from Kavita"
                )
            }
        }
    }

    fun selectLibrary(libraryId: Int) {
        if (_uiState.value.selectedLibraryId == libraryId && _uiState.value.selectedLibrarySeries.isNotEmpty()) return

        _uiState.value = _uiState.value.copy(selectedLibraryId = libraryId, isLoading = true)
        viewModelScope.launch {
            val seriesRes = libraryRepository.getSeriesForLibrary(libraryId)
            _uiState.value = _uiState.value.copy(
                selectedLibrarySeries = seriesRes.getOrDefault(emptyList()),
                isLoading = false
            )
        }
    }

    fun toggleIncognitoMode() {
        val newMode = !_uiState.value.isIncognitoMode
        viewModelScope.launch {
            preferencesRepository.updateIncognitoMode(newMode)
        }
    }
}
