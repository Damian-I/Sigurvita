package com.zemmy.sigurvita.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zemmy.sigurvita.core.data.db.entity.CachedChapterEntity
import com.zemmy.sigurvita.core.data.db.entity.CachedReadingListEntity
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Query("SELECT * FROM cached_series ORDER BY name ASC")
    fun getAllSeries(): Flow<List<CachedSeriesEntity>>

    @Query("SELECT * FROM cached_series WHERE id = :seriesId")
    fun getSeriesById(seriesId: Int): CachedSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSeries(series: List<CachedSeriesEntity>)

    @Query("DELETE FROM cached_series")
    fun clearAll(): Int
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM cached_chapters WHERE seriesId = :seriesId ORDER BY id ASC")
    fun getChaptersForSeries(seriesId: Int): Flow<List<CachedChapterEntity>>

    @Query("SELECT * FROM cached_chapters WHERE id = :chapterId")
    fun getChapterById(chapterId: Int): CachedChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapters(chapters: List<CachedChapterEntity>)

    @Query("UPDATE cached_chapters SET isDownloaded = :isDownloaded, localPath = :localPath WHERE id = :chapterId")
    fun updateDownloadStatus(chapterId: Int, isDownloaded: Boolean, localPath: String?): Int
}

@Dao
interface ReadingListDao {
    @Query("SELECT * FROM cached_reading_lists ORDER BY title ASC")
    fun getAllReadingLists(): Flow<List<CachedReadingListEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingLists(lists: List<CachedReadingListEntity>)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE chapterId = :chapterId")
    fun getDownloadByChapterId(chapterId: Int): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDownloadTask(task: DownloadTaskEntity)

    @Update
    fun updateDownloadTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET downloadedPages = :downloadedPages, status = :status WHERE chapterId = :chapterId")
    fun updateProgress(chapterId: Int, downloadedPages: Int, status: DownloadStatus): Int

    @Query("DELETE FROM download_tasks WHERE chapterId = :chapterId")
    fun deleteDownloadTask(chapterId: Int): Int
}
