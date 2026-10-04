package com.zemmy.sigurvita.core.download

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase

import com.zemmy.sigurvita.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "DownloadWorker"

private fun sanitizeFileName(name: String): String {
    return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
}

private fun extractFilenameFromContentDisposition(header: String?): String? {
    if (header.isNullOrBlank()) return null

    val extMatch = Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)?''(.+)""").find(header)
    if (extMatch != null) {
        val decoded = java.net.URLDecoder.decode(extMatch.groupValues[1].trim(), "UTF-8")
        if (decoded.isNotBlank()) return decoded
    }

    val quotedMatch = Regex("""filename\s*=\s*"([^"]+)"""").find(header)
    if (quotedMatch != null) {
        return quotedMatch.groupValues[1].trim()
    }

    val unquotedMatch = Regex("""filename\s*=\s*([^\s;]+)""").find(header)
    if (unquotedMatch != null) {
        return unquotedMatch.groupValues[1].trim()
    }

    return null
}

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = SigurvitaDatabase.getDatabase(context)
    private val userPreferencesRepository = SigurvitaApp.instance.userPreferencesRepository

    // Derive from the shared authenticated client but with longer timeouts for large downloads.
    // OkHttpClient.newBuilder() shares connection pool and dispatcher — no extra threads.
    private val downloadClient by lazy {
        NetworkClient.getOkHttpClient(userPreferencesRepository).newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

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
        val settings = userPreferencesRepository.readerSettingsFlow.first()

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
                val api = NetworkClient.getKavitaApi(userPreferencesRepository)
                val chapterRes = api.getChapterById(chapterId)
                if (chapterRes.isSuccessful && chapterRes.body() != null) {
                    totalPages = chapterRes.body()!!.pages
                }
            } catch (_: Exception) {}
        }
        if (totalPages <= 0) totalPages = 1

        database.downloadDao().updateProgress(chapterId, 0, DownloadStatus.DOWNLOADING)

        val libraryName = sanitizeFileName(rawLibraryName.ifBlank { "Comics" })
        val seriesName = sanitizeFileName(rawSeriesName.ifBlank { "Series" })
        val title = sanitizeFileName(rawTitle)

        val customPath = settings.downloadPath
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

        // ═══════════════════════════════════════════════════════════════════
        // Attempt 1: Native Kavita chapter download (returns original file)
        // ═══════════════════════════════════════════════════════════════════
        // Uses /api/Download/chapter which returns the original archive file
        // directly from the server. Auth is handled by AuthInterceptor.
        // The request goes through localhost:80 -> AuthInterceptor rewrites
        // to the real server URL and injects auth headers.

        val nativeResult = tryNativeDownload(chapterId, targetFolder, safeAppDir, libraryName, seriesName, title, totalPages)
        if (nativeResult != null) {
            return nativeResult
        }
        // If native download failed, fall through to page-by-page

        // ═══════════════════════════════════════════════════════════════════
        // Attempt 2: Page-by-page fallback (last resort)
        // ═══════════════════════════════════════════════════════════════════
        Log.w(TAG, "Native download failed for chapter $chapterId, falling back to page-by-page download")

        return downloadPageByPage(chapterId, totalPages, targetFolder, safeAppDir, libraryName, seriesName, title)
    }

    private fun tryNativeDownload(
        chapterId: Int,
        targetFolder: File,
        safeAppDir: File,
        libraryName: String,
        seriesName: String,
        title: String,
        totalPages: Int
    ): Result? {
        try {
            // Build request against localhost — AuthInterceptor rewrites to real server
            val downloadUrl = "http://localhost/api/Download/chapter?chapterId=$chapterId"
            val request = Request.Builder().url(downloadUrl).build()

            val response = downloadClient.newCall(request).execute()
            try {
                if (!response.isSuccessful) {
                    Log.w(TAG, "Native download returned HTTP ${response.code} for chapter $chapterId")
                    return null
                }

                val responseBody = response.body ?: run {
                    Log.w(TAG, "Native download returned empty body for chapter $chapterId")
                    return null
                }

                // Extract original filename from Content-Disposition header
                val contentDisposition = response.header("Content-Disposition")
                val serverFilename = extractFilenameFromContentDisposition(contentDisposition)

                // Use server filename if available, otherwise fall back to constructed name
                val outputFilename = if (serverFilename != null) {
                    sanitizeFileName(serverFilename)
                } else {
                    "$title.cbz"
                }

                Log.d(TAG, "Native download for chapter $chapterId: server filename='$serverFilename', using='$outputFilename'")

                // Buffer to temp file first, then move to target
                val tempFile = File.createTempFile("cbz_dl_", ".tmp", context.cacheDir)
                try {
                    responseBody.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }

                    // Try primary target folder, then safe fallback
                    var cbzFile = File(targetFolder, outputFilename)
                    try {
                        tempFile.copyTo(cbzFile, overwrite = true)
                    } catch (_: Exception) {
                        val safeTargetFolder = File(safeAppDir, "$libraryName/$seriesName").apply { mkdirs() }
                        cbzFile = File(safeTargetFolder, outputFilename)
                        tempFile.copyTo(cbzFile, overwrite = true)
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
                        Log.i(TAG, "Native download succeeded: ${cbzFile.absolutePath} (${cbzFile.length()} bytes)")
                        return Result.success()
                    } else {
                        Log.w(TAG, "Native download produced empty/missing file for chapter $chapterId")
                        return null
                    }
                } finally {
                    tempFile.delete()
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Native download exception for chapter $chapterId", e)
            return null
        }
    }

    /**
     * Downloads chapter page-by-page via /api/Reader/image.
     * This is the fallback path when native download fails.
     */
    private fun downloadPageByPage(
        chapterId: Int,
        totalPages: Int,
        initialTargetFolder: File,
        safeAppDir: File,
        libraryName: String,
        seriesName: String,
        title: String,
    ): Result {
        var pageTargetDir = File(initialTargetFolder, title)
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

                val formattedName = "$title p${String.format(Locale.ROOT, "%03d", pageIdx)}.webp"
                var pageFile = File(pageTargetDir, formattedName)

                // Use localhost URL — AuthInterceptor rewrites to real server
                val imageUrl = "http://localhost/api/Reader/image?chapterId=$chapterId&page=$pageIdx"
                val request = Request.Builder().url(imageUrl).build()

                val response = downloadClient.newCall(request).execute()
                try {
                    val responseBody = response.body
                    if (response.isSuccessful && responseBody != null) {
                        val tempFile = File.createTempFile("page_dl_", ".tmp", context.cacheDir)
                        try {
                            responseBody.byteStream().use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output, bufferSize = 8192)
                                }
                            }
                            try {
                                tempFile.copyTo(pageFile, overwrite = true)
                            } catch (_: Exception) {
                                pageTargetDir = File(File(safeAppDir, "$libraryName/$seriesName"), title).apply { mkdirs() }
                                pageFile = File(pageTargetDir, formattedName)
                                tempFile.copyTo(pageFile, overwrite = true)
                            }
                        } finally {
                            tempFile.delete()
                        }
                    } else {
                        val errorMsg = "HTTP ${response.code}: ${response.message}"
                        Log.e(TAG, "Page download failed for chapter $chapterId page $pageIdx: $errorMsg")
                        val task = database.downloadDao().getDownloadByChapterId(chapterId)
                        if (task != null) {
                            database.downloadDao().updateDownloadTask(
                                task.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg)
                            )
                        }
                        return Result.failure()
                    }
                } finally {
                    response.close()
                }

                database.downloadDao().updateProgress(chapterId, pageIdx + 1, DownloadStatus.DOWNLOADING)
            }

            val finalPath = pageTargetDir.absolutePath

            database.downloadDao().updateDownloadResult(
                chapterId = chapterId,
                localPath = finalPath,
                downloadedPages = totalPages,
                status = DownloadStatus.COMPLETED
            )
            database.chapterDao().updateDownloadStatus(
                chapterId = chapterId,
                isDownloaded = true,
                localPath = finalPath
            )

            Result.success()
        } catch (e: Exception) {
            val task = database.downloadDao().getDownloadByChapterId(chapterId)
            val errText = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Page-by-page download failed for chapter $chapterId", e)
            if (task != null) {
                database.downloadDao().updateDownloadTask(
                    task.copy(status = DownloadStatus.FAILED, errorMessage = errText)
                )
            }
            Result.failure()
        }
    }


}
