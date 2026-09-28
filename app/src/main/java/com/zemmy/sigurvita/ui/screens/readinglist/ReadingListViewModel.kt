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

data class ReadingListUiState(
    val readingLists: List<ReadingList> = emptyList(),
    val selectedListItems: List<ReadingListItem> = emptyList(),
    val selectedListTitle: String? = null,
    val totalIssues: Int = 0,
    val issuesRead: Int = 0,
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

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
