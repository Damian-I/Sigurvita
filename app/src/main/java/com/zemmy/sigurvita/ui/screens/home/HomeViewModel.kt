package com.zemmy.sigurvita.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.cache.AllSeriesSnapshot
import com.zemmy.sigurvita.core.data.cache.HomeSnapshot
import com.zemmy.sigurvita.core.data.cache.SnapshotNames
import com.zemmy.sigurvita.core.data.cache.toModel
import com.zemmy.sigurvita.core.data.cache.toSnap
import com.zemmy.sigurvita.core.datastore.SeriesReadContext
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.zemmy.sigurvita.core.util.matchesQuery

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

/** The full series list (up to 5000, plus issue-count lookups) is a heavy fetch used only by search. */
private const val ALL_SERIES_MAX_AGE_MS = 6L * 60L * 60L * 1000L

private fun filterSeries(list: List<Series>, query: String): List<Series> {
    if (query.isBlank()) return emptyList()
    return list.filter { it.matchesQuery(query) }
}

@OptIn(FlowPreview::class)
class HomeViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val libraryRepository = app.libraryRepository
    private val downloadRepository = app.downloadRepository
    private val snapshotStore = app.snapshotStore

    private val searchQueryFlow = MutableStateFlow("")

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var allSeriesJob: Job? = null

    // Set once any live (network) data has been applied, so a late snapshot never overwrites it
    private var liveHomeDataApplied = false

    init {
        viewModelScope.launch(Dispatchers.Default) {
            searchQueryFlow
                .debounce(150.milliseconds)
                .collect { query ->
                    val results = filterSeries(_uiState.value.allSeries, query)
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.update { it.copy(isIncognitoMode = settings.isIncognitoMode) }
            }
        }
        viewModelScope.launch {
            downloadRepository.downloadedSeriesFlow.collect { downloaded ->
                _uiState.update { it.copy(downloadedSeries = downloaded) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.seriesReadContextsFlow.collect { contexts ->
                _uiState.update { it.copy(seriesReadContexts = contexts) }
            }
        }
        restoreSnapshots()
        loadData()
    }

    /** Draws what was saved last time. Runs alongside [loadData]; it never overwrites live data. */
    private fun restoreSnapshots() {
        viewModelScope.launch {
            val accountKey = snapshotStore.accountKey(preferencesRepository.serverConfigFlow.first())

            val home = snapshotStore.read(SnapshotNames.HOME, HomeSnapshot.serializer())
            if (home != null && home.accountKey == accountKey && !liveHomeDataApplied) {
                _uiState.update {
                    it.copy(
                        libraries = home.libraries.map { l -> l.toModel() },
                        onDeckSeries = home.onDeck.map { s -> s.toModel() },
                        recentlyUpdatedSeries = home.recentlyUpdated.map { s -> s.toModel() },
                        newlyAddedSeries = home.newlyAdded.map { s -> s.toModel() },
                        isLoading = false
                    )
                }
            }

            val all = snapshotStore.read(SnapshotNames.ALL_SERIES, AllSeriesSnapshot.serializer())
            if (all != null && all.accountKey == accountKey && _uiState.value.allSeries.isEmpty()) {
                val list = all.series.map { it.toModel() }
                _uiState.update {
                    it.copy(allSeries = list, searchResults = filterSeries(list, it.searchQuery))
                }
            }
        }
    }

    /**
     * Refreshes the four Home sections. Each section is shown as soon as its own request finishes, so a slow
     * request no longer holds back the others. Sections that fail keep what is already on screen.
     */
    fun loadData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val state = _uiState.value
            val hasContent = state.onDeckSeries.isNotEmpty() ||
                state.recentlyUpdatedSeries.isNotEmpty() ||
                state.newlyAddedSeries.isNotEmpty() ||
                state.downloadedSeries.isNotEmpty()
            if (!hasContent) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            // All collectors below run on the main thread (viewModelScope), so this counter needs no lock
            var failedSections = 0

            coroutineScope {
                launch {
                    libraryRepository.getLibraries()
                        .onSuccess { list ->
                            liveHomeDataApplied = true
                            _uiState.update { it.copy(libraries = list) }
                        }
                        .onFailure { failedSections++ }
                }
                launch {
                    libraryRepository.getOnDeckSeries()
                        .onSuccess { list ->
                            liveHomeDataApplied = true
                            _uiState.update { it.copy(onDeckSeries = list) }
                        }
                        .onFailure { failedSections++ }
                }
                launch {
                    libraryRepository.getRecentlyUpdatedSeries()
                        .onSuccess { list ->
                            liveHomeDataApplied = true
                            _uiState.update { it.copy(recentlyUpdatedSeries = list) }
                        }
                        .onFailure { failedSections++ }
                }
                launch {
                    libraryRepository.getNewlyAddedSeries()
                        .onSuccess { list ->
                            liveHomeDataApplied = true
                            _uiState.update { it.copy(newlyAddedSeries = list) }
                        }
                        .onFailure { failedSections++ }
                }
            }

            val current = _uiState.value
            val hasAnyData = current.onDeckSeries.isNotEmpty() ||
                current.recentlyUpdatedSeries.isNotEmpty() ||
                current.newlyAddedSeries.isNotEmpty() ||
                current.downloadedSeries.isNotEmpty()

            if (hasAnyData) {
                _uiState.update {
                    it.copy(isOffline = failedSections > 0, isLoading = false, errorMessage = null)
                }
                if (failedSections == 0) persistHomeSnapshot()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOffline = true,
                        errorMessage = "Failed to load content from Kavita"
                    )
                }
            }

            // The heavy full-list fetch runs after the visible sections, in its own job
            refreshAllSeriesIfNeeded()
        }
    }

    private suspend fun persistHomeSnapshot() {
        val state = _uiState.value
        val accountKey = snapshotStore.accountKey(preferencesRepository.serverConfigFlow.first())
        snapshotStore.write(
            SnapshotNames.HOME,
            HomeSnapshot.serializer(),
            HomeSnapshot(
                accountKey = accountKey,
                savedAt = System.currentTimeMillis(),
                libraries = state.libraries.map { it.toSnap() },
                onDeck = state.onDeckSeries.map { it.toSnap() },
                recentlyUpdated = state.recentlyUpdatedSeries.map { it.toSnap() },
                newlyAdded = state.newlyAddedSeries.map { it.toSnap() }
            )
        )
    }

    /**
     * Loads every series (used only for search). This is expensive, so it is done at most every
     * [ALL_SERIES_MAX_AGE_MS], or when search is opened and there is nothing to search yet. The result
     * is saved locally and shown immediately on later launches.
     */
    private fun refreshAllSeriesIfNeeded() {
        if (allSeriesJob?.isActive == true) return
        allSeriesJob = viewModelScope.launch {
            val fresh = _uiState.value.allSeries.isNotEmpty() &&
                snapshotStore.ageMillis(SnapshotNames.ALL_SERIES) < ALL_SERIES_MAX_AGE_MS
            if (fresh) return@launch

            libraryRepository.getSeriesForLibrary(0).onSuccess { list ->
                _uiState.update {
                    it.copy(allSeries = list, searchResults = filterSeries(list, it.searchQuery))
                }
                val accountKey = snapshotStore.accountKey(preferencesRepository.serverConfigFlow.first())
                snapshotStore.write(
                    SnapshotNames.ALL_SERIES,
                    AllSeriesSnapshot.serializer(),
                    AllSeriesSnapshot(
                        accountKey = accountKey,
                        savedAt = System.currentTimeMillis(),
                        series = list.map { it.toSnap() }
                    )
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun toggleSearchActive() {
        val newSearchState = !_uiState.value.isSearchActive
        val activeQuery = if (!newSearchState) "" else _uiState.value.searchQuery
        _uiState.update {
            it.copy(
                isSearchActive = newSearchState,
                searchQuery = activeQuery
            )
        }
        searchQueryFlow.value = activeQuery
        if (newSearchState) refreshAllSeriesIfNeeded()
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
