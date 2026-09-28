package com.zemmy.sigurvita.ui.screens.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.repository.DownloadRepository
import com.zemmy.sigurvita.core.data.repository.ReadingListRepository
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ReadingListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun extractEraCategory(title: String): String {
    val cleanTitle = title.trim()

    val yearRangeRegex = Regex("""(\d{4}-\d{4})""")
    val yearMatch = yearRangeRegex.find(cleanTitle)?.groupValues?.get(1)

    if (yearMatch != null) {
        return when (yearMatch) {
            "1939-1956" -> "1939-1956 Golden Age"
            "1961-1970" -> "1961-1970 Silver Age"
            "1970-1984" -> "1970-1984 Bronze Age"
            "1984-1997" -> "1984-1997 Modern Age Begins"
            "1997-2004" -> "1997-2004 Heroes Relaunched"
            "2004-2012" -> "2004-2012 Marvel Disassembled"
            "2012-2015" -> "2012-2015 Marvel NOW!"
            "2015-2018" -> "2015-2018 ANAD Marvel"
            "2018-2021" -> "2018-2021 Fresh Start"
            "2021-2024" -> "2021-2024 Timeless"
            else -> yearMatch
        }
    }

    if (cleanTitle.contains("Alternate Universes", ignoreCase = true)) {
        return "Alternate Universes"
    }

    val bracketRegex = Regex("""^\[([^]]+)\]""")
    val bracketMatch = bracketRegex.find(cleanTitle)?.groupValues?.get(1)
    if (bracketMatch != null) {
        return bracketMatch
    }

    return "Other Lists"
}

data class ReadingListUiState(
    val readingLists: List<ReadingList> = emptyList(),
    val selectedListItems: List<ReadingListItem> = emptyList(),
    val selectedListTitle: String? = null,
    val selectedEra: String = "All",
    val searchQuery: String = "",
    val totalIssues: Int = 0,
    val issuesRead: Int = 0,
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val eras: List<String>
        get() {
            val extracted = readingLists.map { extractEraCategory(it.title) }.distinct().sorted()
            return listOf("All") + extracted
        }

    val filteredReadingLists: List<ReadingList>
        get() = readingLists.filter { list ->
            val matchesEra = selectedEra == "All" || extractEraCategory(list.title) == selectedEra
            val matchesQuery = searchQuery.isBlank() ||
                    list.title.contains(searchQuery, ignoreCase = true) ||
                    (list.summary?.contains(searchQuery, ignoreCase = true) == true)
            matchesEra && matchesQuery
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
    private val preferencesRepository = app.userPreferencesRepository
    private val repository = ReadingListRepository(
        NetworkClient.createKavitaApi(preferencesRepository),
        app.database,
        preferencesRepository
    )
    private val downloadRepository = DownloadRepository(app.database, app)

    private val _uiState = MutableStateFlow(ReadingListUiState())
    val uiState: StateFlow<ReadingListUiState> = _uiState.asStateFlow()

    init {
        loadReadingLists()
    }

    fun loadReadingLists() {
        _uiState.value = _uiState.value.copy(
            selectedListItems = emptyList(),
            selectedListTitle = null,
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

    fun loadListDetail(listId: Int, title: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
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

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectEra(era: String) {
        _uiState.value = _uiState.value.copy(selectedEra = era)
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
                    totalPages = item.pages
                )
            }
        }
    }
}
