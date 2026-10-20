package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import com.zemmy.sigurvita.core.util.naturalCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle

enum class SeriesViewFilter(val displayName: String) {
    BOTH("Both"),
    VOLUMES("Only Volumes"),
    CHAPTERS("Only Chapters")
}

private fun isVolumeFile(v: Volume): Boolean {
    val nameLower = v.name.trim().lowercase()
    if (nameLower.startsWith("c") || nameLower.startsWith("ch") || nameLower.contains("chapter") || Regex("""\bc\d+""").containsMatchIn(nameLower)) {
        return false // It's a chapter file (like c123)
    }
    if (nameLower.startsWith("v") || nameLower.startsWith("vol") || nameLower.contains("volume") || Regex("""\bv\d+""").containsMatchIn(nameLower)) {
        return true // It's a volume file (like V12)
    }
    return v.number > 0f && !v.name.contains("100000") && abs(v.number) < 10000f
}

sealed class SeriesItem {
    abstract val id: Int
    abstract val coverUrl: String?
    abstract val isRead: Boolean
    abstract val isDownloaded: Boolean
    abstract val pages: Int
    abstract val pagesRead: Int
    abstract val title: String
    abstract val subtitle: String?

    data class VolumeItem(
        val volume: Volume,
        override val id: Int = volume.id,
        override val coverUrl: String? = volume.chapters.firstOrNull()?.coverUrl,
        override val isRead: Boolean = volume.chapters.isNotEmpty() && volume.chapters.all { it.isRead },
        override val isDownloaded: Boolean = volume.chapters.isNotEmpty() && volume.chapters.all { it.isDownloaded },
        override val pages: Int = if (volume.totalPages > 0) volume.totalPages else volume.chapters.sumOf { it.pages },
        override val pagesRead: Int = if (volume.pagesRead > 0) volume.pagesRead else volume.chapters.sumOf { it.pagesRead },
        override val title: String = run {
            val name = volume.name.trim()
            when {
                name.contains("Volume", ignoreCase = true) -> name
                name.isNotBlank() -> "Volume $name"
                else -> "Volume ${volume.number}"
            }
        },
        override val subtitle: String? = null,
        val firstChapterId: Int = volume.chapters.firstOrNull()?.id ?: 0,
        val volumeId: Int = volume.id
    ) : SeriesItem()

    data class ChapterItem(
        val chapter: Chapter,
        val volumeId: Int,
        val isManga: Boolean = true,
        override val id: Int = chapter.id,
        override val coverUrl: String? = chapter.coverUrl,
        override val isRead: Boolean = chapter.isRead,
        override val isDownloaded: Boolean = chapter.isDownloaded,
        override val pages: Int = chapter.pages,
        override val pagesRead: Int = chapter.pagesRead,
        override val title: String = formatChapterDisplayTitle(chapter.title, chapter.chapterNumber, isManga = isManga),
        override val subtitle: String? = null
    ) : SeriesItem()
}

data class SeriesDetailUiState(
    val series: Series? = null,
    val volumes: List<Volume> = emptyList(),
    val filteredVolumes: List<Volume> = emptyList(),
    val displayMangaItems: List<SeriesItem> = emptyList(),
    val downloadTasks: Map<Int, DownloadTask> = emptyMap(),
    val libraryName: String = "Comics",
    val libraryId: Int = 0,
    val isMangaLibrary: Boolean = false,
    val chapterReadingLists: Map<Int, List<Pair<Int, String>>> = emptyMap(),
    val totalChapters: Int = 0,
    val chaptersRead: Int = 0,
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val searchQuery: String = "",
    val viewFilter: SeriesViewFilter = SeriesViewFilter.BOTH,
    val isSearchActive: Boolean = false,
    val isGridView: Boolean = false,
    val isIncognitoMode: Boolean = false,
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val metadata: com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto? = null,
    val errorMessage: String? = null
) {
    val nextToReadId: Int?
        get() {
            if (isMangaLibrary) {
                for (item in displayMangaItems) {
                    when (item) {
                        is SeriesItem.VolumeItem -> {
                            val firstUnread = item.volume.chapters.firstOrNull { !it.isRead }
                            if (firstUnread != null) return firstUnread.id
                        }
                        is SeriesItem.ChapterItem -> {
                            if (!item.chapter.isRead) return item.chapter.id
                        }
                    }
                }
            }
            for (v in volumes) {
                val unread = v.chapters.firstOrNull { !it.isRead }
                if (unread != null) return unread.id
            }
            return volumes.firstOrNull()?.chapters?.firstOrNull()?.id
        }
}

@OptIn(FlowPreview::class)
class SeriesDetailViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val seriesRepository = app.seriesRepository
    private val libraryRepository = app.libraryRepository
    private val readingListRepository = app.readingListRepository
    private val downloadRepository = app.downloadRepository

    private val searchQueryFlow = MutableStateFlow("")

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            searchQueryFlow
                .debounce(150.milliseconds)
                .collect {
                    filterData()
                }
        }
        viewModelScope.launch {
            downloadRepository.downloadsFlow.collect { tasks ->
                _uiState.update {
                    it.copy(downloadTasks = tasks.associateBy { t -> t.chapterId })
                }
            }
        }
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(isIncognitoMode = settings.isIncognitoMode)
                }
            }
        }
    }

    private fun filterData() {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value
            val query = searchQueryFlow.value
            val volumes = state.volumes
            val isMangaLibrary = state.isMangaLibrary
            val viewFilter = state.viewFilter

            val computedDisplayMangaItems = if (isMangaLibrary) {
                val volumeItems = mutableListOf<SeriesItem.VolumeItem>()
                val chapterItems = mutableListOf<SeriesItem.ChapterItem>()

                for (v in volumes) {
                    if (isVolumeFile(v)) {
                        volumeItems.add(SeriesItem.VolumeItem(v))
                    } else {
                        for (c in v.chapters) {
                            chapterItems.add(SeriesItem.ChapterItem(c, v.id, isManga = true))
                        }
                        if (v.chapters.isEmpty()) {
                            val dummyChapter = Chapter(
                                id = v.id,
                                volumeId = v.id,
                                seriesId = 0,
                                chapterNumber = v.name.replace(Regex("""[^0-9.]"""), ""),
                                title = v.name,
                                pages = v.totalPages,
                                pagesRead = v.pagesRead
                            )
                            chapterItems.add(SeriesItem.ChapterItem(dummyChapter, v.id, isManga = true))
                        }
                    }
                }

                val sortedVolumes = volumeItems.sortedBy { it.volume.number }
                val sortedChapters = chapterItems.sortedWith { c1, c2 -> naturalCompare(c1.chapter.chapterNumber, c2.chapter.chapterNumber) }

                val baseItems = when (viewFilter) {
                    SeriesViewFilter.BOTH -> sortedVolumes + sortedChapters
                    SeriesViewFilter.VOLUMES -> sortedVolumes
                    SeriesViewFilter.CHAPTERS -> sortedChapters
                }

                if (query.isBlank()) {
                    baseItems
                } else {
                    baseItems.filter { item ->
                        item.title.contains(query, ignoreCase = true) ||
                        (item is SeriesItem.ChapterItem && item.chapter.chapterNumber.contains(query, ignoreCase = true))
                    }
                }
            } else {
                emptyList()
            }

            val computedFilteredVolumes = if (query.isBlank()) {
                volumes
            } else {
                volumes.mapNotNull { volume ->
                    val matchingChapters = volume.chapters.filter { c ->
                        c.title.contains(query, ignoreCase = true) ||
                        c.chapterNumber.contains(query, ignoreCase = true)
                    }
                    if (matchingChapters.isNotEmpty()) {
                        volume.copy(chapters = matchingChapters)
                    } else null
                }
            }

            _uiState.update {
                it.copy(
                    displayMangaItems = computedDisplayMangaItems,
                    filteredVolumes = computedFilteredVolumes
                )
            }
        }
    }

    fun loadSeriesDetail(seriesId: Int) {
        val isInitialLoad = _uiState.value.series == null
        if (isInitialLoad) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }
        viewModelScope.launch {
            val seriesDeferred = async { seriesRepository.getSeriesDetail(seriesId) }
            val volumesDeferred = async { seriesRepository.getSeriesVolumes(seriesId) }
            val librariesDeferred = async { libraryRepository.getLibraries() }
            val metadataDeferred = async { seriesRepository.getSeriesMetadataDetails(seriesId) }

            val seriesRes = seriesDeferred.await()
            val volumesRes = volumesDeferred.await()
            val libraryRes = librariesDeferred.await()
            val metadataRes = metadataDeferred.await()

            if (seriesRes.isSuccess) {
                val fetchedSeries = seriesRes.getOrNull()
                var volumes = volumesRes.getOrDefault(emptyList())
                val metadata = metadataRes.getOrNull()

                val library = libraryRes.getOrNull()?.find { it.id == fetchedSeries?.libraryId }
                val resolvedLibraryId = library?.id ?: fetchedSeries?.libraryId ?: 0
                val isManga = library?.isManga == true
                val resolvedLibraryName = when {
                    isManga -> "Manga"
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

                val isOfflineMode = libraryRes.isFailure || metadataRes.isFailure

                _uiState.update {
                    it.copy(
                        series = updatedSeries,
                        volumes = volumes,
                        metadata = metadata,
                        libraryName = resolvedLibraryName,
                        libraryId = resolvedLibraryId,
                        isMangaLibrary = isManga,
                        totalChapters = computedTotalChapters,
                        chaptersRead = computedChaptersRead,
                        totalPages = calculatedTotalPages,
                        pagesRead = calculatedPagesRead,
                        isOffline = isOfflineMode,
                        isLoading = false
                    )
                }
                filterData()

                launch {
                    try {
                        val readingListsMap = readingListRepository.getChapterReadingLists(seriesId)
                        _uiState.update { it.copy(chapterReadingLists = readingListsMap) }
                    } catch (_: Exception) {}
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load series"
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun selectViewFilter(filter: SeriesViewFilter) {
        _uiState.update { it.copy(viewFilter = filter) }
        filterData()
    }

    fun toggleSearchActive() {
        val newSearchState = !_uiState.value.isSearchActive
        val activeQuery = if (!newSearchState) "" else _uiState.value.searchQuery
        _uiState.update {
            it.copy(
                isSearchActive = newSearchState,
                searchQuery = activeQuery
            )
        }
        searchQueryFlow.value = activeQuery
    }

    fun toggleGridView() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun downloadChapter(chapter: Chapter) {
        val series = _uiState.value.series ?: return
        val itemLabel = if (_uiState.value.isMangaLibrary) "Chapter ${chapter.chapterNumber}" else "Issue ${chapter.chapterNumber}"
        viewModelScope.launch {
            downloadRepository.enqueueDownload(
                chapterId = chapter.id,
                seriesId = series.id,
                volumeId = chapter.volumeId,
                title = chapter.title.ifBlank { itemLabel },
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
        val allChapters = _uiState.value.volumes.flatMap { v -> v.chapters }
        val bulkItems = allChapters.map { chapter ->
            val itemLabel = if (_uiState.value.isMangaLibrary) "Chapter ${chapter.chapterNumber}" else "Issue ${chapter.chapterNumber}"
            com.zemmy.sigurvita.core.data.repository.BulkDownloadItem(
                chapterId = chapter.id,
                seriesId = series.id,
                volumeId = chapter.volumeId,
                title = chapter.title.ifBlank { itemLabel },
                coverUrl = chapter.coverUrl ?: series.coverUrl,
                totalPages = chapter.pages,
                libraryName = _uiState.value.libraryName,
                seriesName = series.name,
                chapterNumber = chapter.chapterNumber,
                isRead = chapter.isRead
            )
        }

        viewModelScope.launch {
            downloadRepository.enqueueBulkDownloads(bulkItems, count, unreadOnly)
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
                    if (c.id == chapter.id) c.copy(isRead = newReadState, pagesRead = if (newReadState) c.pages else 0) else c
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
        filterData()

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
                chapters = vol.chapters.map { c -> c.copy(isRead = targetReadState, pagesRead = if (targetReadState) c.pages else 0) }
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
        filterData()

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
