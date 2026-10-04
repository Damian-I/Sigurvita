package com.zemmy.sigurvita.ui.screens.markedpages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MarkedPagesUiState(
    val markedPages: List<MarkedPageEntity> = emptyList(),
    val searchQuery: String = "",
    val previewingPage: MarkedPageEntity? = null
) {
    val filteredPages: List<MarkedPageEntity>
        get() = if (searchQuery.isBlank()) markedPages else markedPages.filter {
            it.chapterTitle.contains(searchQuery, ignoreCase = true) ||
            it.seriesName.contains(searchQuery, ignoreCase = true)
        }
}

class MarkedPagesViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val repository = app.markedPageRepository

    private val _uiState = MutableStateFlow(MarkedPagesUiState())
    val uiState: StateFlow<MarkedPagesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.markedPagesFlow.collect { pages ->
                _uiState.value = _uiState.value.copy(markedPages = pages)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun deleteMarkedPage(id: Int) {
        viewModelScope.launch {
            repository.deleteMarkedPageById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun setPreviewPage(page: MarkedPageEntity?) {
        _uiState.value = _uiState.value.copy(previewingPage = page)
    }
}
