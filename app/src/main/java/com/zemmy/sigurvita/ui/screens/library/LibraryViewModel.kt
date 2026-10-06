package com.zemmy.sigurvita.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.Series
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val libraries: List<Library> = emptyList(),
    val selectedLibraryId: Int = 0,
    val libraryName: String = "All Libraries",
    val series: List<Series> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isIncognitoMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredSeries: List<Series>
        get() = if (searchQuery.isBlank()) series else series.filter {
            it.name.contains(searchQuery, ignoreCase = true) || (it.localizedName?.contains(searchQuery, ignoreCase = true) == true)
        }
}

class LibraryViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val libraryRepository = app.libraryRepository

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    isIncognitoMode = settings.isIncognitoMode
                )
            }
        }
    }

    fun loadLibrary(libraryId: Int) {
        val isInitial = _uiState.value.series.isEmpty()
        if (isInitial) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, selectedLibraryId = libraryId)
        } else {
            _uiState.value = _uiState.value.copy(selectedLibraryId = libraryId, errorMessage = null)
        }
        viewModelScope.launch {
            val librariesRes = async { libraryRepository.getLibraries() }
            val seriesRes = async { libraryRepository.getSeriesForLibrary(libraryId) }

            val libraries = librariesRes.await().getOrDefault(emptyList())
            val series = seriesRes.await().getOrDefault(emptyList())

            val libName = if (libraryId == 0) "All Libraries" else libraries.find { it.id == libraryId }?.name ?: "Library"

            _uiState.value = _uiState.value.copy(
                libraries = libraries,
                selectedLibraryId = libraryId,
                libraryName = libName,
                series = series,
                isLoading = false
            )
        }
    }

    fun selectLibrary(libraryId: Int) {
        loadLibrary(libraryId)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearchActive() {
        val newSearchState = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(
            isSearchActive = newSearchState,
            searchQuery = if (!newSearchState) "" else _uiState.value.searchQuery
        )
    }

    fun toggleIncognitoMode() {
        val newMode = !_uiState.value.isIncognitoMode
        viewModelScope.launch {
            preferencesRepository.updateIncognitoMode(newMode)
        }
    }
}
