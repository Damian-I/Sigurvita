package com.zemmy.sigurvita.ui.screens.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ReadingListItem
import com.zemmy.sigurvita.core.util.EraCategory
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.core.util.extractEraInfo
import com.zemmy.sigurvita.core.util.naturalCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class ReadingListSortOption(val displayName: String) {
    TITLE_NATURAL_ASC("Title (A-Z)"),
    TITLE_NATURAL_DESC("Title (Z-A)"),
    ITEM_COUNT_DESC("Most Items"),
    ITEM_COUNT_ASC("Fewest Items")
}

data class ReadingListUiState(
    val readingLists: List<ReadingList> = emptyList(),
    val filteredReadingLists: List<ReadingList> = emptyList(),
    val selectedListId: Int? = null,
    val selectedListItems: List<ReadingListItem> = emptyList(),
    val filteredListItems: List<ReadingListItem> = emptyList(),
    val downloadTasks: Map<Int, DownloadTask> = emptyMap(),
    val selectedListTitle: String? = null,
    val selectedEraName: String = "All",
    val searchQuery: String = "",
    val sortOption: ReadingListSortOption = ReadingListSortOption.TITLE_NATURAL_ASC,
    val totalIssues: Int = 0,
    val issuesRead: Int = 0,
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val chosenReadingListIds: Set<Int> = emptySet(),
    val isIncognitoMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val eraCategories: List<EraCategory>
        get() {
            val extracted = readingLists.map { extractEraInfo(it.title) }.distinctBy { it.name }.sortedBy { it.name }
            return listOf(EraCategory(name = "All", years = null)) + extracted
        }

    val selectedEraCategory: EraCategory?
        get() = eraCategories.find { it.name == selectedEraName }

    val nextToReadIndex: Int
        get() = selectedListItems.indexOfFirst { !it.isRead }
}

@OptIn(FlowPreview::class)
class ReadingListViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val repository = app.readingListRepository
    private val seriesRepository = app.seriesRepository
    private val downloadRepository = app.downloadRepository
    private val preferencesRepository = app.userPreferencesRepository

    private val searchQueryFlow = MutableStateFlow("")

    private val _uiState = MutableStateFlow(ReadingListUiState())
    val uiState: StateFlow<ReadingListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            searchQueryFlow
                .debounce(150.milliseconds)
                .collect {
                    filterData()
                }
        }
        viewModelScope.launch {
            downloadRepository.downloadsFlow.collect { tasks ->
                _uiState.update {
                    it.copy(downloadTasks = tasks.associateBy { t -> t.chapterId })
                }
            }
        }
        viewModelScope.launch {
            preferencesRepository.chosenReadingListsFlow.collect { ids ->
                _uiState.update { it.copy(chosenReadingListIds = ids.toSet()) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.update { it.copy(isIncognitoMode = settings.isIncognitoMode) }
            }
        }
        loadReadingLists()
    }

    fun toggleIncognitoMode() {
        val newMode = !_uiState.value.isIncognitoMode
        viewModelScope.launch {
            preferencesRepository.updateIncognitoMode(newMode)
        }
    }

    private fun filterData() {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value
            val query = searchQueryFlow.value
            val lists = state.readingLists
            val items = state.selectedListItems
            val selectedEraName = state.selectedEraName
            val sortOption = state.sortOption

            val filteredLists = lists.filter { list ->
                val eraInfo = extractEraInfo(list.title)
                val matchesEra = selectedEraName == "All" || eraInfo.name == selectedEraName
                val matchesQuery = query.isBlank() ||
                        list.title.contains(query, ignoreCase = true) ||
                        cleanReadingListTitle(list.title).contains(query, ignoreCase = true) ||
                        (list.summary?.contains(query, ignoreCase = true) == true)
                matchesEra && matchesQuery
            }.let { filtered ->
                when (sortOption) {
                    ReadingListSortOption.TITLE_NATURAL_ASC ->
                        filtered.sortedWith { l1, l2 -> naturalCompare(cleanReadingListTitle(l1.title), cleanReadingListTitle(l2.title)) }
                    ReadingListSortOption.TITLE_NATURAL_DESC ->
                        filtered.sortedWith { l2, l1 -> naturalCompare(cleanReadingListTitle(l2.title), cleanReadingListTitle(l1.title)) }
                    ReadingListSortOption.ITEM_COUNT_DESC ->
                        filtered.sortedByDescending { it.itemCount }
                    ReadingListSortOption.ITEM_COUNT_ASC ->
                        filtered.sortedBy { it.itemCount }
                }
            }

            val filteredItems = items.filter { item ->
                query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.seriesName.contains(query, ignoreCase = true)
            }

            _uiState.update {
                it.copy(
                    filteredReadingLists = filteredLists,
                    filteredListItems = filteredItems
                )
            }
        }
    }

    fun loadReadingLists() {
        _uiState.update {
            it.copy(
                selectedListId = null,
                selectedListItems = emptyList(),
                selectedListTitle = null,
                selectedEraName = "All",
                searchQuery = "",
                isLoading = true,
                errorMessage = null
            )
        }
        searchQueryFlow.value = ""
        viewModelScope.launch {
            val result = repository.getReadingLists()
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        readingLists = result.getOrNull() ?: emptyList(),
                        isLoading = false
                    )
                }
                filterData()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load Reading Lists"
                    )
                }
            }
        }
    }

    fun togglePin(listId: Int) {
        viewModelScope.launch {
            val current = _uiState.value.chosenReadingListIds
            if (current.contains(listId)) {
                preferencesRepository.removeChosenReadingList(listId)
            } else {
                preferencesRepository.addChosenReadingList(listId)
            }
        }
    }

    fun loadListDetail(listId: Int, title: String) {
        val isInitialForList = _uiState.value.selectedListId != listId || _uiState.value.selectedListItems.isEmpty()
        if (isInitialForList) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedListId = listId,
                    selectedListTitle = title,
                    searchQuery = "",
                    errorMessage = null
                )
            }
            searchQueryFlow.value = ""
        } else {
            _uiState.update {
                it.copy(
                    selectedListId = listId,
                    selectedListTitle = title,
                    errorMessage = null
                )
            }
        }
        viewModelScope.launch {
            val result = repository.getReadingListItems(listId)
            if (result.isSuccess) {
                val items = result.getOrNull() ?: emptyList()
                val computedTotalIssues = items.size
                val computedIssuesRead = items.count { it.isRead }
                val computedTotalPages = items.sumOf { it.pages }
                val computedPagesRead = items.sumOf { it.pagesRead }

                _uiState.update {
                    it.copy(
                        selectedListItems = items,
                        totalIssues = computedTotalIssues,
                        issuesRead = computedIssuesRead,
                        totalPages = computedTotalPages,
                        pagesRead = computedPagesRead,
                        isLoading = false
                    )
                }
                filterData()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load items for $title"
                    )
                }
            }
        }
    }

    fun selectReadingListById(readingListId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.getReadingLists()
            if (result.isSuccess) {
                val lists = result.getOrNull() ?: emptyList()
                val targetList = lists.find { it.id == readingListId }
                if (targetList != null) {
                    loadListDetail(readingListId, targetList.title)
                } else {
                    _uiState.update { it.copy(readingLists = lists, isLoading = false) }
                    filterData()
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load reading list") }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun selectEra(eraName: String) {
        _uiState.update { it.copy(selectedEraName = eraName) }
        filterData()
    }

    fun selectSortOption(option: ReadingListSortOption) {
        _uiState.update { it.copy(sortOption = option) }
        filterData()
    }

    fun toggleItemRead(item: ReadingListItem) {
        val chapterId = item.chapterId ?: return
        if (chapterId <= 0) return

        val newReadState = !item.isRead
        val updatedItems = _uiState.value.selectedListItems.map {
            if (it.id == item.id) it.copy(isRead = newReadState, pagesRead = if (newReadState) it.pages else 0) else it
        }
        val newIssuesRead = updatedItems.count { it.isRead }
        val newPagesRead = updatedItems.sumOf { if (it.isRead) it.pages else it.pagesRead }

        _uiState.value = _uiState.value.copy(
            selectedListItems = updatedItems,
            issuesRead = newIssuesRead,
            pagesRead = newPagesRead
        )
        filterData()

        viewModelScope.launch {
            if (newReadState) {
                seriesRepository.markChapterRead(chapterId, item.seriesId)
            } else {
                seriesRepository.markChapterUnread(chapterId, item.seriesId)
            }
            val listId = _uiState.value.selectedListId
            if (listId != null) {
                val result = repository.getReadingListItems(listId)
                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        selectedListItems = items,
                        totalIssues = items.size,
                        issuesRead = items.count { it.isRead },
                        totalPages = items.sumOf { it.pages },
                        pagesRead = items.sumOf { it.pagesRead }
                    )
                    filterData()
                }
            }
        }
    }

    fun downloadChapter(item: ReadingListItem) {
        val chapterId = item.chapterId ?: return
        val targetLibrary = item.libraryName?.takeIf { it.isNotBlank() } ?: "Comics"
        viewModelScope.launch {
            downloadRepository.enqueueDownload(
                chapterId = chapterId,
                seriesId = item.seriesId,
                volumeId = item.volumeId ?: 0,
                title = "${item.seriesName} - ${item.title}",
                coverUrl = item.coverUrl,
                totalPages = item.pages,
                libraryName = targetLibrary,
                seriesName = item.seriesName
            )
        }
    }

    fun cancelDownload(chapterId: Int) {
        viewModelScope.launch {
            downloadRepository.cancelDownload(chapterId)
        }
    }

    fun deleteLocalChapter(chapterId: Int) {
        viewModelScope.launch {
            downloadRepository.deleteLocalChapter(chapterId)
        }
    }

    fun downloadBulk(count: Int, unreadOnly: Boolean) {
        val allItems = _uiState.value.selectedListItems
        val validItems = allItems.filter { it.chapterId != null && it.chapterId > 0 }
        val bulkItems = validItems.map { item ->
            val targetLibrary = item.libraryName?.takeIf { it.isNotBlank() } ?: "Comics"
            com.zemmy.sigurvita.core.data.repository.BulkDownloadItem(
                chapterId = item.chapterId!!,
                seriesId = item.seriesId,
                volumeId = item.volumeId ?: 0,
                title = "${item.seriesName} - ${item.title}",
                coverUrl = item.coverUrl,
                totalPages = item.pages,
                libraryName = targetLibrary,
                seriesName = item.seriesName,
                isRead = item.isRead
            )
        }

        viewModelScope.launch {
            downloadRepository.enqueueBulkDownloads(bulkItems, count, unreadOnly)
        }
    }
}
