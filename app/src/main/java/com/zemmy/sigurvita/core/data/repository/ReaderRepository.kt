package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import android.util.Log
import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.MarkVolumesReadDto
import com.zemmy.sigurvita.core.data.api.dto.ProgressDto
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.util.naturalCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.ZipFile

class ReaderRepository(
    private val api: KavitaApi,
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val database: SigurvitaDatabase
) {
    suspend fun getPages(chapterId: Int, totalPages: Int): List<ReaderPage> = withContext(Dispatchers.IO) {
        val config = preferencesRepository.serverConfigFlow.first()
        val downloadTask = database.downloadDao().getDownloadByChapterId(chapterId)
        val localPath = downloadTask?.localPath ?: database.chapterDao().getChapterById(chapterId)?.localPath

        fun isSupportedImage(filename: String): Boolean {
            val lower = filename.lowercase()
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                   lower.endsWith(".png") || lower.endsWith(".webp") ||
                   lower.endsWith(".gif") || lower.endsWith(".bmp")
        }

        // Case 1: Downloaded Archive File (.cbz, .zip, etc.)
        if (localPath != null) {
            val archiveFile = File(localPath)
            if (archiveFile.exists() && archiveFile.isFile && archiveFile.length() > 0L) {
                try {
                    val cacheDir = File(context.cacheDir, "cbz_extracted/$chapterId").apply { mkdirs() }
                    val extractedFiles = mutableListOf<ReaderPage>()

                    ZipFile(archiveFile).use { zip ->
                        val entries = zip.entries().asSequence()
                            .filter { entry ->
                                !entry.isDirectory &&
                                !File(entry.name).name.startsWith(".") &&
                                isSupportedImage(entry.name)
                            }
                            .sortedWith { e1, e2 ->
                                naturalCompare(File(e1.name).name, File(e2.name).name)
                            }
                            .toList()

                        entries.forEachIndexed { idx, entry ->
                            val pageFile = File(cacheDir, "page_$idx.webp")
                            if (!pageFile.exists() || pageFile.length() <= 0L) {
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(pageFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            if (pageFile.exists() && pageFile.length() > 0L) {
                                extractedFiles.add(
                                    ReaderPage(
                                        index = idx,
                                        pageNumber = idx + 1,
                                        imageUrl = pageFile.absolutePath,
                                        isLocal = true
                                    )
                                )
                            }
                        }
                    }

                    if (extractedFiles.isNotEmpty()) {
                        return@withContext extractedFiles
                    }
                } catch (e: Exception) {
                    Log.w("ReaderRepository", "Failed to extract archive at $localPath: ${e.message}")
                }
            }
        }

        // Case 2: Downloaded Image Pages Folder
        if (localPath != null) {
            val localFolder = File(localPath)
            if (localFolder.exists() && localFolder.isDirectory) {
                val imageFiles = localFolder.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                        !file.name.startsWith(".") &&
                        isSupportedImage(file.name)
                    }
                    .sortedWith { f1, f2 -> naturalCompare(f1.name, f2.name) }
                    .toList()

                if (imageFiles.isNotEmpty()) {
                    return@withContext imageFiles.mapIndexed { idx, file ->
                        ReaderPage(
                            index = idx,
                            pageNumber = idx + 1,
                            imageUrl = file.absolutePath,
                            isLocal = true
                        )
                    }
                }
            }
        }

        // Case 3: Online Streaming Fallback
        var pageCount = totalPages
        if (pageCount <= 0) {
            try {
                val chapterRes = api.getChapterById(chapterId)
                if (chapterRes.isSuccessful && chapterRes.body() != null) {
                    pageCount = chapterRes.body()!!.pages
                }
            } catch (_: Exception) {}
        }
        if (pageCount <= 0) pageCount = 50

        (0 until pageCount).map { pageIdx ->
            ReaderPage(
                index = pageIdx,
                pageNumber = pageIdx + 1,
                imageUrl = "${config.serverUrl}/api/Reader/image?chapterId=$chapterId&page=$pageIdx&apiKey=${config.apiKey}",
                isLocal = false
            )
        }
    }

    suspend fun getProgress(chapterId: Int): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val res = api.getReaderProgress(chapterId)
            if (res.isSuccessful && res.body() != null) {
                res.body()!!.pageNum
            } else 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * @param resolvedLibraryId If > 0, skips expensive API lookups to resolve it
     */
    suspend fun saveProgress(
        seriesId: Int,
        volumeId: Int,
        chapterId: Int,
        pageNum: Int,
        totalPages: Int = 0,
        resolvedLibraryId: Int = 0
    ) = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.readerSettingsFlow.first()
        if (prefs.isIncognitoMode) {
            return@withContext
        }

        var resolvedSeriesId = seriesId
        var resolvedVolumeId = volumeId
        var libraryId = resolvedLibraryId

        if (resolvedSeriesId <= 0 || resolvedVolumeId <= 0) {
            try {
                val cRes = api.getChapterById(chapterId)
                if (cRes.isSuccessful && cRes.body() != null) {
                    val cDto = cRes.body()!!
                    if (resolvedVolumeId <= 0) resolvedVolumeId = cDto.volumeId
                }
            } catch (_: Exception) {}
        }

        // Only resolve libraryId from network if caller didn't provide it
        if (libraryId <= 0 && resolvedSeriesId > 0) {
            try {
                val sRes = api.getSeriesById(resolvedSeriesId)
                if (sRes.isSuccessful && sRes.body() != null) {
                    libraryId = sRes.body()!!.libraryId
                } else {
                    val sRes2 = api.getSeriesDetail(resolvedSeriesId)
                    if (sRes2.isSuccessful && sRes2.body() != null) {
                        libraryId = sRes2.body()!!.libraryId
                    }
                }
            } catch (_: Exception) {}
        }

        if (libraryId <= 0) {
            try {
                val libs = api.getLibraries()
                if (libs.isSuccessful && !libs.body().isNullOrEmpty()) {
                    libraryId = libs.body()!!.first().id
                }
            } catch (_: Exception) {}
        }
        if (libraryId <= 0) libraryId = 1

        val isFinished = totalPages in 1..pageNum
        val nowIso = Instant.now().toString()
        try {
            val progressDto = ProgressDto(
                volumeId = resolvedVolumeId,
                chapterId = chapterId,
                pageNum = pageNum,
                seriesId = resolvedSeriesId,
                libraryId = libraryId,
                bookScrollId = null,
                lastModifiedUtc = nowIso
            )
            val response = api.setReaderProgress(progressDto)
            if (!response.isSuccessful) {
                Log.e("ReaderRepository", "Failed to sync progress: HTTP ${response.code()}")
            }

            if (isFinished) {
                api.markMultipleRead(MarkVolumesReadDto(seriesId = resolvedSeriesId, chapterIds = listOf(chapterId)))
            }
        } catch (e: Exception) {
            Log.e("ReaderRepository", "Exception syncing progress", e)
        }
    }
}
