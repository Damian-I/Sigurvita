package com.zemmy.sigurvita.ui.screens.home

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

data class HomeUiState(
    val libraries: List<Library> = emptyList(),
    val selectedLibraryId: Int = 0,
    val selectedLibrarySeries: List<Series> = emptyList(),
    val onDeckSeries: List<Series> = emptyList(),
    val recentlyUpdatedSeries: List<Series> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isIncognitoMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredSelectedLibrarySeries: List<Series>
        get() = if (searchQuery.isBlank()) selectedLibrarySeries else selectedLibrarySeries.filter {
            it.name.contains(searchQuery, ignoreCase = true) || (it.localizedName?.contains(searchQuery, ignoreCase = true) == true)
        }

    val filteredOnDeckSeries: List<Series>
        get() = if (searchQuery.isBlank()) onDeckSeries else onDeckSeries.filter {
            it.name.contains(searchQuery, ignoreCase = true) || (it.localizedName?.contains(searchQuery, ignoreCase = true) == true)
        }

    val filteredRecentlyUpdatedSeries: List<Series>
        get() = if (searchQuery.isBlank()) recentlyUpdatedSeries else recentlyUpdatedSeries.filter {
            it.name.contains(searchQuery, ignoreCase = true) || (it.localizedName?.contains(searchQuery, ignoreCase = true) == true)
        }
}

class HomeViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val libraryRepository = app.libraryRepository

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
            // Run all independent API calls concurrently
            val librariesDeferred = async { libraryRepository.getLibraries() }
            val onDeckDeferred = async { libraryRepository.getOnDeckSeries() }
            val recentlyUpdatedDeferred = async { libraryRepository.getRecentlyUpdatedSeries() }
            val initialSeriesDeferred = async { libraryRepository.getSeriesForLibrary(0) }

            val librariesRes = librariesDeferred.await()
            val onDeckRes = onDeckDeferred.await()
            val recentlyUpdatedRes = recentlyUpdatedDeferred.await()
            val initialSeriesRes = initialSeriesDeferred.await()

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
