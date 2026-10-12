package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.download.DownloadWorker
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class BulkDownloadItem(
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val title: String,
    val coverUrl: String?,
    val totalPages: Int,
    val libraryName: String,
    val seriesName: String,
    val chapterNumber: String = "",
    val isRead: Boolean
)

class DownloadRepository(
    private val database: SigurvitaDatabase,
    private val context: Context
) {
    val downloadedSeriesFlow: Flow<List<Series>> = database.downloadDao().getAllDownloads().map { entities ->
        val cachedMap = database.seriesDao().getAllSeriesList().associateBy { it.id }
        entities.filter { it.status == DownloadStatus.COMPLETED && it.seriesId > 0 }
            .groupBy { it.seriesId }
            .map { (seriesId, tasks) ->
                val firstTask = tasks.first()
                val totalPages = tasks.sumOf { it.totalPages }
                val cachedSeries = cachedMap[seriesId]
                val pagesRead = cachedSeries?.pagesRead ?: 0
                Series(
                    id = seriesId,
                    libraryId = 0,
                    name = firstTask.seriesName.ifBlank { firstTask.title },
                    coverUrl = firstTask.coverUrl,
                    totalPages = totalPages,
                    pagesRead = pagesRead
                )
            }
    }.flowOn(Dispatchers.IO)

    val downloadsFlow: Flow<List<DownloadTask>> = database.downloadDao().getAllDownloads().map { entities ->
        entities.map { entity ->
            DownloadTask(
                chapterId = entity.chapterId,
                seriesId = entity.seriesId,
                volumeId = entity.volumeId,
                libraryName = entity.libraryName,
                seriesName = entity.seriesName,
                chapterNumber = entity.chapterNumber,
                title = entity.title,
                coverUrl = entity.coverUrl,
                totalPages = entity.totalPages,
                downloadedPages = entity.downloadedPages,
                status = entity.status,
                errorMessage = entity.errorMessage,
                localPath = entity.localPath
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun enqueueDownload(
        chapterId: Int,
        seriesId: Int,
        volumeId: Int,
        title: String,
        coverUrl: String?,
        totalPages: Int,
        libraryName: String = "Downloads",
        seriesName: String = "",
        chapterNumber: String = ""
    ) = withContext(Dispatchers.IO) {
        val task = DownloadTaskEntity(
            chapterId = chapterId,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryName = libraryName,
            seriesName = seriesName,
            chapterNumber = chapterNumber,
            title = title,
            coverUrl = coverUrl,
            totalPages = totalPages,
            downloadedPages = 0,
            status = DownloadStatus.QUEUED
        )
        database.downloadDao().insertDownloadTask(task)

        val workData = Data.Builder()
            .putInt("chapterId", chapterId)
            .putInt("totalPages", totalPages)
            .putString("libraryName", libraryName)
            .putString("seriesName", seriesName)
            .putString("chapterNumber", chapterNumber)
            .putString("title", title)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("download_$chapterId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$chapterId",
            ExistingWorkPolicy.REPLACE,
            downloadWork
        )
    }

    suspend fun enqueueBulkDownloads(
        items: List<BulkDownloadItem>,
        count: Int,
        unreadOnly: Boolean
    ) = withContext(Dispatchers.IO) {
        val eligible = if (unreadOnly) items.filter { !it.isRead } else items
        eligible.take(count).forEach { item ->
            enqueueDownload(
                chapterId = item.chapterId,
                seriesId = item.seriesId,
                volumeId = item.volumeId,
                title = item.title,
                coverUrl = item.coverUrl,
                totalPages = item.totalPages,
                libraryName = item.libraryName,
                seriesName = item.seriesName,
                chapterNumber = item.chapterNumber
            )
        }
    }

    suspend fun pauseDownload(chapterId: Int) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$chapterId")
        database.downloadDao().updateStatus(chapterId, DownloadStatus.PAUSED)
    }

    suspend fun resumeDownload(chapterId: Int) = withContext(Dispatchers.IO) {
        val task = database.downloadDao().getDownloadByChapterId(chapterId) ?: return@withContext
        enqueueDownload(
            chapterId = task.chapterId,
            seriesId = task.seriesId,
            volumeId = task.volumeId,
            title = task.title,
            coverUrl = task.coverUrl,
            totalPages = task.totalPages,
            libraryName = task.libraryName,
            seriesName = task.seriesName,
            chapterNumber = task.chapterNumber
        )
    }

    private fun cleanupEmptyDirectories(file: File?) {
        if (file == null) return
        try {
            var currentDir: File? = if (file.isDirectory) file.parentFile else file.parentFile
            val safeAppDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            val rootPath = safeAppDir.absolutePath

            while (currentDir != null && currentDir.exists()) {
                if (currentDir.absolutePath == rootPath || currentDir.parentFile == null) {
                    break
                }
                val remainingFiles = currentDir.listFiles()?.filter { !it.name.startsWith(".") }
                if (remainingFiles.isNullOrEmpty()) {
                    val parent = currentDir.parentFile
                    currentDir.deleteRecursively()
                    currentDir = parent
                } else {
                    break
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun cancelDownload(chapterId: Int) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$chapterId")
        val task = database.downloadDao().getDownloadByChapterId(chapterId)
        if (task?.localPath != null) {
            val file = File(task.localPath)
            if (file.exists()) {
                file.deleteRecursively()
            }
            cleanupEmptyDirectories(file)
        }
        database.downloadDao().deleteDownloadTask(chapterId)
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        database.downloadDao().clearCompleted()
    }

    suspend fun deleteLocalChapter(chapterId: Int) = withContext(Dispatchers.IO) {
        val task = database.downloadDao().getDownloadByChapterId(chapterId)
        val localPath = task?.localPath
        if (localPath != null) {
            val file = File(localPath)
            if (file.exists()) {
                file.deleteRecursively()
            }
            cleanupEmptyDirectories(file)
        }
        try {
            val cbzCacheDir = File(context.cacheDir, "cbz_extracted/$chapterId")
            if (cbzCacheDir.exists()) {
                cbzCacheDir.deleteRecursively()
            }
        } catch (_: Exception) {}

        database.downloadDao().deleteDownloadTask(chapterId)
    }

    suspend fun validateExternalDeletions() = withContext(Dispatchers.IO) {
        try {
            val tasks = database.downloadDao().getAllDownloadsList()
            for (task in tasks) {
                val path = task.localPath
                var isFileValid = false

                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists()) {
                        if (file.isFile) {
                            isFileValid = file.length() > 0L
                        } else if (file.isDirectory) {
                            val hasFiles = file.walkTopDown().any { f ->
                                f.isFile && f.length() > 0L && !f.name.startsWith(".")
                            }
                            isFileValid = hasFiles
                        }
                    }
                }

                if (!isFileValid && (task.status == DownloadStatus.COMPLETED || !path.isNullOrBlank())) {
                    database.downloadDao().deleteDownloadTask(task.chapterId)
                    try {
                        val cbzCacheDir = File(context.cacheDir, "cbz_extracted/${task.chapterId}")
                        if (cbzCacheDir.exists()) {
                            cbzCacheDir.deleteRecursively()
                        }
                    } catch (_: Exception) {}
                    if (!path.isNullOrBlank()) {
                        cleanupEmptyDirectories(File(path))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error validating external file deletions", e)
        }
    }
}
