package com.zemmy.sigurvita.core.download

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private fun sanitizeFileName(name: String): String {
    return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
}

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = SigurvitaDatabase.getDatabase(context)
    private val userPreferencesRepository = UserPreferencesRepository(context)

    private val plainHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val chapterId = inputData.getInt("chapterId", -1)
        var totalPages = inputData.getInt("totalPages", 0)
        val rawLibraryName = inputData.getString("libraryName") ?: "Downloads"
        val rawSeriesName = inputData.getString("seriesName") ?: ""
        val rawTitle = inputData.getString("title") ?: "Chapter $chapterId"

        if (chapterId == -1) {
            return Result.failure()
        }

        val config = userPreferencesRepository.serverConfigFlow.first()
        if (config.serverUrl.isBlank() || (config.apiKey.isBlank() && config.jwtToken.isBlank())) {
            val task = database.downloadDao().getDownloadByChapterId(chapterId)
            if (task != null) {
                database.downloadDao().updateDownloadTask(
                    task.copy(status = DownloadStatus.FAILED, errorMessage = "Server URL or API Key missing")
                )
            }
            return Result.failure()
        }

        // Fetch page count from Kavita if missing
        if (totalPages <= 0) {
            try {
                val api = NetworkClient.createKavitaApi(userPreferencesRepository)
                val chapterRes = api.getChapterById(chapterId)
                if (chapterRes.isSuccessful && chapterRes.body() != null) {
                    totalPages = chapterRes.body()!!.pages
                }
            } catch (_: Exception) {}
        }
        if (totalPages <= 0) totalPages = 1

        database.downloadDao().updateProgress(chapterId, 0, DownloadStatus.DOWNLOADING)

        val libraryName = sanitizeFileName(rawLibraryName.ifBlank { "Downloads" })
        val seriesName = sanitizeFileName(rawSeriesName.ifBlank { "Series" })
        val title = sanitizeFileName(rawTitle)

        val customPath = userPreferencesRepository.readerSettingsFlow.first().downloadPath
        val safeAppDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")

        val rootDir = if (customPath.isNotBlank()) {
            val customFile = File(customPath)
            if (customFile.exists() && customFile.canWrite()) {
                customFile
            } else {
                try {
                    if (customFile.mkdirs() && customFile.canWrite()) customFile else safeAppDir
                } catch (_: Exception) {
                    safeAppDir
                }
            }
        } else {
            safeAppDir
        }

        var targetFolder = File(rootDir, "$libraryName/$seriesName")
        try {
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }
        } catch (_: Exception) {}

        if (!targetFolder.exists() || !targetFolder.canWrite()) {
            targetFolder = File(safeAppDir, "$libraryName/$seriesName")
            targetFolder.mkdirs()
        }

        var cbzFile = File(targetFolder, "$title.cbz")
        val baseUrl = config.serverUrl.trim().removeSuffix("/")

        // Attempt 1: Download CBZ Archive directly from Kavita
        try {
            val downloadUrl = "$baseUrl/api/Download/chapter?chapterId=$chapterId"
            val requestBuilder = Request.Builder().url(downloadUrl)
            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("x-api-key", config.apiKey)
            }
            if (config.jwtToken.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.jwtToken}")
            }

            val response = plainHttpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body

            if (response.isSuccessful && responseBody != null) {
                try {
                    responseBody.byteStream().use { input ->
                        FileOutputStream(cbzFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to safe internal/app dir
                    val safeTargetFolder = File(safeAppDir, "$libraryName/$seriesName").apply { mkdirs() }
                    cbzFile = File(safeTargetFolder, "$title.cbz")
                    responseBody.byteStream().use { input ->
                        FileOutputStream(cbzFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (cbzFile.exists() && cbzFile.length() > 0L) {
                    database.downloadDao().updateDownloadResult(
                        chapterId = chapterId,
                        localPath = cbzFile.absolutePath,
                        downloadedPages = totalPages,
                        status = DownloadStatus.COMPLETED
                    )
                    database.chapterDao().updateDownloadStatus(
                        chapterId = chapterId,
                        isDownloaded = true,
                        localPath = cbzFile.absolutePath
                    )
                    return Result.success()
                }
            }
        } catch (_: Exception) {
            // Fallback to page-by-page downloading
        }

        // Attempt 2: Download individual page images as fallback
        var pageTargetDir = File(targetFolder, title)
        try {
            if (!pageTargetDir.exists()) {
                pageTargetDir.mkdirs()
            }
        } catch (_: Exception) {}

        if (!pageTargetDir.exists() || !pageTargetDir.canWrite()) {
            pageTargetDir = File(File(safeAppDir, "$libraryName/$seriesName"), title)
            pageTargetDir.mkdirs()
        }

        return try {
            for (pageIdx in 0 until totalPages) {
                if (isStopped) {
                    database.downloadDao().updateProgress(chapterId, pageIdx, DownloadStatus.PAUSED)
                    return Result.retry()
                }

                var pageFile = File(pageTargetDir, "page_$pageIdx.jpg")
                val imageUrl = "$baseUrl/api/reader/image?chapterId=$chapterId&page=$pageIdx&apiKey=${config.apiKey}"
                val requestBuilder = Request.Builder().url(imageUrl)

                if (config.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("x-api-key", config.apiKey)
                }
                if (config.jwtToken.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${config.jwtToken}")
                }

                var response = plainHttpClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful) {
                    response.close()
                    val altUrl = "$baseUrl/api/Reader/image?chapterId=$chapterId&page=$pageIdx&apiKey=${config.apiKey}"
                    response = plainHttpClient.newCall(Request.Builder().url(altUrl).apply {
                        if (config.apiKey.isNotBlank()) addHeader("x-api-key", config.apiKey)
                        if (config.jwtToken.isNotBlank()) addHeader("Authorization", "Bearer ${config.jwtToken}")
                    }.build()).execute()
                }

                val responseBody = response.body
                if (response.isSuccessful && responseBody != null) {
                    try {
                        responseBody.byteStream().use { input ->
                            FileOutputStream(pageFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Exception) {
                        // Switch to safe app dir on EPERM
                        pageTargetDir = File(File(safeAppDir, "$libraryName/$seriesName"), title).apply { mkdirs() }
                        pageFile = File(pageTargetDir, "page_$pageIdx.jpg")
                        responseBody.byteStream().use { input ->
                            FileOutputStream(pageFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } else {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    val task = database.downloadDao().getDownloadByChapterId(chapterId)
                    if (task != null) {
                        database.downloadDao().updateDownloadTask(
                            task.copy(
                                status = DownloadStatus.FAILED,
                                errorMessage = errorMsg
                            )
                        )
                    }
                    return Result.failure()
                }

                database.downloadDao().updateProgress(chapterId, pageIdx + 1, DownloadStatus.DOWNLOADING)
            }

            database.downloadDao().updateDownloadResult(
                chapterId = chapterId,
                localPath = pageTargetDir.absolutePath,
                downloadedPages = totalPages,
                status = DownloadStatus.COMPLETED
            )
            database.chapterDao().updateDownloadStatus(
                chapterId = chapterId,
                isDownloaded = true,
                localPath = pageTargetDir.absolutePath
            )

            Result.success()
        } catch (e: Exception) {
            val task = database.downloadDao().getDownloadByChapterId(chapterId)
            val errText = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            if (task != null) {
                database.downloadDao().updateDownloadTask(
                    task.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = errText
                    )
                )
            }
            Result.failure()
        }
    }
}
