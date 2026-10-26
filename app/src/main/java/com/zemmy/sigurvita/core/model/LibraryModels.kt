package com.zemmy.sigurvita.core.model

data class Library(
    val id: Int,
    val name: String,
    val type: Int, // 0 = Manga, 1 = Comic, 2 = Book
    val coverUrl: String? = null
) {
    val isManga: Boolean get() = type == 0 || name.contains("manga", ignoreCase = true)
}

data class Series(
    val id: Int,
    val libraryId: Int,
    val name: String,
    val localizedName: String? = null,
    val coverUrl: String? = null,
    val summary: String? = null,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val totalIssues: Int = 0,
    val issuesRead: Int = 0,
    val isCompleted: Boolean = false,
    val userRating: Float = 0f
) {
    val isFullyRead: Boolean
        get() = (totalPages in 1..pagesRead) || (totalIssues in 1..issuesRead) || isCompleted
}

data class Volume(
    val id: Int,
    val seriesId: Int,
    val name: String,
    val number: Float,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val chapters: List<Chapter> = emptyList()
)

data class MangaFile(
    val id: Int = 0,
    val filePath: String? = null,
    val pages: Int = 0,
    val bytes: Long = 0L,
    val format: Int = 0,
    val extension: String? = null
)

data class Chapter(
    val id: Int,
    val volumeId: Int,
    val seriesId: Int,
    val chapterNumber: String,
    val title: String,
    val summary: String? = null,
    val pages: Int,
    val pagesRead: Int = 0,
    val isRead: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val coverUrl: String? = null,
    val files: List<MangaFile> = emptyList()
)

data class ReadingList(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val itemCount: Int = 0,
    val coverUrl: String? = null,
    val promoted: Boolean = false,
    val items: List<ReadingListItem> = emptyList(),
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val issuesRead: Int = 0
)

data class ReadingListItem(
    val id: Int,
    val readingListId: Int,
    val seriesId: Int,
    val seriesName: String,
    val libraryName: String? = null,
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val title: String,
    val coverUrl: String? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isRead: Boolean = false
)
