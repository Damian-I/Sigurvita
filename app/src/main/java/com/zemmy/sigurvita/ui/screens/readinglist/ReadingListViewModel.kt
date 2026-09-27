package com.zemmy.sigurvita.ui.screens.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
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

    private val _uiState = MutableStateFlow(ReadingListUiState())
    val uiState: StateFlow<ReadingListUiState> = _uiState.asStateFlow()

    init {
        loadReadingLists()
    }

    fun loadReadingLists() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
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
                    errorMessage = "Failed to load Reading Lists"
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
                _uiState.value = _uiState.value.copy(
                    selectedListItems = result.getOrNull() ?: emptyList(),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load items for $title"
                )
            }
        }
    }
}
