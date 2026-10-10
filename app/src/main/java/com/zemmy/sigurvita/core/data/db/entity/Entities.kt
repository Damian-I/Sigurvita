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

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val chapterId: Int,
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
    val localPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "marked_pages")
data class MarkedPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chapterId: Int,
    val seriesId: Int = 0,
    val volumeId: Int = 0,
    val pageIndex: Int,
    val totalPages: Int = 0,
    val seriesName: String = "",
    val chapterTitle: String = "",
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
