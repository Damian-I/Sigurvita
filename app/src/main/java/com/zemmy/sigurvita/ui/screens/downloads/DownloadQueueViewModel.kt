package com.zemmy.sigurvita.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.DownloadTask
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadQueueViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val repository = app.downloadRepository

    init {
        validateDownloads()
    }

    fun validateDownloads() {
        viewModelScope.launch {
            repository.validateExternalDeletions()
        }
    }

    val downloads: StateFlow<List<DownloadTask>> = repository.downloadsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun pauseDownload(chapterId: Int) {
        viewModelScope.launch {
            repository.pauseDownload(chapterId)
        }
    }

    fun resumeDownload(chapterId: Int) {
        viewModelScope.launch {
            repository.resumeDownload(chapterId)
        }
    }

    fun cancelDownload(chapterId: Int) {
        viewModelScope.launch {
            repository.cancelDownload(chapterId)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompleted()
        }
    }

    fun pauseAll() {
        viewModelScope.launch {
            downloads.value.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }.forEach {
                repository.pauseDownload(it.chapterId)
            }
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            downloads.value.filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }.forEach {
                repository.resumeDownload(it.chapterId)
            }
        }
    }
}
