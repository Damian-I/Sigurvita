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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReadingListSortOption(val displayName: String) {
    TITLE_NATURAL_ASC("Title (A-Z)"),
    TITLE_NATURAL_DESC("Title (Z-A)"),
    ITEM_COUNT_DESC("Most Items"),
    ITEM_COUNT_ASC("Fewest Items")
}

data class ReadingListUiState(
    val readingLists: List<ReadingList> = emptyList(),
    val selectedListId: Int? = null,
    val selectedListItems: List<ReadingListItem> = emptyList(),
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

    val filteredReadingLists: List<ReadingList>
        get() {
            val filtered = readingLists.filter { list ->
                val eraInfo = extractEraInfo(list.title)
                val matchesEra = selectedEraName == "All" || eraInfo.name == selectedEraName
                val matchesQuery = searchQuery.isBlank() ||
                        list.title.contains(searchQuery, ignoreCase = true) ||
                        cleanReadingListTitle(list.title).contains(searchQuery, ignoreCase = true) ||
                        (list.summary?.contains(searchQuery, ignoreCase = true) == true)
                matchesEra && matchesQuery
            }

            return when (sortOption) {
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

    val filteredListItems: List<ReadingListItem>
        get() = selectedListItems.filter { item ->
            searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.seriesName.contains(searchQuery, ignoreCase = true)
        }

    val nextToReadIndex: Int
        get() = selectedListItems.indexOfFirst { !it.isRead }
}

class ReadingListViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val repository = app.readingListRepository
    private val seriesRepository = app.seriesRepository
    private val downloadRepository = app.downloadRepository
    private val preferencesRepository = app.userPreferencesRepository

    private val _uiState = MutableStateFlow(ReadingListUiState())
    val uiState: StateFlow<ReadingListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadRepository.downloadsFlow.collect { tasks ->
                _uiState.value = _uiState.value.copy(
                    downloadTasks = tasks.associateBy { it.chapterId }
                )
            }
        }
        viewModelScope.launch {
            preferencesRepository.chosenReadingListsFlow.collect { ids ->
                _uiState.value = _uiState.value.copy(chosenReadingListIds = ids.toSet())
            }
        }
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(isIncognitoMode = settings.isIncognitoMode)
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

    fun loadReadingLists() {
        _uiState.value = _uiState.value.copy(
            selectedListId = null,
            selectedListItems = emptyList(),
            selectedListTitle = null,
            selectedEraName = "All",
            searchQuery = "",
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            val result = repository.getReadingLists()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    readingLists = result.getOrNull() ?: emptyList(),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to load Reading Lists"
                )
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
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            selectedListId = listId,
            selectedListTitle = title,
            searchQuery = "",
            errorMessage = null
        )
        viewModelScope.launch {
            val result = repository.getReadingListItems(listId)
            if (result.isSuccess) {
                val items = result.getOrNull() ?: emptyList()
                val computedTotalIssues = items.size
                val computedIssuesRead = items.count { it.isRead }
                val computedTotalPages = items.sumOf { it.pages }
                val computedPagesRead = items.sumOf { it.pagesRead }

                _uiState.value = _uiState.value.copy(
                    selectedListItems = items,
                    totalIssues = computedTotalIssues,
                    issuesRead = computedIssuesRead,
                    totalPages = computedTotalPages,
                    pagesRead = computedPagesRead,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to load items for $title"
                )
            }
        }
    }

    fun selectReadingListById(readingListId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getReadingLists()
            if (result.isSuccess) {
                val lists = result.getOrNull() ?: emptyList()
                val targetList = lists.find { it.id == readingListId }
                if (targetList != null) {
                    loadListDetail(readingListId, targetList.title)
                } else {
                    _uiState.value = _uiState.value.copy(readingLists = lists, isLoading = false)
                }
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to load reading list")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectEra(eraName: String) {
        _uiState.value = _uiState.value.copy(selectedEraName = eraName)
    }

    fun selectSortOption(option: ReadingListSortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
    }

    fun toggleItemRead(item: ReadingListItem) {
        val chapterId = item.chapterId ?: return
        if (chapterId <= 0) return
        viewModelScope.launch {
            if (!item.isRead) {
                seriesRepository.markChapterRead(chapterId, item.seriesId)
            } else {
                seriesRepository.markChapterUnread(chapterId, item.seriesId)
            }
            val listId = _uiState.value.selectedListId
            val title = _uiState.value.selectedListTitle
            if (listId != null && title != null) {
                loadListDetail(listId, title)
            }
        }
    }

    fun downloadChapter(item: ReadingListItem) {
        val chapterId = item.chapterId ?: return
        viewModelScope.launch {
            downloadRepository.enqueueDownload(
                chapterId = chapterId,
                seriesId = item.seriesId,
                volumeId = item.volumeId ?: 0,
                title = "${item.seriesName} - ${item.title}",
                coverUrl = item.coverUrl,
                totalPages = item.pages,
                libraryName = "Reading Lists",
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
        val eligibleItems = if (unreadOnly) {
            allItems.filter { !it.isRead && it.chapterId != null && it.chapterId > 0 }
        } else {
            allItems.filter { it.chapterId != null && it.chapterId > 0 }
        }

        val itemsToDownload = eligibleItems.take(count)

        viewModelScope.launch {
            itemsToDownload.forEach { item ->
                downloadRepository.enqueueDownload(
                    chapterId = item.chapterId!!,
                    seriesId = item.seriesId,
                    volumeId = item.volumeId ?: 0,
                    title = "${item.seriesName} - ${item.title}",
                    coverUrl = item.coverUrl,
                    totalPages = item.pages,
                    libraryName = "Reading Lists",
                    seriesName = item.seriesName
                )
            }
        }
    }
}
