package com.zemmy.sigurvita.core.model

data class DownloadTask(
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryName: String = "Downloads",
    val seriesName: String = "",
    val chapterNumber: String = "",
    val title: String,
    val coverUrl: String?,
    val totalPages: Int,
    val downloadedPages: Int,
    val status: DownloadStatus,
    val errorMessage: String? = null,
    val localPath: String? = null
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}
