package com.zemmy.sigurvita.core.model

enum class ReadingMode {
    RTL,       // Right to Left (Manga)
    LTR,       // Left to Right (Comic)
    WEBTOON    // Vertical Continuous Scroll
}

enum class FitMode {
    FIT_WIDTH,
    FIT_HEIGHT,
    FIT_SCREEN
}

data class ServerConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val jwtToken: String = "",
    val userId: Int = 0,
    val isLoggedIn: Boolean = false
)

data class ReaderSettings(
    val readingMode: ReadingMode = ReadingMode.RTL,
    val fitMode: FitMode = FitMode.FIT_WIDTH,
    val volumeKeyNavigationEnabled: Boolean = true,
    val volumeKeyInvert: Boolean = false,
    val keepScreenOn: Boolean = true,
    val backgroundColorHex: String = "#000000"
)

data class Library(
    val id: Int,
    val name: String,
    val type: Int, // 0 = Manga, 1 = Comic, 2 = Book
    val coverUrl: String? = null
)

data class Series(
    val id: Int,
    val libraryId: Int,
    val name: String,
    val localizedName: String? = null,
    val coverUrl: String? = null,
    val summary: String? = null,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val isCompleted: Boolean = false,
    val userRating: Float = 0f
)

data class Volume(
    val id: Int,
    val seriesId: Int,
    val name: String,
    val number: Float,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val chapters: List<Chapter> = emptyList()
)

data class Chapter(
    val id: Int,
    val volumeId: Int,
    val seriesId: Int,
    val chapterNumber: String,
    val title: String,
    val pages: Int,
    val pagesRead: Int = 0,
    val isRead: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
)

data class ReadingList(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val itemCount: Int = 0,
    val coverUrl: String? = null,
    val promoted: Boolean = false,
    val items: List<ReadingListItem> = emptyList()
)

data class ReadingListItem(
    val id: Int,
    val readingListId: Int,
    val seriesId: Int,
    val seriesName: String,
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val title: String,
    val coverUrl: String? = null
)

data class ReaderPage(
    val index: Int,
    val pageNumber: Int,
    val imageUrl: String,
    val isLocal: Boolean = false
)

data class DownloadTask(
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val title: String,
    val coverUrl: String?,
    val totalPages: Int,
    val downloadedPages: Int,
    val status: DownloadStatus,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}
