package com.zemmy.sigurvita.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zemmy.sigurvita.core.model.DownloadStatus

@Entity(tableName = "cached_series")
data class CachedSeriesEntity(
    @PrimaryKey val id: Int,
    val libraryId: Int,
    val name: String,
    val localizedName: String?,
    val coverUrl: String?,
    val summary: String?,
    val pagesRead: Int,
    val totalPages: Int,
    val isCompleted: Boolean,
    val userRating: Float,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_chapters")
data class CachedChapterEntity(
    @PrimaryKey val id: Int,
    val volumeId: Int,
    val seriesId: Int,
    val chapterNumber: String,
    val title: String,
    val pages: Int,
    val pagesRead: Int,
    val isRead: Boolean,
    val isDownloaded: Boolean,
    val localPath: String?
)

@Entity(tableName = "cached_reading_lists")
data class CachedReadingListEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val summary: String?,
    val itemCount: Int,
    val coverUrl: String?,
    val promoted: Boolean,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val title: String,
    val coverUrl: String?,
    val totalPages: Int,
    val downloadedPages: Int,
    val status: DownloadStatus,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
