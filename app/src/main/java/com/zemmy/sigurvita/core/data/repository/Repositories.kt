package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.FilterStatementDto
import com.zemmy.sigurvita.core.data.api.dto.FilterV2Dto
import com.zemmy.sigurvita.core.data.api.dto.LibraryDto
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
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ReadingListItem
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

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

                ReadingListItem(
                    id = dto.id,
                    readingListId = dto.readingListId,
                    seriesId = dto.seriesId,
                    seriesName = dto.seriesName,
                    volumeId = dto.volumeId,
                    chapterId = dto.chapterId,
                    title = dto.title,
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.seriesId}&apiKey=${config.apiKey}",
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

    suspend fun markSeriesRead(seriesId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val res = api.markSeriesRead(seriesId)
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark as read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSeriesUnread(seriesId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val res = api.markSeriesUnread(seriesId)
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
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun getPages(chapterId: Int, totalPages: Int): List<ReaderPage> = withContext(Dispatchers.IO) {
        val config = preferencesRepository.serverConfigFlow.first()
        val settings = preferencesRepository.readerSettingsFlow.first()
        val chapterDir = if (settings.downloadPath.isNotBlank()) {
            File(settings.downloadPath, "chapters/$chapterId")
        } else {
            File(context.filesDir, "downloads/chapters/$chapterId")
        }

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
            val localFile = File(chapterDir, "page_$pageIdx.jpg")
            if (localFile.exists()) {
                ReaderPage(
                    index = pageIdx,
                    pageNumber = pageIdx + 1,
                    imageUrl = localFile.absolutePath,
                    isLocal = true
                )
            } else {
                ReaderPage(
                    index = pageIdx,
                    pageNumber = pageIdx + 1,
                    imageUrl = "${config.serverUrl}/api/Reader/image?chapterId=$chapterId&page=$pageIdx&apiKey=${config.apiKey}",
                    isLocal = false
                )
            }
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

    suspend fun saveProgress(seriesId: Int, volumeId: Int, chapterId: Int, pageNum: Int) = withContext(Dispatchers.IO) {
        try {
            api.setReaderProgress(
                ReaderProgressDto(
                    seriesId = seriesId,
                    volumeId = volumeId,
                    chapterId = chapterId,
                    pageNum = pageNum
                )
            )
        } catch (_: Exception) {
            // Local fallback / optimistic update
        }
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
                title = entity.title,
                coverUrl = entity.coverUrl,
                totalPages = entity.totalPages,
                downloadedPages = entity.downloadedPages,
                status = entity.status,
                errorMessage = entity.errorMessage
            )
        }
    }

    suspend fun enqueueDownload(
        chapterId: Int,
        seriesId: Int,
        volumeId: Int,
        title: String,
        coverUrl: String?,
        totalPages: Int
    ) = withContext(Dispatchers.IO) {
        val task = DownloadTaskEntity(
            chapterId = chapterId,
            seriesId = seriesId,
            volumeId = volumeId,
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
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("download_$chapterId")
            .build()

        WorkManager.getInstance(context).enqueue(downloadWork)
    }

    suspend fun cancelDownload(chapterId: Int) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$chapterId")
        database.downloadDao().deleteDownloadTask(chapterId)
    }
}
