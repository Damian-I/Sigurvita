package com.zemmy.sigurvita.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.datastore.SeriesReadContext
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class HomeUiState(
    val libraries: List<Library> = emptyList(),
    val downloadedSeries: List<Series> = emptyList(),
    val onDeckSeries: List<Series> = emptyList(),
    val recentlyUpdatedSeries: List<Series> = emptyList(),
    val newlyAddedSeries: List<Series> = emptyList(),
    val allSeries: List<Series> = emptyList(),
    val searchResults: List<Series> = emptyList(),
    val seriesReadContexts: Map<Int, SeriesReadContext> = emptyMap(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isIncognitoMode: Boolean = false,
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
class HomeViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val libraryRepository = app.libraryRepository
    private val downloadRepository = app.downloadRepository

    private val searchQueryFlow = MutableStateFlow("")

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            searchQueryFlow
                .debounce(150.milliseconds)
                .collect { query ->
                    val seriesList = _uiState.value.allSeries
                    val results = if (query.isBlank()) {
                        emptyList()
                    } else {
                        seriesList.filter {
                            it.name.contains(query, ignoreCase = true) || (it.localizedName?.contains(query, ignoreCase = true) == true)
                        }
                    }
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    isIncognitoMode = settings.isIncognitoMode
                )
            }
        }
        viewModelScope.launch {
            downloadRepository.downloadedSeriesFlow.collect { downloaded ->
                _uiState.value = _uiState.value.copy(
                    downloadedSeries = downloaded
                )
            }
        }
        viewModelScope.launch {
            preferencesRepository.seriesReadContextsFlow.collect { contexts ->
                _uiState.value = _uiState.value.copy(
                    seriesReadContexts = contexts
                )
            }
        }
        loadData()
    }

    fun loadData() {
        val hasContent = _uiState.value.onDeckSeries.isNotEmpty() ||
                         _uiState.value.recentlyUpdatedSeries.isNotEmpty() ||
                         _uiState.value.newlyAddedSeries.isNotEmpty() ||
                         _uiState.value.downloadedSeries.isNotEmpty()
        if (!hasContent) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        }
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

            val isAnyNetworkSuccess = librariesRes.isSuccess && onDeckRes.isSuccess && recentlyUpdatedRes.isSuccess && newlyAddedRes.isSuccess && allSeriesRes.isSuccess
            val onDeck = onDeckRes.getOrDefault(emptyList())
            val recentlyUpdated = recentlyUpdatedRes.getOrDefault(emptyList())
            val newlyAdded = newlyAddedRes.getOrDefault(emptyList())
            val allSeries = allSeriesRes.getOrDefault(emptyList())
            val libraries = librariesRes.getOrDefault(emptyList())

            val hasAnyData = onDeck.isNotEmpty() || recentlyUpdated.isNotEmpty() || newlyAdded.isNotEmpty() || allSeries.isNotEmpty() || _uiState.value.downloadedSeries.isNotEmpty()

            if (hasAnyData) {
                val query = _uiState.value.searchQuery
                val results = if (query.isBlank()) emptyList() else allSeries.filter {
                    it.name.contains(query, ignoreCase = true) || (it.localizedName?.contains(query, ignoreCase = true) == true)
                }
                _uiState.value = _uiState.value.copy(
                    libraries = libraries,
                    onDeckSeries = onDeck,
                    recentlyUpdatedSeries = recentlyUpdated,
                    newlyAddedSeries = newlyAdded,
                    allSeries = allSeries,
                    searchResults = results,
                    isOffline = !isAnyNetworkSuccess,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isOffline = true,
                    errorMessage = "Failed to load content from Kavita"
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchQueryFlow.value = query
    }

    fun toggleSearchActive() {
        val newSearchState = !_uiState.value.isSearchActive
        val activeQuery = if (!newSearchState) "" else _uiState.value.searchQuery
        _uiState.value = _uiState.value.copy(
            isSearchActive = newSearchState,
            searchQuery = activeQuery
        )
        searchQueryFlow.value = activeQuery
    }

    fun toggleIncognitoMode() {
        val newMode = !_uiState.value.isIncognitoMode
        viewModelScope.launch {
            preferencesRepository.updateIncognitoMode(newMode)
        }
    }

    fun updateSeriesReadContext(seriesId: Int, originType: String, readingListId: Int = 0, readingListTitle: String = "") {
        viewModelScope.launch {
            preferencesRepository.saveSeriesReadContext(seriesId, originType, readingListId, readingListTitle)
        }
    }

    fun onOnDeckClick(
        series: Series,
        onNavigateToReader: (chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int, readingListId: Int) -> Unit,
        onNavigateToSeriesDetail: (seriesId: Int) -> Unit,
        onNavigateToReadingListDetail: (readingListId: Int) -> Unit
    ) {
        val context = _uiState.value.seriesReadContexts[series.id]
        if ((context?.originType == "READING_LIST") && (context.readingListId > 0)) {
            viewModelScope.launch {
                try {
                    val itemsRes = app.readingListRepository.getReadingListItems(context.readingListId)
                    val items = itemsRes.getOrDefault(emptyList())
                    val unreadItem = items.find { it.seriesId == series.id && !it.isRead && it.chapterId != null && it.chapterId > 0 }
                        ?: items.find { !it.isRead && it.chapterId != null && it.chapterId > 0 }
                    unreadItem?.chapterId?.let { chapterId ->
                        onNavigateToReader(chapterId, unreadItem.pages, series.id, unreadItem.volumeId ?: 0, context.readingListId)
                    } ?: run {
                        onNavigateToReadingListDetail(context.readingListId)
                    }
                } catch (_: Exception) {
                    onNavigateToReadingListDetail(context.readingListId)
                }
            }
        } else {
            onNavigateToSeriesDetail(series.id)
        }
    }

    fun getAvailableReadingListsForNextChapter(seriesId: Int, onResult: (List<ReadingList>) -> Unit) {
        viewModelScope.launch {
            try {
                val volumesRes = app.seriesRepository.getSeriesVolumes(seriesId)
                val chapters = volumesRes.getOrNull()?.flatMap { it.chapters } ?: emptyList()

                val nextChapter = chapters.find { it.pagesRead > 0 && !it.isRead }
                    ?: chapters.find { !it.isRead }
                    ?: chapters.firstOrNull()

                val targetChapterId = nextChapter?.id ?: 0

                val chapterListMap = app.readingListRepository.getChapterReadingLists(seriesId)
                val matchingListsInfo = if (targetChapterId > 0 && chapterListMap.containsKey(targetChapterId)) {
                    chapterListMap[targetChapterId] ?: emptyList()
                } else {
                    chapterListMap.values.flatten().distinctBy { it.first }
                }

                if (matchingListsInfo.isNotEmpty()) {
                    val matchingIds = matchingListsInfo.map { it.first }.toSet()
                    val allListsRes = app.readingListRepository.getReadingLists()
                    val allLists = allListsRes.getOrDefault(emptyList())
                    val filteredLists = allLists.filter { matchingIds.contains(it.id) }
                    onResult(filteredLists)
                } else {
                    onResult(emptyList())
                }
            } catch (_: Exception) {
                onResult(emptyList())
            }
        }
    }
}
