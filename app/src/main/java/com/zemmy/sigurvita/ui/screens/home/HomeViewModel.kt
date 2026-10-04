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
    val onDeckSeries: List<Series> = emptyList(),
    val recentlyUpdatedSeries: List<Series> = emptyList(),
    val newlyAddedSeries: List<Series> = emptyList(),
    val allSeries: List<Series> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isIncognitoMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val searchResults: List<Series>
        get() = if (searchQuery.isBlank()) emptyList() else allSeries.filter {
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
            val librariesDeferred = async { libraryRepository.getLibraries() }
            val onDeckDeferred = async { libraryRepository.getOnDeckSeries() }
            val recentlyUpdatedDeferred = async { libraryRepository.getRecentlyUpdatedSeries() }
            val newlyAddedDeferred = async { libraryRepository.getNewlyAddedSeries() }
            val allSeriesDeferred = async { libraryRepository.getSeriesForLibrary(0) }

            val librariesRes = librariesDeferred.await()
            val onDeckRes = onDeckDeferred.await()
            val recentlyUpdatedRes = recentlyUpdatedDeferred.await()
            val newlyAddedRes = newlyAddedDeferred.await()
            val allSeriesRes = allSeriesDeferred.await()

            if (librariesRes.isSuccess || onDeckRes.isSuccess || recentlyUpdatedRes.isSuccess || newlyAddedRes.isSuccess || allSeriesRes.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    libraries = librariesRes.getOrDefault(emptyList()),
                    onDeckSeries = onDeckRes.getOrDefault(emptyList()),
                    recentlyUpdatedSeries = recentlyUpdatedRes.getOrDefault(emptyList()),
                    newlyAddedSeries = newlyAddedRes.getOrDefault(emptyList()),
                    allSeries = allSeriesRes.getOrDefault(emptyList()),
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
