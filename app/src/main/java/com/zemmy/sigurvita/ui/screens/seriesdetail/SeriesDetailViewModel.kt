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
    val isIncognitoMode: Boolean = false,
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
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    isIncognitoMode = settings.isIncognitoMode
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
                var volumes = volumesRes.getOrDefault(emptyList())

                val libraryRes = libraryRepository.getLibraries()
                val library = libraryRes.getOrNull()?.find { it.id == fetchedSeries?.libraryId }
                val resolvedLibraryName = when {
                    library?.type == 0 || library?.name?.contains("manga", ignoreCase = true) == true -> "Manga"
                    library?.type == 1 || library?.name?.contains("comic", ignoreCase = true) == true -> "Comics"
                    library?.name?.isNotBlank() == true -> library.name
                    else -> "Comics"
                }

                val computedTotalChapters = volumes.sumOf { it.chapters.size }
                var computedChaptersRead = volumes.sumOf { v -> v.chapters.count { it.isRead } }

                val calculatedTotalPages = if ((fetchedSeries?.totalPages ?: 0) > 0) {
                    fetchedSeries!!.totalPages
                } else {
                    volumes.sumOf { v -> if (v.totalPages > 0) v.totalPages else v.chapters.sumOf { c -> c.pages } }
                }

                var calculatedPagesRead = if ((fetchedSeries?.pagesRead ?: 0) > 0) {
                    fetchedSeries!!.pagesRead
                } else {
                    volumes.sumOf { v -> if (v.pagesRead > 0) v.pagesRead else v.chapters.sumOf { c -> c.pagesRead } }
                }

                // Sync completed series status
                if (fetchedSeries?.isCompleted == true) {
                    computedChaptersRead = computedTotalChapters
                    calculatedPagesRead = calculatedTotalPages
                    volumes = volumes.map { v ->
                        v.copy(chapters = v.chapters.map { c -> c.copy(isRead = true) })
                    }
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

    fun toggleChapterRead(chapter: Chapter) {
        val targetSeriesId = if (chapter.seriesId > 0) chapter.seriesId else (_uiState.value.series?.id ?: 0)
        val newReadState = !chapter.isRead

        // Optimistic UI Update
        val updatedVolumes = _uiState.value.volumes.map { vol ->
            vol.copy(
                chapters = vol.chapters.map { c ->
                    if (c.id == chapter.id) c.copy(isRead = newReadState) else c
                }
            )
        }
        val newChaptersRead = updatedVolumes.sumOf { v -> v.chapters.count { it.isRead } }
        val newPagesRead = updatedVolumes.sumOf { v -> v.chapters.sumOf { if (it.isRead) it.pages else it.pagesRead } }

        _uiState.value = _uiState.value.copy(
            volumes = updatedVolumes,
            chaptersRead = newChaptersRead,
            pagesRead = newPagesRead
        )

        viewModelScope.launch {
            if (newReadState) {
                seriesRepository.markChapterRead(chapter.id, targetSeriesId)
            } else {
                seriesRepository.markChapterUnread(chapter.id, targetSeriesId)
            }
        }
    }

    fun toggleMarkRead() {
        val series = _uiState.value.series ?: return
        val isCurrentlyCompleted = series.isCompleted || (_uiState.value.chaptersRead >= _uiState.value.totalChapters && _uiState.value.totalChapters > 0)
        val targetReadState = !isCurrentlyCompleted

        // Optimistic UI Update
        val updatedVolumes = _uiState.value.volumes.map { vol ->
            vol.copy(
                chapters = vol.chapters.map { c -> c.copy(isRead = targetReadState) }
            )
        }
        val newChaptersRead = if (targetReadState) _uiState.value.totalChapters else 0
        val newPagesRead = if (targetReadState) _uiState.value.totalPages else 0

        _uiState.value = _uiState.value.copy(
            series = series.copy(isCompleted = targetReadState),
            volumes = updatedVolumes,
            chaptersRead = newChaptersRead,
            pagesRead = newPagesRead
        )

        viewModelScope.launch {
            if (targetReadState) {
                seriesRepository.markSeriesRead(series.id)
            } else {
                seriesRepository.markSeriesUnread(series.id)
            }
        }
    }

    fun toggleIncognitoMode() {
        val newMode = !_uiState.value.isIncognitoMode
        viewModelScope.launch {
            preferencesRepository.updateIncognitoMode(newMode)
        }
    }
}
