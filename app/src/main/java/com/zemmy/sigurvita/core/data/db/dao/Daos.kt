package com.zemmy.sigurvita.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Query("SELECT * FROM cached_series ORDER BY name ASC")
    fun getAllSeriesList(): List<CachedSeriesEntity>

    @Query("SELECT * FROM cached_series WHERE id = :seriesId")
    fun getSeriesById(seriesId: Int): CachedSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSeries(series: List<CachedSeriesEntity>)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks")
    fun getAllDownloadsList(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE chapterId = :chapterId")
    fun getDownloadByChapterId(chapterId: Int): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE seriesId = :seriesId AND status = 'COMPLETED'")
    fun getCompletedDownloadsForSeries(seriesId: Int): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED'")
    fun getAllCompletedDownloads(): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDownloadTask(task: DownloadTaskEntity)

    @Update
    fun updateDownloadTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET downloadedPages = :downloadedPages, status = :status WHERE chapterId = :chapterId")
    fun updateProgress(chapterId: Int, downloadedPages: Int, status: DownloadStatus): Int

    @Query("UPDATE download_tasks SET localPath = :localPath, downloadedPages = :downloadedPages, status = :status WHERE chapterId = :chapterId")
    fun updateDownloadResult(chapterId: Int, localPath: String?, downloadedPages: Int, status: DownloadStatus): Int

    @Query("UPDATE download_tasks SET status = :status WHERE chapterId = :chapterId")
    fun updateStatus(chapterId: Int, status: DownloadStatus): Int

    @Query("DELETE FROM download_tasks WHERE status = 'COMPLETED'")
    fun clearCompleted(): Int

    @Query("DELETE FROM download_tasks WHERE chapterId = :chapterId")
    fun deleteDownloadTask(chapterId: Int): Int
}

@Dao
interface MarkedPageDao {
    @Query("SELECT * FROM marked_pages ORDER BY createdAt DESC")
    fun getAllMarkedPages(): Flow<List<com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity>>

    @Query("SELECT pageIndex FROM marked_pages WHERE chapterId = :chapterId")
    fun getMarkedPageIndicesForChapter(chapterId: Int): Flow<List<Int>>

    @Query("SELECT * FROM marked_pages WHERE chapterId = :chapterId AND pageIndex = :pageIndex LIMIT 1")
    fun getMarkedPage(chapterId: Int, pageIndex: Int): com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMarkedPage(page: com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity)

    @Query("DELETE FROM marked_pages WHERE chapterId = :chapterId AND pageIndex = :pageIndex")
    fun deleteMarkedPage(chapterId: Int, pageIndex: Int): Int

    @Query("DELETE FROM marked_pages WHERE id = :id")
    fun deleteMarkedPageById(id: Int): Int

    @Query("UPDATE marked_pages SET reviewTag = :reviewTag WHERE id = :id")
    fun updateReviewTag(id: Int, reviewTag: String): Int

    @Query("DELETE FROM marked_pages")
    fun clearAllMarkedPages(): Int
}
