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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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

private fun InputStream.copyToWithCancellation(
    out: OutputStream,
    job: Job,
    bufferSize: Int = 8192
): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        job.ensureActive()
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)
    }
    return bytesCopied
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val chapterId = inputData.getInt("chapterId", -1)
        var totalPages = inputData.getInt("totalPages", 0)
        val rawLibraryName = inputData.getString("libraryName") ?: "Downloads"
        val rawSeriesName = inputData.getString("seriesName") ?: ""
        val rawTitle = inputData.getString("title") ?: "Chapter $chapterId"

        if (chapterId == -1) {
            return@withContext Result.failure()
        }

        try {
            val config = userPreferencesRepository.serverConfigFlow.first()

            if (config.serverUrl.isBlank() || (config.apiKey.isBlank() && config.jwtToken.isBlank())) {
                val task = database.downloadDao().getDownloadByChapterId(chapterId)
                if (task != null) {
                    database.downloadDao().updateDownloadTask(
                        task.copy(status = DownloadStatus.FAILED, errorMessage = "Server URL or API Key missing")
                    )
                }
                return@withContext Result.failure()
            }

            // Fetch page count from Kavita if missing
            if (totalPages <= 0) {
                try {
                    val api = NetworkClient.getKavitaApi(userPreferencesRepository)
                    val chapterRes = api.getChapterById(chapterId)
                    if (chapterRes.isSuccessful && chapterRes.body() != null) {
                        totalPages = chapterRes.body()!!.pages
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
            if (totalPages <= 0) totalPages = 1

            database.downloadDao().updateProgress(chapterId, 0, DownloadStatus.DOWNLOADING)

            val libraryName = sanitizeFileName(rawLibraryName.ifBlank { "Comics" })
            val seriesName = sanitizeFileName(rawSeriesName.ifBlank { "Series" })
            val title = sanitizeFileName(rawTitle)

            @Suppress("DEPRECATION")
            val safeAppDir = run {
                val mediaDirs = context.externalMediaDirs
                if (!mediaDirs.isNullOrEmpty() && mediaDirs[0] != null) {
                    File(mediaDirs[0], "Downloads").apply { mkdirs() }
                } else {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: File(context.filesDir, "downloads")
                }
            }

            val targetFolder = File(safeAppDir, "$libraryName/$seriesName").apply {
                mkdirs()
            }

            // ═══════════════════════════════════════════════════════════════════
            // Attempt 1: Native Kavita chapter download (returns original file)
            // ═══════════════════════════════════════════════════════════════════
            val nativeResult = tryNativeDownload(chapterId, targetFolder, safeAppDir, libraryName, seriesName, title, totalPages)
            if (nativeResult != null) {
                return@withContext nativeResult
            }

            // ═══════════════════════════════════════════════════════════════════
            // Attempt 2: Page-by-page fallback (last resort)
            // ═══════════════════════════════════════════════════════════════════
            Log.w(TAG, "Native download failed for chapter $chapterId, falling back to page-by-page download")

            downloadPageByPage(chapterId, totalPages, targetFolder, safeAppDir, libraryName, seriesName, title)
        } catch (e: CancellationException) {
            Log.i(TAG, "Download worker cancelled for chapter $chapterId")
            if (isStopped) {
                database.downloadDao().updateProgress(chapterId, 0, DownloadStatus.PAUSED)
            }
            throw e
        }
    }

    private suspend fun executeCallWithCancellation(request: Request): okhttp3.Response {
        val call = downloadClient.newCall(request)
        val handle = currentCoroutineContext().job.invokeOnCompletion {
            call.cancel()
        }
        return try {
            call.execute()
        } finally {
            handle.dispose()
        }
    }

    private suspend fun tryNativeDownload(
        chapterId: Int,
        targetFolder: File,
        safeAppDir: File,
        libraryName: String,
        seriesName: String,
        title: String,
        totalPages: Int
    ): Result? {
        try {
            val downloadUrl = "${NetworkClient.PLACEHOLDER_BASE_URL}api/Download/chapter?chapterId=$chapterId"
            val request = Request.Builder().url(downloadUrl).build()

            val response = executeCallWithCancellation(request)
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Native download returned HTTP ${resp.code} for chapter $chapterId")
                    return null
                }

                val responseBody = resp.body ?: run {
                    Log.w(TAG, "Native download returned empty body for chapter $chapterId")
                    return null
                }

                val contentDisposition = resp.header("Content-Disposition")
                val serverFilename = extractFilenameFromContentDisposition(contentDisposition)
                val outputFilename = if (serverFilename != null) {
                    sanitizeFileName(serverFilename)
                } else {
                    "$title.cbz"
                }

                Log.d(TAG, "Native download for chapter $chapterId: server filename='$serverFilename', using='$outputFilename'")

                var activeTargetDir = targetFolder
                if (!activeTargetDir.exists() || !activeTargetDir.canWrite()) {
                    activeTargetDir = File(safeAppDir, "$libraryName/$seriesName").apply { mkdirs() }
                } else {
                    activeTargetDir.mkdirs()
                }

                val partFile = File(activeTargetDir, "$outputFilename.part")
                val finalFile = File(activeTargetDir, outputFilename)

                try {
                    responseBody.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            input.copyToWithCancellation(output, currentCoroutineContext().job)
                        }
                    }

                    if (partFile.exists() && partFile.length() > 0L) {
                        if (!partFile.renameTo(finalFile)) {
                            partFile.copyTo(finalFile, overwrite = true)
                            partFile.delete()
                        }

                        if (finalFile.exists() && finalFile.length() > 0L) {
                            database.downloadDao().updateDownloadResult(
                                chapterId = chapterId,
                                localPath = finalFile.absolutePath,
                                downloadedPages = totalPages,
                                status = DownloadStatus.COMPLETED
                            )
                            Log.i(TAG, "Native download succeeded: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
                            return Result.success()
                        } else {
                            Log.w(TAG, "Native download produced empty/missing file for chapter $chapterId")
                            return null
                        }
                    } else {
                        Log.w(TAG, "Native download part file empty for chapter $chapterId")
                        return null
                    }
                } finally {
                    if (partFile.exists()) {
                        partFile.delete()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Native download exception for chapter $chapterId", e)
            return null
        }
    }

    private suspend fun downloadPageByPage(
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
                currentCoroutineContext().ensureActive()
                if (isStopped) {
                    database.downloadDao().updateProgress(chapterId, pageIdx, DownloadStatus.PAUSED)
                    return Result.retry()
                }

                val formattedName = "$title p${String.format(Locale.ROOT, "%03d", pageIdx)}.webp"
                var pageFile = File(pageTargetDir, formattedName)
                var partFile = File(pageTargetDir, "$formattedName.part")

                val imageUrl = "${NetworkClient.PLACEHOLDER_BASE_URL}api/Reader/image?chapterId=$chapterId&page=$pageIdx"
                val request = Request.Builder().url(imageUrl).build()

                val response = try {
                    executeCallWithCancellation(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "Transient network error downloading chapter $chapterId page $pageIdx", e)
                    val task = database.downloadDao().getDownloadByChapterId(chapterId)
                    if (task != null) {
                        database.downloadDao().updateDownloadTask(
                            task.copy(status = DownloadStatus.PAUSED, errorMessage = "Network error, retrying...")
                        )
                    }
                    return Result.retry()
                }

                response.use { resp ->
                    val responseBody = resp.body
                    if (resp.isSuccessful && responseBody != null) {
                        try {
                            responseBody.byteStream().use { input ->
                                FileOutputStream(partFile).use { output ->
                                    input.copyToWithCancellation(output, currentCoroutineContext().job)
                                }
                            }
                            if (partFile.exists() && partFile.length() > 0L) {
                                if (!partFile.renameTo(pageFile)) {
                                    partFile.copyTo(pageFile, overwrite = true)
                                    partFile.delete()
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            try {
                                pageTargetDir = File(File(safeAppDir, "$libraryName/$seriesName"), title).apply { mkdirs() }
                                pageFile = File(pageTargetDir, formattedName)
                                partFile = File(pageTargetDir, "$formattedName.part")
                                responseBody.byteStream().use { input ->
                                    FileOutputStream(partFile).use { output ->
                                        input.copyToWithCancellation(output, currentCoroutineContext().job)
                                    }
                                }
                                if (partFile.exists() && partFile.length() > 0L) {
                                    if (!partFile.renameTo(pageFile)) {
                                        partFile.copyTo(pageFile, overwrite = true)
                                        partFile.delete()
                                    }
                                }
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Log.e(TAG, "Page download write failed for chapter $chapterId page $pageIdx", ex)
                                val task = database.downloadDao().getDownloadByChapterId(chapterId)
                                if (task != null) {
                                    database.downloadDao().updateDownloadTask(
                                        task.copy(status = DownloadStatus.FAILED, errorMessage = ex.message ?: "Write failed")
                                    )
                                }
                                return Result.failure()
                            }
                        } finally {
                            if (partFile.exists()) {
                                partFile.delete()
                            }
                        }
                    } else {
                        val errorMsg = "HTTP ${resp.code}: ${resp.message}"
                        Log.e(TAG, "Page download failed for chapter $chapterId page $pageIdx: $errorMsg")
                        val task = database.downloadDao().getDownloadByChapterId(chapterId)
                        if (task != null) {
                            database.downloadDao().updateDownloadTask(
                                task.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg)
                            )
                        }
                        return Result.failure()
                    }
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

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Transient I/O error during page-by-page download for chapter $chapterId, will retry", e)
            val task = database.downloadDao().getDownloadByChapterId(chapterId)
            if (task != null) {
                database.downloadDao().updateDownloadTask(
                    task.copy(status = DownloadStatus.PAUSED, errorMessage = "Network error, retrying...")
                )
            }
            Result.retry()
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
