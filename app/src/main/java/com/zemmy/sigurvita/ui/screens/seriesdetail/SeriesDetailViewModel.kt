package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.repository.DownloadRepository
import com.zemmy.sigurvita.core.data.repository.LibraryRepository
import com.zemmy.sigurvita.core.data.repository.SeriesRepository
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    val series: Series? = null,
    val volumes: List<Volume> = emptyList(),
    val downloadTasks: Map<Int, DownloadTask> = emptyMap(),
    val libraryName: String = "Comics",
    val totalChapters: Int = 0,
    val chaptersRead: Int = 0,
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SeriesDetailViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val api = NetworkClient.createKavitaApi(preferencesRepository)
    private val seriesRepository = SeriesRepository(api, preferencesRepository)
    private val libraryRepository = LibraryRepository(api, app.database, preferencesRepository)
    private val downloadRepository = DownloadRepository(app.database, app)

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadRepository.downloadsFlow.collect { tasks ->
                _uiState.value = _uiState.value.copy(
                    downloadTasks = tasks.associateBy { it.chapterId }
                )
            }
        }
    }

    fun loadSeriesDetail(seriesId: Int) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val seriesRes = seriesRepository.getSeriesDetail(seriesId)
            val volumesRes = seriesRepository.getSeriesVolumes(seriesId)

            if (seriesRes.isSuccess) {
                val fetchedSeries = seriesRes.getOrNull()
                val volumes = volumesRes.getOrDefault(emptyList())

                val libraryRes = libraryRepository.getLibraries()
                val library = libraryRes.getOrNull()?.find { it.id == fetchedSeries?.libraryId }
                val resolvedLibraryName = when {
                    library?.type == 0 || library?.name?.contains("manga", ignoreCase = true) == true -> "Manga"
                    library?.type == 1 || library?.name?.contains("comic", ignoreCase = true) == true -> "Comics"
                    library?.name?.isNotBlank() == true -> library.name
                    else -> "Comics"
                }

                val computedTotalChapters = volumes.sumOf { it.chapters.size }
                val computedChaptersRead = volumes.sumOf { v -> v.chapters.count { it.isRead } }

                val calculatedTotalPages = if ((fetchedSeries?.totalPages ?: 0) > 0) {
                    fetchedSeries!!.totalPages
                } else {
                    volumes.sumOf { v -> if (v.totalPages > 0) v.totalPages else v.chapters.sumOf { c -> c.pages } }
                }

                val calculatedPagesRead = if ((fetchedSeries?.pagesRead ?: 0) > 0) {
                    fetchedSeries!!.pagesRead
                } else {
                    volumes.sumOf { v -> if (v.pagesRead > 0) v.pagesRead else v.chapters.sumOf { c -> c.pagesRead } }
                }

                val updatedSeries = fetchedSeries?.copy(
                    totalPages = calculatedTotalPages,
                    pagesRead = calculatedPagesRead
                )

                _uiState.value = _uiState.value.copy(
                    series = updatedSeries,
                    volumes = volumes,
                    libraryName = resolvedLibraryName,
                    totalChapters = computedTotalChapters,
                    chaptersRead = computedChaptersRead,
                    totalPages = calculatedTotalPages,
                    pagesRead = calculatedPagesRead,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load series"
                )
            }
        }
    }

    fun downloadChapter(chapter: Chapter) {
        val series = _uiState.value.series ?: return
        viewModelScope.launch {
            downloadRepository.enqueueDownload(
                chapterId = chapter.id,
                seriesId = series.id,
                volumeId = chapter.volumeId,
                title = chapter.title.ifBlank { "Issue ${chapter.chapterNumber}" },
                coverUrl = chapter.coverUrl ?: series.coverUrl,
                totalPages = chapter.pages,
                libraryName = _uiState.value.libraryName,
                seriesName = series.name,
                chapterNumber = chapter.chapterNumber
            )
        }
    }

    fun downloadBulk(count: Int, unreadOnly: Boolean) {
        val series = _uiState.value.series ?: return
        val allChapters = _uiState.value.volumes.flatMap { it.chapters }
        val eligibleChapters = if (unreadOnly) {
            allChapters.filter { !it.isRead }
        } else {
            allChapters
        }

        val chaptersToDownload = eligibleChapters.take(count)

        viewModelScope.launch {
            chaptersToDownload.forEach { chapter ->
                downloadRepository.enqueueDownload(
                    chapterId = chapter.id,
                    seriesId = series.id,
                    volumeId = chapter.volumeId,
                    title = chapter.title.ifBlank { "Issue ${chapter.chapterNumber}" },
                    coverUrl = chapter.coverUrl ?: series.coverUrl,
                    totalPages = chapter.pages,
                    libraryName = _uiState.value.libraryName,
                    seriesName = series.name,
                    chapterNumber = chapter.chapterNumber
                )
            }
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
            _uiState.value.series?.id?.let { loadSeriesDetail(it) }
        }
    }

    fun toggleMarkRead() {
        val series = _uiState.value.series ?: return
        viewModelScope.launch {
            if (series.isCompleted) {
                seriesRepository.markSeriesUnread(series.id)
            } else {
                seriesRepository.markSeriesRead(series.id)
            }
            loadSeriesDetail(series.id)
        }
    }
}
