package com.zemmy.sigurvita.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

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
