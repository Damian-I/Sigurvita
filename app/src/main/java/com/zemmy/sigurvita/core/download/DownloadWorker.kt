package com.zemmy.sigurvita.core.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = SigurvitaDatabase.getDatabase(context)
    private val userPreferencesRepository = UserPreferencesRepository(context)
    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result {
        val chapterId = inputData.getInt("chapterId", -1)
        val totalPages = inputData.getInt("totalPages", 0)

        if (chapterId == -1 || totalPages <= 0) {
            return Result.failure()
        }

        val config = userPreferencesRepository.serverConfigFlow.first()
        if (config.serverUrl.isBlank() || config.apiKey.isBlank()) {
            database.downloadDao().updateProgress(chapterId, 0, DownloadStatus.FAILED)
            return Result.failure()
        }

        database.downloadDao().updateProgress(chapterId, 0, DownloadStatus.DOWNLOADING)

        val customPath = userPreferencesRepository.readerSettingsFlow.first().downloadPath
        val targetDir = if (customPath.isNotBlank()) {
            File(customPath, "chapters/$chapterId")
        } else {
            File(context.filesDir, "downloads/chapters/$chapterId")
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        return try {
            for (pageIdx in 0 until totalPages) {
                if (isStopped) {
                    database.downloadDao().updateProgress(chapterId, pageIdx, DownloadStatus.PAUSED)
                    return Result.retry()
                }

                val pageFile = File(targetDir, "page_$pageIdx.jpg")
                if (!pageFile.exists()) {
                    val imageUrl = "${config.serverUrl}/api/Reader/image?chapterId=$chapterId&page=$pageIdx&apiKey=${config.apiKey}"
                    val request = Request.Builder().url(imageUrl).build()
                    val response = httpClient.newCall(request).execute()

                    if (response.isSuccessful && response.body != null) {
                        response.body!!.byteStream().use { input ->
                            FileOutputStream(pageFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        database.downloadDao().updateProgress(chapterId, pageIdx, DownloadStatus.FAILED)
                        return Result.failure()
                    }
                }

                database.downloadDao().updateProgress(chapterId, pageIdx + 1, DownloadStatus.DOWNLOADING)
            }

            database.downloadDao().updateProgress(chapterId, totalPages, DownloadStatus.COMPLETED)
            database.chapterDao().updateDownloadStatus(
                chapterId = chapterId,
                isDownloaded = true,
                localPath = targetDir.absolutePath
            )

            Result.success()
        } catch (_: Exception) {
            database.downloadDao().updateProgress(chapterId, 0, DownloadStatus.FAILED)
            Result.failure()
        }
    }
}
