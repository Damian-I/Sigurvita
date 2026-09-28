package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.repository.DownloadRepository
import com.zemmy.sigurvita.core.data.repository.SeriesRepository
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    val series: Series? = null,
    val volumes: List<Volume> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SeriesDetailViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val seriesRepository = SeriesRepository(
        NetworkClient.createKavitaApi(preferencesRepository),
        app.database,
        preferencesRepository
    )
    private val downloadRepository = DownloadRepository(app.database, app)

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    fun loadSeriesDetail(seriesId: Int) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val seriesRes = seriesRepository.getSeriesDetail(seriesId)
            val volumesRes = seriesRepository.getSeriesVolumes(seriesId)

            if (seriesRes.isSuccess) {
                val fetchedSeries = seriesRes.getOrNull()
                val volumes = volumesRes.getOrDefault(emptyList())

                val calculatedTotalPages = if (fetchedSeries?.totalPages ?: 0 > 0) {
                    fetchedSeries!!.totalPages
                } else {
                    volumes.sumOf { v -> if (v.totalPages > 0) v.totalPages else v.chapters.sumOf { c -> c.pages } }
                }

                val calculatedPagesRead = if (fetchedSeries?.pagesRead ?: 0 > 0) {
                    fetchedSeries!!.pagesRead
                } else {
                    volumes.sumOf { v -> if (v.pagesRead > 0) v.pagesRead else v.chapters.sumOf { c -> c.pagesRead } }
                }

                val updatedSeries = fetchedSeries?.copy(
                    totalPages = calculatedTotalPages,
                    pagesRead = calculatedPagesRead
                )

                _uiState.value = SeriesDetailUiState(
                    series = updatedSeries,
                    volumes = volumes,
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
                title = "${series.name} - ${chapter.title}",
                coverUrl = series.coverUrl,
                totalPages = chapter.pages
            )
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
