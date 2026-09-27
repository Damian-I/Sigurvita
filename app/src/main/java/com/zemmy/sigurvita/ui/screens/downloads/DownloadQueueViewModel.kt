package com.zemmy.sigurvita.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.repository.DownloadRepository
import com.zemmy.sigurvita.core.model.DownloadTask
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadQueueViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val repository = DownloadRepository(app.database, app)

    val downloads: StateFlow<List<DownloadTask>> = repository.downloadsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun cancelDownload(chapterId: Int) {
        viewModelScope.launch {
            repository.cancelDownload(chapterId)
        }
    }
}
