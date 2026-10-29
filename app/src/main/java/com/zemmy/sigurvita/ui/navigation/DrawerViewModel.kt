package com.zemmy.sigurvita.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.cache.DrawerSnapshot
import com.zemmy.sigurvita.core.data.cache.SnapshotNames
import com.zemmy.sigurvita.core.data.cache.toModel
import com.zemmy.sigurvita.core.data.cache.toSnap
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ServerConfig
import com.zemmy.sigurvita.core.util.ReadingListEraGroup
import com.zemmy.sigurvita.core.util.parseReadingListHierarchy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawerUiState(
    val serverUrl: String = "",
    /** Already in the user's saved order. */
    val libraries: List<Library> = emptyList(),
    val readingLists: List<ReadingList> = emptyList(),
    val chosenReadingListIds: Set<Int> = emptySet(),
    /** Era -> Part -> Subpart tree of the chosen and in-progress reading lists. */
    val hierarchy: List<ReadingListEraGroup> = emptyList()
)

/**
 * Owns everything the navigation drawer shows: libraries, reading lists, their saved order/selection and the
 * Era -> Part -> Subpart tree. Shows the last snapshot immediately, then refreshes from the server whenever the
 * logged-in account changes. Activity-scoped (obtained once in [SigurvitaNavGraph]).
 */
class DrawerViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val prefs = app.userPreferencesRepository
    private val libraryRepo = app.libraryRepository
    private val readingListRepo = app.readingListRepository

    private val serverUrl = MutableStateFlow("")
    private val libraries = MutableStateFlow<List<Library>>(emptyList())
    private val readingLists = MutableStateFlow<List<ReadingList>>(emptyList())
    private val inProgressIds = MutableStateFlow<Set<Int>>(emptySet())

    private data class Remote(
        val serverUrl: String,
        val libraries: List<Library>,
        val readingLists: List<ReadingList>,
        val inProgressIds: Set<Int>
    )

    private val remote = combine(serverUrl, libraries, readingLists, inProgressIds) { url, libs, lists, inProgress ->
        Remote(url, libs, lists, inProgress)
    }

    val uiState: StateFlow<DrawerUiState> = combine(
        remote,
        prefs.libraryOrderFlow,
        prefs.chosenReadingListsFlow
    ) { r, order, chosen ->
        val chosenIds = chosen.toSet()
        val activeLists = r.readingLists.filter { chosenIds.contains(it.id) || r.inProgressIds.contains(it.id) }
        DrawerUiState(
            serverUrl = r.serverUrl,
            libraries = applyLibraryOrder(r.libraries, order),
            readingLists = r.readingLists,
            chosenReadingListIds = chosenIds,
            hierarchy = parseReadingListHierarchy(activeLists)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

    init {
        viewModelScope.launch {
            prefs.serverConfigFlow
                .distinctUntilChangedBy { Triple(it.isLoggedIn, it.serverUrl, it.apiKey) }
                .collectLatest { config -> load(config) }
        }
    }

    fun saveLibraryOrder(ids: List<Int>) {
        viewModelScope.launch { prefs.updateLibraryOrder(ids) }
    }

    fun setReadingListChosen(listId: Int, chosen: Boolean) {
        viewModelScope.launch {
            if (chosen) prefs.addChosenReadingList(listId) else prefs.removeChosenReadingList(listId)
        }
    }

    private var refreshJob: Job? = null

    /**
     * Re-reads reading-list progress with the same call the Reading Lists screen uses, so READ / in-progress in the
     * sidebar follows what was just read. Libraries are not re-fetched.
     */
    fun refreshReadingLists() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!prefs.serverConfigFlow.first().isLoggedIn) return@launch
            val res = readingListRepo.getReadingLists()
            if (res.isSuccess) {
                val fetched = res.getOrDefault(emptyList())
                readingLists.value = fetched
                inProgressIds.value = fetched.filter { it.pagesRead > 0 }.map { it.id }.toSet()
            }
        }
    }

    private suspend fun load(config: ServerConfig) {
        serverUrl.value = config.serverUrl

        if (!config.isLoggedIn) {
            libraries.value = emptyList()
            readingLists.value = emptyList()
            inProgressIds.value = emptySet()
            return
        }

        val snapshots = app.snapshotStore
        val accountKey = snapshots.accountKey(config)

        // Show what was saved last time straight away
        val saved = snapshots.read(SnapshotNames.DRAWER, DrawerSnapshot.serializer())
        if (saved != null && saved.accountKey == accountKey) {
            if (libraries.value.isEmpty()) libraries.value = saved.libraries.map { it.toModel() }
            if (readingLists.value.isEmpty()) {
                readingLists.value = saved.readingLists.map { it.toModel() }
                inProgressIds.value = saved.readingLists
                    .filter { it.pagesRead > 0 }
                    .map { it.id }
                    .toSet()
            }
        }

        // Refresh in the background, both requests in parallel
        coroutineScope {
            val librariesDeferred = async { libraryRepo.getLibraries() }
            val readingListsDeferred = async { readingListRepo.getReadingLists() }

            val libRes = librariesDeferred.await()
            if (libRes.isSuccess) libraries.value = libRes.getOrDefault(emptyList())

            val rlRes = readingListsDeferred.await()
            if (rlRes.isSuccess) {
                val fetchedLists = rlRes.getOrDefault(emptyList())
                readingLists.value = fetchedLists
                // getReadingLists() already totals pagesRead per list: no extra request per list is needed
                inProgressIds.value = fetchedLists.filter { it.pagesRead > 0 }.map { it.id }.toSet()
            }

            if (libRes.isSuccess || rlRes.isSuccess) {
                snapshots.write(
                    SnapshotNames.DRAWER,
                    DrawerSnapshot.serializer(),
                    DrawerSnapshot(
                        accountKey = accountKey,
                        savedAt = System.currentTimeMillis(),
                        libraries = libraries.value.map { it.toSnap() },
                        readingLists = readingLists.value.map { it.toSnap() }
                    )
                )
            }
        }
    }
}

private fun applyLibraryOrder(libraries: List<Library>, order: List<Int>): List<Library> {
    if (order.isEmpty()) return libraries
    val byId = libraries.associateBy { it.id }
    val sorted = order.mapNotNull { byId[it] }
    val remaining = libraries.filter { !order.contains(it.id) }
    return sorted + remaining
}
