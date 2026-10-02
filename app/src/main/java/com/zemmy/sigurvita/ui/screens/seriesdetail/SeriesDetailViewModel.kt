package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import com.zemmy.sigurvita.core.util.naturalCompare
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SeriesViewFilter(val displayName: String) {
    BOTH("Both"),
    VOLUMES("Only Volumes"),
    CHAPTERS("Only Chapters")
}

private fun formatMangaChapterTitle(title: String, chapterNumber: String): String {
    val isSentinelNumber = chapterNumber.contains("100000") ||
            chapterNumber.toIntOrNull()?.let { abs(it) >= 10000 } == true

    val cleanNumber = if (!isSentinelNumber && chapterNumber.isNotBlank()) "Chapter $chapterNumber" else ""

    var cleanTitle = title.trim()
    if (chapterNumber.isNotBlank()) {
        cleanTitle = cleanTitle
            .removePrefix("Chapter #$chapterNumber")
            .removePrefix("Chapter $chapterNumber")
            .removePrefix("Issue #$chapterNumber")
            .removePrefix("Issue $chapterNumber")
            .removePrefix("#$chapterNumber")
            .removePrefix(chapterNumber)
            .trimStart(' ', '-', ':', '•')
            .trim()
    }

    if (cleanTitle.equals(chapterNumber.trim(), ignoreCase = true)) {
        cleanTitle = ""
    }

    return when {
        cleanNumber.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanNumber - $cleanTitle"
        cleanTitle.isNotBlank() -> cleanTitle
        cleanNumber.isNotBlank() -> cleanNumber
        else -> "Chapter"
    }
}

private fun formatComicChapterTitle(title: String, chapterNumber: String): String {
    val isSentinelNumber = chapterNumber.contains("100000") ||
            chapterNumber.toIntOrNull()?.let { abs(it) >= 10000 } == true

    val cleanNumber = if (!isSentinelNumber && chapterNumber.isNotBlank()) "#$chapterNumber" else ""

    var cleanTitle = title.trim()
    if (chapterNumber.isNotBlank()) {
        cleanTitle = cleanTitle
            .removePrefix("Issue #$chapterNumber")
            .removePrefix("Issue $chapterNumber")
            .removePrefix("#$chapterNumber")
            .removePrefix(chapterNumber)
            .trimStart(' ', '-', ':', '•')
            .trim()
    }

    if (cleanTitle.equals(chapterNumber.trim(), ignoreCase = true)) {
        cleanTitle = ""
    }

    return when {
        cleanNumber.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanNumber - $cleanTitle"
        cleanTitle.isNotBlank() -> cleanTitle
        cleanNumber.isNotBlank() -> cleanNumber
        else -> "Issue"
    }
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
        override val title: String = if (isManga) formatMangaChapterTitle(chapter.title, chapter.chapterNumber) else formatComicChapterTitle(chapter.title, chapter.chapterNumber),
        override val subtitle: String? = null
    ) : SeriesItem()
}

data class SeriesDetailUiState(
    val series: Series? = null,
    val volumes: List<Volume> = emptyList(),
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
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val displayMangaItems: List<SeriesItem>
        get() {
            if (!isMangaLibrary) return emptyList()

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

            if (searchQuery.isBlank()) return baseItems

            return baseItems.filter { item ->
                item.title.contains(searchQuery, ignoreCase = true) ||
                (item is SeriesItem.ChapterItem && item.chapter.chapterNumber.contains(searchQuery, ignoreCase = true))
            }
        }

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

    val filteredVolumes: List<Volume>
        get() {
            if (searchQuery.isBlank()) return volumes
            return volumes.mapNotNull { volume ->
                val matchingChapters = volume.chapters.filter { c ->
                    c.title.contains(searchQuery, ignoreCase = true) ||
                    c.chapterNumber.contains(searchQuery, ignoreCase = true)
                }
                if (matchingChapters.isNotEmpty()) {
                    volume.copy(chapters = matchingChapters)
                } else null
            }
        }
}

class SeriesDetailViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val seriesRepository = app.seriesRepository
    private val libraryRepository = app.libraryRepository
    private val readingListRepository = app.readingListRepository
    private val downloadRepository = app.downloadRepository

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
            val seriesDeferred = async { seriesRepository.getSeriesDetail(seriesId) }
            val volumesDeferred = async { seriesRepository.getSeriesVolumes(seriesId) }
            val librariesDeferred = async { libraryRepository.getLibraries() }

            val seriesRes = seriesDeferred.await()
            val volumesRes = volumesDeferred.await()
            val libraryRes = librariesDeferred.await()

            if (seriesRes.isSuccess) {
                val fetchedSeries = seriesRes.getOrNull()
                var volumes = volumesRes.getOrDefault(emptyList())

                val library = libraryRes.getOrNull()?.find { it.id == fetchedSeries?.libraryId }
                val resolvedLibraryId = library?.id ?: fetchedSeries?.libraryId ?: 0
                val isManga = library?.type == 0 || library?.name?.contains("manga", ignoreCase = true) == true
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

                _uiState.value = _uiState.value.copy(
                    series = updatedSeries,
                    volumes = volumes,
                    libraryName = resolvedLibraryName,
                    libraryId = resolvedLibraryId,
                    isMangaLibrary = isManga,
                    totalChapters = computedTotalChapters,
                    chaptersRead = computedChaptersRead,
                    totalPages = calculatedTotalPages,
                    pagesRead = calculatedPagesRead,
                    isLoading = false
                )

                launch {
                    try {
                        val readingListsMap = readingListRepository.getChapterReadingLists(seriesId)
                        _uiState.value = _uiState.value.copy(chapterReadingLists = readingListsMap)
                    } catch (_: Exception) {}
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load series"
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectViewFilter(filter: SeriesViewFilter) {
        _uiState.value = _uiState.value.copy(viewFilter = filter)
    }

    fun toggleSearchActive() {
        val newSearchState = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(
            isSearchActive = newSearchState,
            searchQuery = if (!newSearchState) "" else _uiState.value.searchQuery
        )
    }

    fun toggleGridView() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
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
        val eligibleChapters = if (unreadOnly) {
            allChapters.filter { !it.isRead }
        } else {
            allChapters
        }

        val chaptersToDownload = eligibleChapters.take(count)

        viewModelScope.launch {
            chaptersToDownload.forEach { chapter ->
                val itemLabel = if (_uiState.value.isMangaLibrary) "Chapter ${chapter.chapterNumber}" else "Issue ${chapter.chapterNumber}"
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
