package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.FilterStatementDto
import com.zemmy.sigurvita.core.data.api.dto.FilterV2Dto
import com.zemmy.sigurvita.core.data.api.dto.LibraryDto
import com.zemmy.sigurvita.core.data.api.dto.MarkMultipleDto
import com.zemmy.sigurvita.core.data.api.dto.MarkSeriesDto
import com.zemmy.sigurvita.core.data.api.dto.ReaderProgressDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListItemDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesDto
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.data.db.entity.CachedReadingListEntity
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.download.DownloadWorker
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.DownloadFormat
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ReadingListItem
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import com.zemmy.sigurvita.ui.screens.readinglist.naturalCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.ZipFile

class AuthRepository(
    private val api: KavitaApi,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun testConnectionAndSave(serverUrl: String, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val formattedUrl = serverUrl.trim().removeSuffix("/")
            preferencesRepository.saveServerConfig(serverUrl = formattedUrl, apiKey = apiKey)

            // Try plugin authentication
            val authRes = api.authenticatePlugin(apiKey = apiKey)
            if (authRes.isSuccessful && authRes.body()?.token != null) {
                val token = authRes.body()!!.token!!
                preferencesRepository.saveServerConfig(
                    serverUrl = formattedUrl,
                    apiKey = apiKey,
                    jwtToken = token
                )
                Result.success(true)
            } else {
                val accountRes = api.getAccount()
                if (accountRes.isSuccessful && accountRes.body() != null) {
                    val user = accountRes.body()!!
                    preferencesRepository.saveServerConfig(
                        serverUrl = formattedUrl,
                        apiKey = apiKey,
                        userId = user.id
                    )
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to authenticate with Kavita server (HTTP ${authRes.code()})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class LibraryRepository(
    private val api: KavitaApi,
    private val database: SigurvitaDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun getLibraries(): Result<List<Library>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList: List<LibraryDto>? = null

            val res = api.getLibraries()
            if (res.isSuccessful && !res.body().isNullOrEmpty()) {
                dtoList = res.body()
            } else {
                val filterRes = api.getLibrariesFilter()
                if (filterRes.isSuccessful) {
                    dtoList = filterRes.body()
                }
            }

            val libraries = dtoList?.filter { it.id > 0 && it.name.isNotBlank() }?.map { dto ->
                Library(
                    id = dto.id,
                    name = dto.name,
                    type = dto.type,
                    coverUrl = dto.coverImage?.let { "${config.serverUrl}/$it" }
                )
            } ?: emptyList()

            Result.success(libraries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOnDeckSeries(): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList = api.getOnDeckSeries().body()

            if (dtoList.isNullOrEmpty()) {
                dtoList = api.getSeriesV2(libraryId = 0, filter = FilterV2Dto(), pageNumber = 1, pageSize = 20).body()
            }

            val seriesList = dtoList?.filter { it.id > 0 && it.name.isNotBlank() }?.map { dto ->
                Series(
                    id = dto.id,
                    libraryId = dto.libraryId,
                    name = dto.name,
                    localizedName = dto.localizedName,
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                    summary = dto.summary,
                    pagesRead = dto.pagesRead,
                    totalPages = dto.totalPages,
                    userRating = dto.userRating
                )
            } ?: emptyList()

            if (seriesList.isNotEmpty()) {
                database.seriesDao().insertSeries(seriesList.map {
                    CachedSeriesEntity(
                        id = it.id,
                        libraryId = it.libraryId,
                        name = it.name,
                        localizedName = it.localizedName,
                        coverUrl = it.coverUrl,
                        summary = it.summary,
                        pagesRead = it.pagesRead,
                        totalPages = it.totalPages,
                        isCompleted = it.isCompleted,
                        userRating = it.userRating
                    )
                })
            }

            Result.success(seriesList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentlyUpdatedSeries(): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList = api.getRecentlyUpdatedSeries().body()

            if (dtoList.isNullOrEmpty()) {
                dtoList = api.getSeriesV2(libraryId = 0, filter = FilterV2Dto(), pageNumber = 1, pageSize = 20).body()
            }

            val seriesList = dtoList?.filter { it.id > 0 && it.name.isNotBlank() }?.map { dto ->
                Series(
                    id = dto.id,
                    libraryId = dto.libraryId,
                    name = dto.name,
                    localizedName = dto.localizedName,
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                    summary = dto.summary,
                    pagesRead = dto.pagesRead,
                    totalPages = dto.totalPages,
                    userRating = dto.userRating
                )
            } ?: emptyList()

            Result.success(seriesList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesForLibrary(libraryId: Int): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList: List<SeriesDto>? = null

            if (libraryId > 0) {
                val filter = FilterV2Dto(
                    statements = listOf(
                        FilterStatementDto(field = 19, comparison = 0, value = libraryId.toString())
                    ),
                    combination = 1
                )
                val resWithFilter = api.getSeriesV2(libraryId = libraryId, filter = filter, pageNumber = 1, pageSize = 5000)
                if (resWithFilter.isSuccessful && !resWithFilter.body().isNullOrEmpty()) {
                    dtoList = resWithFilter.body()
                } else {
                    val resQuery = api.getSeriesV2(libraryId = libraryId, filter = FilterV2Dto(), pageNumber = 1, pageSize = 5000)
                    if (resQuery.isSuccessful && !resQuery.body().isNullOrEmpty()) {
                        dtoList = resQuery.body()
                    }
                }
            }

            // Fallback: If dtoList is null, fetch all series and filter client-side by libraryId
            if (dtoList == null) {
                val resAll = api.getSeriesV2(libraryId = 0, filter = FilterV2Dto(), pageNumber = 1, pageSize = 5000)
                if (resAll.isSuccessful) {
                    val allSeries = resAll.body() ?: emptyList()
                    dtoList = if (libraryId > 0) {
                        allSeries.filter { it.libraryId == libraryId }
                    } else {
                        allSeries
                    }
                }
            }

            val seriesList = dtoList?.filter { it.id > 0 && it.name.isNotBlank() }?.map { dto ->
                Series(
                    id = dto.id,
                    libraryId = dto.libraryId,
                    name = dto.name,
                    localizedName = dto.localizedName,
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                    summary = dto.summary,
                    pagesRead = dto.pagesRead,
                    totalPages = dto.totalPages,
                    userRating = dto.userRating
                )
            } ?: emptyList()

            Result.success(seriesList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class ReadingListRepository(
    private val api: KavitaApi,
    private val database: SigurvitaDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun getReadingLists(): Result<List<ReadingList>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList: List<ReadingListDto>? = null

            val resPost = api.getReadingLists(filter = FilterV2Dto())
            if (resPost.isSuccessful && !resPost.body().isNullOrEmpty()) {
                dtoList = resPost.body()
            } else {
                val resGet = api.getReadingListsGet()
                if (resGet.isSuccessful && !resGet.body().isNullOrEmpty()) {
                    dtoList = resGet.body()
                } else {
                    val resAll = api.getReadingListsAll()
                    if (resAll.isSuccessful && !resAll.body().isNullOrEmpty()) {
                        dtoList = resAll.body()
                    }
                }
            }

            val lists = dtoList?.filter { it.id > 0 && it.title.isNotBlank() }?.map { dto ->
                ReadingList(
                    id = dto.id,
                    title = dto.title,
                    summary = dto.summary,
                    itemCount = dto.itemCount,
                    coverUrl = dto.coverImage?.let { "${config.serverUrl}/$it" },
                    promoted = dto.promoted
                )
            } ?: emptyList()

            if (lists.isNotEmpty()) {
                database.readingListDao().insertReadingLists(lists.map {
                    CachedReadingListEntity(
                        id = it.id,
                        title = it.title,
                        summary = it.summary,
                        itemCount = it.itemCount,
                        coverUrl = it.coverUrl,
                        promoted = it.promoted
                    )
                })
            }

            Result.success(lists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReadingListItems(readingListId: Int): Result<List<ReadingListItem>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList: List<ReadingListItemDto>? = null

            val resPath = api.getReadingListItems(readingListId)
            if (resPath.isSuccessful && !resPath.body().isNullOrEmpty()) {
                dtoList = resPath.body()
            } else {
                val resQuery1 = api.getReadingListItemsQuery(readingListId)
                if (resQuery1.isSuccessful && !resQuery1.body().isNullOrEmpty()) {
                    dtoList = resQuery1.body()
                } else {
                    val resQuery2 = api.getReadingListItemsReadingListId(readingListId)
                    if (resQuery2.isSuccessful && !resQuery2.body().isNullOrEmpty()) {
                        dtoList = resQuery2.body()
                    }
                }
            }

            val items = dtoList?.filter { it.id > 0 }?.map { dto ->
                var pages = dto.pages
                var pagesRead = dto.pagesRead
                var isRead = dto.isRead

                if (dto.chapterId != null && dto.chapterId > 0 && pages <= 0) {
                    try {
                        val cRes = api.getChapterById(dto.chapterId)
                        if (cRes.isSuccessful && cRes.body() != null) {
                            val cBody = cRes.body()!!
                            pages = cBody.pages
                            pagesRead = cBody.pagesRead
                            isRead = cBody.isRead || (cBody.pages > 0 && cBody.pagesRead >= cBody.pages)
                        }
                    } catch (_: Exception) {}
                } else {
                    isRead = dto.isRead || (pages > 0 && pagesRead >= pages)
                }

                val coverUrl = if (dto.chapterId != null && dto.chapterId > 0) {
                    "${config.serverUrl}/api/image/chapter-cover?chapterId=${dto.chapterId}&apiKey=${config.apiKey}"
                } else {
                    "${config.serverUrl}/api/image/series-cover?seriesId=${dto.seriesId}&apiKey=${config.apiKey}"
                }

                ReadingListItem(
                    id = dto.id,
                    readingListId = dto.readingListId,
                    seriesId = dto.seriesId,
                    seriesName = dto.seriesName,
                    volumeId = dto.volumeId,
                    chapterId = dto.chapterId,
                    title = dto.title,
                    coverUrl = coverUrl,
                    pages = pages,
                    pagesRead = pagesRead,
                    isRead = isRead
                )
            } ?: emptyList()

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class SeriesRepository(
    private val api: KavitaApi,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun getSeriesDetail(seriesId: Int): Result<Series> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var series: Series? = null

            val resById = api.getSeriesById(seriesId)
            if (resById.isSuccessful && resById.body() != null) {
                val dto = resById.body()!!
                series = Series(
                    id = dto.id,
                    libraryId = dto.libraryId,
                    name = dto.name,
                    localizedName = dto.localizedName,
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                    summary = dto.summary,
                    pagesRead = dto.pagesRead,
                    totalPages = dto.totalPages,
                    userRating = dto.userRating
                )
            } else {
                val resDetail = api.getSeriesDetail(seriesId)
                if (resDetail.isSuccessful && resDetail.body() != null) {
                    val dto = resDetail.body()!!
                    series = Series(
                        id = dto.id,
                        libraryId = dto.libraryId,
                        name = dto.name,
                        coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                        summary = dto.summary,
                        pagesRead = dto.pagesRead,
                        totalPages = dto.totalPages,
                        isCompleted = dto.isCompleted
                    )
                }
            }

            if (series == null) {
                series = Series(
                    id = seriesId,
                    libraryId = 0,
                    name = "Series Details",
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${seriesId}&apiKey=${config.apiKey}"
                )
            }

            Result.success(series)
        } catch (e: Exception) {
            val config = preferencesRepository.serverConfigFlow.first()
            Result.success(
                Series(
                    id = seriesId,
                    libraryId = 0,
                    name = "Series Details",
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${seriesId}&apiKey=${config.apiKey}"
                )
            )
        }
    }

    suspend fun getSeriesVolumes(seriesId: Int): Result<List<Volume>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            val res = api.getSeriesVolumes(seriesId)
            if (res.isSuccessful) {
                val volumes = res.body()?.map { vDto ->
                    Volume(
                        id = vDto.id,
                        seriesId = vDto.seriesId,
                        name = vDto.name,
                        number = vDto.number,
                        pagesRead = vDto.pagesRead,
                        totalPages = vDto.totalPages,
                        chapters = vDto.chapters.map { cDto ->
                            val isChapterRead = cDto.isRead || (cDto.pages > 0 && cDto.pagesRead >= cDto.pages)
                            Chapter(
                                id = cDto.id,
                                volumeId = cDto.volumeId,
                                seriesId = cDto.seriesId,
                                chapterNumber = cDto.number,
                                title = cDto.title,
                                pages = cDto.pages,
                                pagesRead = cDto.pagesRead,
                                isRead = isChapterRead,
                                coverUrl = "${config.serverUrl}/api/image/chapter-cover?chapterId=${cDto.id}&apiKey=${config.apiKey}"
                            )
                        }
                    )
                } ?: emptyList()
                Result.success(volumes)
            } else {
                Result.failure(Exception("Failed to fetch volumes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markChapterRead(chapterId: Int, seriesId: Int = 0): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markMultipleRead(MarkMultipleDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark chapter read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markChapterUnread(chapterId: Int, seriesId: Int = 0): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markMultipleUnread(MarkMultipleDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark chapter unread"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSeriesRead(seriesId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markSeriesRead(MarkSeriesDto(seriesId = seriesId))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark as read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSeriesUnread(seriesId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markSeriesUnread(MarkSeriesDto(seriesId = seriesId))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark as unread"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

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

        // Case 1: Downloaded CBZ File
        if (localPath != null && localPath.endsWith(".cbz", ignoreCase = true)) {
            val cbzFile = File(localPath)
            if (cbzFile.exists() && cbzFile.length() > 0L) {
                try {
                    val cacheDir = File(context.cacheDir, "cbz_extracted/$chapterId").apply { mkdirs() }
                    val extractedFiles = mutableListOf<ReaderPage>()

                    ZipFile(cbzFile).use { zip ->
                        val entries = zip.entries().asSequence()
                            .filter { !it.isDirectory && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".webp", true)) }
                            .sortedWith { e1, e2 -> naturalCompare(e1.name, e2.name) }
                            .toList()

                        entries.forEachIndexed { idx, entry ->
                            val pageFile = File(cacheDir, "page_$idx.jpg")
                            if (!pageFile.exists() || pageFile.length() <= 0L) {
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(pageFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            if (pageFile.exists()) {
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
                } catch (_: Exception) {}
            }
        }

        // Case 2: Downloaded Image Pages Folder
        if (localPath != null) {
            val localFolder = File(localPath)
            if (localFolder.exists() && localFolder.isDirectory) {
                val imageFiles = localFolder.listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".webp", true)) }
                    ?.sortedWith { f1, f2 -> naturalCompare(f1.name, f2.name) }
                    ?: emptyList()

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

    suspend fun saveProgress(seriesId: Int, volumeId: Int, chapterId: Int, pageNum: Int, totalPages: Int = 0) = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext
        }

        val isFinished = totalPages > 0 && pageNum >= totalPages
        val nowIso = Instant.now().toString()
        try {
            api.setReaderProgress(
                ReaderProgressDto(
                    volumeId = volumeId,
                    chapterId = chapterId,
                    pageNum = pageNum,
                    seriesId = seriesId,
                    lastModifiedUtc = nowIso
                )
            )
            if (isFinished) {
                api.markMultipleRead(MarkMultipleDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
            }
        } catch (_: Exception) {
            // Local fallback
        }
    }

    suspend fun markChapterRead(chapterId: Int, seriesId: Int = 0) = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext
        }
        try {
            api.markMultipleRead(MarkMultipleDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
        } catch (_: Exception) {}
    }
}

class DownloadRepository(
    private val database: SigurvitaDatabase,
    private val context: Context
) {
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
    }

    suspend fun enqueueDownload(
        chapterId: Int,
        seriesId: Int,
        volumeId: Int,
        title: String,
        coverUrl: String?,
        totalPages: Int,
        libraryName: String = "Downloads",
        seriesName: String = "",
        chapterNumber: String = "",
        downloadFormat: DownloadFormat = DownloadFormat.CBZ
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
            .putString("downloadFormat", downloadFormat.name)
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
            while (currentDir != null && currentDir.exists()) {
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
        val localPath = task?.localPath ?: database.chapterDao().getChapterById(chapterId)?.localPath
        if (localPath != null) {
            val file = File(localPath)
            if (file.exists()) {
                file.deleteRecursively()
            }
            cleanupEmptyDirectories(file)
        }
        database.downloadDao().deleteDownloadTask(chapterId)
        database.chapterDao().updateDownloadStatus(chapterId, false, null)
    }
}
