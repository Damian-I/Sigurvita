package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import android.util.Log
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
import com.zemmy.sigurvita.core.data.api.dto.MarkReadDto
import com.zemmy.sigurvita.core.data.api.dto.MarkVolumesReadDto
import com.zemmy.sigurvita.core.data.api.dto.ProgressDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListItemDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesDto
import com.zemmy.sigurvita.core.data.api.dto.SortOptionsDto
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
import com.zemmy.sigurvita.core.util.extractChapterNumber
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import com.zemmy.sigurvita.core.util.naturalCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            val cachedSeries = database.seriesDao().getAllSeriesList()
            if (cachedSeries.isNotEmpty()) {
                val distinctLibs = cachedSeries.groupBy { it.libraryId }.map { (libId, _) ->
                    Library(id = libId, name = if (libId == 0) "All" else "Library $libId", type = 1)
                }
                Result.success(distinctLibs)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun getOfflineSeriesFallback(libraryId: Int = 0): List<Series> {
        val cachedList = database.seriesDao().getAllSeriesList().map { entity ->
            Series(
                id = entity.id,
                libraryId = entity.libraryId,
                name = entity.name,
                localizedName = entity.localizedName,
                coverUrl = entity.coverUrl,
                summary = entity.summary,
                pagesRead = entity.pagesRead,
                totalPages = entity.totalPages,
                isCompleted = entity.isCompleted,
                userRating = entity.userRating
            )
        }
        val filtered = if (libraryId > 0) cachedList.filter { it.libraryId == libraryId } else cachedList
        if (filtered.isNotEmpty()) return filtered

        // If Room cached series is empty, derive from downloaded tasks
        val downloadedTasks = database.downloadDao().getAllCompletedDownloads()
        return downloadedTasks.filter { it.seriesId > 0 }
            .groupBy { it.seriesId }
            .map { (seriesId, tasks) ->
                val first = tasks.first()
                Series(
                    id = seriesId,
                    libraryId = if (libraryId > 0) libraryId else 1,
                    name = first.seriesName.ifBlank { first.title },
                    coverUrl = first.coverUrl,
                    totalPages = tasks.sumOf { it.totalPages },
                    pagesRead = tasks.sumOf { it.downloadedPages }
                )
            }
    }

    suspend fun getOnDeckSeries(): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            val onDeckRes = api.getOnDeckSeries()
            var dtoList = if (onDeckRes.isSuccessful) onDeckRes.body() else null

            if (dtoList.isNullOrEmpty()) {
                val fallbackRes = api.getSeriesV2(filter = FilterV2Dto(), pageNumber = 1, pageSize = 20)
                if (fallbackRes.isSuccessful) {
                    dtoList = fallbackRes.body()
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
                    totalPages = dto.pages,
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
            val fallback = getOfflineSeriesFallback()
            if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e)
        }
    }

    suspend fun getRecentlyUpdatedSeries(): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            val groupedRes = api.getRecentlyUpdatedSeries()
            val seriesList = if (groupedRes.isSuccessful && !groupedRes.body().isNullOrEmpty()) {
                groupedRes.body()!!.filter { it.seriesId > 0 && !it.seriesName.isNullOrBlank() }.map { dto ->
                    Series(
                        id = dto.seriesId,
                        libraryId = dto.libraryId,
                        name = dto.seriesName ?: "",
                        localizedName = dto.localizedSeriesName,
                        coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.seriesId}&apiKey=${config.apiKey}"
                    )
                }
            } else {
                val fallbackList = api.getSeriesV2(filter = FilterV2Dto(), pageNumber = 1, pageSize = 20).body()
                fallbackList?.filter { it.id > 0 && it.name.isNotBlank() }?.map { dto ->
                    Series(
                        id = dto.id,
                        libraryId = dto.libraryId,
                        name = dto.name,
                        localizedName = dto.localizedName,
                        coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                        summary = dto.summary,
                        pagesRead = dto.pagesRead,
                        totalPages = dto.pages,
                        userRating = dto.userRating
                    )
                } ?: emptyList()
            }

            Result.success(seriesList)
        } catch (e: Exception) {
            val fallback = getOfflineSeriesFallback()
            if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e)
        }
    }

    suspend fun getNewlyAddedSeries(): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            val filter = FilterV2Dto(
                sortOptions = SortOptionsDto(sortField = 2, isAscending = false)
            )
            val res = api.getRecentlyAddedSeries(filter = filter)
            var dtoList = if (res.isSuccessful && !res.body().isNullOrEmpty()) res.body() else null

            if (dtoList.isNullOrEmpty()) {
                val fallbackRes = api.getSeriesV2(filter = filter, pageNumber = 1, pageSize = 20)
                if (fallbackRes.isSuccessful) {
                    dtoList = fallbackRes.body()
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
                    totalPages = dto.pages,
                    userRating = dto.userRating
                )
            } ?: emptyList()

            Result.success(seriesList)
        } catch (e: Exception) {
            val fallback = getOfflineSeriesFallback()
            if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e)
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
                    combination = 0
                )
                val resWithFilter = api.getSeriesV2(filter = filter, pageNumber = 1, pageSize = 5000)
                if (resWithFilter.isSuccessful && !resWithFilter.body().isNullOrEmpty()) {
                    dtoList = resWithFilter.body()
                } else {
                    val resQuery = api.getSeriesV2(filter = FilterV2Dto(), pageNumber = 1, pageSize = 5000)
                    if (resQuery.isSuccessful && !resQuery.body().isNullOrEmpty()) {
                        dtoList = resQuery.body()
                    }
                }
            } else {
                val resAll = api.getSeriesV2(filter = FilterV2Dto(), pageNumber = 1, pageSize = 5000)
                if (resAll.isSuccessful) {
                    dtoList = resAll.body()
                }
            }

            // Fallback: If dtoList is null, fetch all series and filter client-side by libraryId
            if (dtoList == null) {
                val resAll = api.getSeriesV2(filter = FilterV2Dto(), pageNumber = 1, pageSize = 5000)
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
                    totalPages = dto.pages,
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
            val fallback = getOfflineSeriesFallback(libraryId)
            if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e)
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

            val resPost = api.getReadingLists()
            if (resPost.isSuccessful && !resPost.body().isNullOrEmpty()) {
                dtoList = resPost.body()
            }

            val lists = dtoList?.filter { it.id > 0 && !it.title.isNullOrBlank() }?.map { dto ->
                ReadingList(
                    id = dto.id,
                    title = dto.title ?: "",
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

    suspend fun hasReadingListProgress(readingListId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = api.getReadingListItems(readingListId)
            val dtoList = if (res.isSuccessful) res.body() else null
            return@withContext dtoList?.any { it.pagesRead > 0 } == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getReadingListItems(readingListId: Int): Result<List<ReadingListItem>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList: List<ReadingListItemDto>? = null

            val res = api.getReadingListItems(readingListId)
            if (res.isSuccessful && !res.body().isNullOrEmpty()) {
                dtoList = res.body()
            }

            if (dtoList.isNullOrEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val validDtos = dtoList.filter { it.id > 0 }

            val itemsDeferred = validDtos.map { dto ->
                async {
                    var pages = if (dto.pagesTotal > 0) dto.pagesTotal else dto.pages
                    var pagesRead = dto.pagesRead
                    var isRead = pages > 0 && pagesRead >= pages

                    var resolvedTitle = dto.title ?: ""
                    if (dto.chapterId > 0) {
                        val cachedChapter = database.chapterDao().getChapterById(dto.chapterId)
                        if (cachedChapter != null) {
                            if (pages <= 0) pages = cachedChapter.pages
                            if (pagesRead <= 0) pagesRead = cachedChapter.pagesRead
                            isRead = isRead || cachedChapter.isRead
                            resolvedTitle = formatChapterDisplayTitle(cachedChapter.title, cachedChapter.chapterNumber)
                        } else {
                            try {
                                val cRes = api.getChapterById(dto.chapterId)
                                if (cRes.isSuccessful && cRes.body() != null) {
                                    val cBody = cRes.body()!!
                                    if (pages <= 0) pages = cBody.pages
                                    if (pagesRead <= 0) pagesRead = cBody.pagesRead
                                    isRead = isRead || (cBody.pages > 0 && cBody.pagesRead >= cBody.pages)
                                    resolvedTitle = formatChapterDisplayTitle(cBody.titleName ?: cBody.title ?: "", cBody.number ?: "")
                                }
                            } catch (_: Exception) {}
                        }
                    } else {
                        isRead = pages > 0 && pagesRead >= pages
                    }

                    if (resolvedTitle.isBlank() || resolvedTitle.startsWith("Issue", ignoreCase = true)) {
                        resolvedTitle = formatChapterDisplayTitle(dto.title ?: "", "")
                    }

                    val coverUrl = if (dto.chapterId > 0) {
                        "${config.serverUrl}/api/image/chapter-cover?chapterId=${dto.chapterId}&apiKey=${config.apiKey}"
                    } else {
                        "${config.serverUrl}/api/image/series-cover?seriesId=${dto.seriesId}&apiKey=${config.apiKey}"
                    }

                    val rawSeriesName = dto.seriesName ?: ""
                    var resolvedLibraryName: String? = null
                    val resolvedSeriesName = if (rawSeriesName.isNotBlank()) {
                        rawSeriesName
                    } else if (dto.seriesId > 0) {
                        val cachedSeries = database.seriesDao().getSeriesById(dto.seriesId)
                        if (cachedSeries != null && cachedSeries.name.isNotBlank()) {
                            cachedSeries.name
                        } else {
                            try {
                                val sRes = api.getSeriesById(dto.seriesId)
                                val sDto = sRes.body()
                                if (sDto != null && sDto.libraryId > 0) {
                                    val libRes = api.getLibraries()
                                    resolvedLibraryName = libRes.body()?.find { it.id == sDto.libraryId }?.name
                                }
                                sDto?.name ?: ""
                            } catch (_: Exception) { "" }
                        }
                    } else ""

                    if (resolvedLibraryName.isNullOrBlank() && dto.seriesId > 0) {
                        try {
                            val cachedSeries = database.seriesDao().getSeriesById(dto.seriesId)
                            if (cachedSeries != null && cachedSeries.libraryId > 0) {
                                val libRes = api.getLibraries()
                                resolvedLibraryName = libRes.body()?.find { it.id == cachedSeries.libraryId }?.name
                            } else {
                                val sRes = api.getSeriesById(dto.seriesId)
                                val sDto = sRes.body()
                                if (sDto != null && sDto.libraryId > 0) {
                                    val libRes = api.getLibraries()
                                    resolvedLibraryName = libRes.body()?.find { it.id == sDto.libraryId }?.name
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    ReadingListItem(
                        id = dto.id,
                        readingListId = dto.readingListId,
                        seriesId = dto.seriesId,
                        seriesName = resolvedSeriesName,
                        libraryName = resolvedLibraryName,
                        volumeId = dto.volumeId,
                        chapterId = dto.chapterId,
                        title = resolvedTitle,
                        coverUrl = coverUrl,
                        pages = pages,
                        pagesRead = pagesRead,
                        isRead = isRead
                    )
                }
            }

            val items = itemsDeferred.awaitAll()
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChapterReadingLists(seriesId: Int): Map<Int, List<Pair<Int, String>>> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<Int, MutableList<Pair<Int, String>>>()
        try {
            // First try direct fast API call for reading lists containing this series
            val directRes = api.getReadingListsForSeries(seriesId)
            var listsToFetch: List<Pair<Int, String>> = emptyList()

            if (directRes.isSuccessful && !directRes.body().isNullOrEmpty()) {
                listsToFetch = directRes.body()!!.filter { !it.title.isNullOrBlank() }.map { Pair(it.id, it.title!!) }
            } else {
                // Fallback if endpoint returns empty/unavailable
                val listsRes = getReadingLists()
                if (listsRes.isSuccess) {
                    listsToFetch = listsRes.getOrDefault(emptyList()).map { Pair(it.id, it.title) }
                }
            }

            if (listsToFetch.isNotEmpty()) {
                // Concurrently fetch items for the relevant lists in parallel
                val deferreds = listsToFetch.map { (listId, listTitle) ->
                    async {
                        val itemsRes = getReadingListItems(listId)
                        Pair(Pair(listId, listTitle), itemsRes.getOrDefault(emptyList()))
                    }
                }
                val results = deferreds.awaitAll()
                for ((listInfo, items) in results) {
                    val (listId, listTitle) = listInfo
                    for (item in items) {
                        if (item.seriesId == seriesId && item.chapterId != null && item.chapterId > 0) {
                            map.getOrPut(item.chapterId) { mutableListOf() }.add(Pair(listId, listTitle))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext map
    }
}

class SeriesRepository(
    private val api: KavitaApi,
    private val database: SigurvitaDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun getSeriesDetail(seriesId: Int): Result<Series> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var series: Series? = null

            val metadataRes = api.getSeriesMetadata(seriesId)
            val summary = if (metadataRes.isSuccessful) metadataRes.body()?.summary else null

            val resById = api.getSeriesById(seriesId)
            if (resById.isSuccessful && resById.body() != null) {
                val dto = resById.body()!!
                series = Series(
                    id = dto.id,
                    libraryId = dto.libraryId,
                    name = dto.name,
                    localizedName = dto.localizedName,
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                    summary = summary?.takeIf { it.isNotBlank() } ?: dto.summary,
                    pagesRead = dto.pagesRead,
                    totalPages = dto.pages,
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
                        summary = summary?.takeIf { it.isNotBlank() } ?: dto.summary,
                        pagesRead = dto.pagesRead,
                        totalPages = dto.pages,
                        isCompleted = dto.isCompleted
                    )
                }
            }

            if (series != null) {
                database.seriesDao().insertSeries(listOf(
                    CachedSeriesEntity(
                        id = series.id,
                        libraryId = series.libraryId,
                        name = series.name,
                        localizedName = series.localizedName,
                        coverUrl = series.coverUrl,
                        summary = series.summary,
                        pagesRead = series.pagesRead,
                        totalPages = series.totalPages,
                        isCompleted = series.isCompleted,
                        userRating = series.userRating
                    )
                ))
                Result.success(series)
            } else {
                val cached = database.seriesDao().getSeriesById(seriesId)
                if (cached != null) {
                    Result.success(
                        Series(
                            id = cached.id,
                            libraryId = cached.libraryId,
                            name = cached.name,
                            localizedName = cached.localizedName,
                            coverUrl = cached.coverUrl,
                            summary = cached.summary,
                            pagesRead = cached.pagesRead,
                            totalPages = cached.totalPages,
                            isCompleted = cached.isCompleted,
                            userRating = cached.userRating
                        )
                    )
                } else {
                    Result.failure(Exception("Series not found"))
                }
            }
        } catch (e: Exception) {
            val cached = database.seriesDao().getSeriesById(seriesId)
            if (cached != null) {
                Result.success(
                    Series(
                        id = cached.id,
                        libraryId = cached.libraryId,
                        name = cached.name,
                        localizedName = cached.localizedName,
                        coverUrl = cached.coverUrl,
                        summary = cached.summary,
                        pagesRead = cached.pagesRead,
                        totalPages = cached.totalPages,
                        isCompleted = cached.isCompleted,
                        userRating = cached.userRating
                    )
                )
            } else {
                val completedTasks = database.downloadDao().getCompletedDownloadsForSeries(seriesId)
                if (completedTasks.isNotEmpty()) {
                    val first = completedTasks.first()
                    Result.success(
                        Series(
                            id = seriesId,
                            libraryId = 1,
                            name = first.seriesName.ifBlank { first.title },
                            coverUrl = first.coverUrl,
                            totalPages = completedTasks.sumOf { it.totalPages },
                            pagesRead = completedTasks.sumOf { it.downloadedPages }
                        )
                    )
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    private fun buildVolumesFromDownloads(seriesId: Int, tasks: List<com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity>): List<Volume> {
        return tasks.groupBy { it.volumeId }.map { (volId, taskGroup) ->
            val chapters = taskGroup.map { task ->
                Chapter(
                    id = task.chapterId,
                    volumeId = task.volumeId,
                    seriesId = task.seriesId,
                    chapterNumber = task.chapterNumber.ifBlank { "1" },
                    title = task.title,
                    pages = task.totalPages,
                    pagesRead = task.downloadedPages,
                    isRead = false,
                    isDownloaded = true,
                    localPath = task.localPath,
                    coverUrl = task.coverUrl
                )
            }
            Volume(
                id = if (volId > 0) volId else 1,
                seriesId = seriesId,
                name = if (volId > 0) "Volume $volId" else "Downloaded Issues",
                number = volId.toFloat(),
                pagesRead = chapters.sumOf { it.pagesRead },
                totalPages = chapters.sumOf { it.pages },
                chapters = chapters
            )
        }
    }

    suspend fun getSeriesVolumes(seriesId: Int): Result<List<Volume>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            val res = api.getSeriesVolumes(seriesId)
            if (res.isSuccessful && res.body() != null) {
                val volumes = res.body()!!.map { vDto ->
                    val resolvedVolSeriesId = if (vDto.seriesId > 0) vDto.seriesId else seriesId
                    val volNumber = if (vDto.minNumber > 0f) vDto.minNumber else vDto.number.toFloat()
                    Volume(
                        id = vDto.id,
                        seriesId = resolvedVolSeriesId,
                        name = vDto.name ?: "",
                        number = volNumber,
                        pagesRead = vDto.pagesRead,
                        totalPages = vDto.pages,
                        chapters = vDto.chapters.map { cDto ->
                            val isChapterRead = cDto.pages > 0 && cDto.pagesRead >= cDto.pages
                            val titleNameVal = (cDto.titleName ?: "").trim()
                            val titleVal = (cDto.title ?: "").trim()
                            val numVal = (cDto.number ?: if (cDto.minNumber > 0f) cDto.minNumber.toString() else "").trim()

                            val resolvedNumber = extractChapterNumber(numVal, titleVal.ifBlank { titleNameVal })

                            val resolvedTitle = when {
                                titleNameVal.isNotBlank() && titleNameVal != resolvedNumber -> titleNameVal
                                titleVal.isNotBlank() && titleVal != resolvedNumber -> titleVal
                                titleNameVal.isNotBlank() -> titleNameVal
                                else -> titleVal
                            }
                            val mappedFiles = cDto.files?.map { fDto ->
                                com.zemmy.sigurvita.core.model.MangaFile(
                                    id = fDto.id,
                                    filePath = fDto.filePath,
                                    pages = fDto.pages,
                                    bytes = fDto.bytes,
                                    format = fDto.format,
                                    extension = fDto.extension
                                )
                            } ?: emptyList()

                            Chapter(
                                id = cDto.id,
                                volumeId = cDto.volumeId,
                                seriesId = resolvedVolSeriesId,
                                chapterNumber = resolvedNumber,
                                title = resolvedTitle,
                                summary = cDto.summary,
                                pages = cDto.pages,
                                pagesRead = cDto.pagesRead,
                                isRead = isChapterRead,
                                coverUrl = "${config.serverUrl}/api/image/chapter-cover?chapterId=${cDto.id}&apiKey=${config.apiKey}",
                                files = mappedFiles
                            )
                        }
                    )
                }
                Result.success(volumes)
            } else {
                val completedTasks = database.downloadDao().getCompletedDownloadsForSeries(seriesId)
                if (completedTasks.isNotEmpty()) {
                    Result.success(buildVolumesFromDownloads(seriesId, completedTasks))
                } else {
                    Result.failure(Exception("Failed to fetch volumes"))
                }
            }
        } catch (e: Exception) {
            val completedTasks = database.downloadDao().getCompletedDownloadsForSeries(seriesId)
            if (completedTasks.isNotEmpty()) {
                Result.success(buildVolumesFromDownloads(seriesId, completedTasks))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getSeriesMetadataDetails(seriesId: Int): Result<com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto> = withContext(Dispatchers.IO) {
        try {
            val res = api.getSeriesMetadata(seriesId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch series metadata"))
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
            val res = api.markMultipleRead(MarkVolumesReadDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
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
            val res = api.markMultipleUnread(MarkVolumesReadDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
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
            val res = api.markSeriesRead(MarkReadDto(seriesId = seriesId))
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
            val res = api.markSeriesUnread(MarkReadDto(seriesId = seriesId))
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
                                !entry.name.contains("__MACOSX") &&
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

        val isFinished = totalPages > 0 && pageNum >= totalPages
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

class DownloadRepository(
    private val database: SigurvitaDatabase,
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository = com.zemmy.sigurvita.SigurvitaApp.instance.userPreferencesRepository
) {
    val downloadedSeriesFlow: Flow<List<Series>> = database.downloadDao().getAllDownloads().map { entities ->
        entities.filter { it.status == DownloadStatus.COMPLETED && it.seriesId > 0 }
            .groupBy { it.seriesId }
            .map { (seriesId, tasks) ->
                val firstTask = tasks.first()
                val totalPages = tasks.sumOf { it.totalPages }
                val pagesRead = tasks.sumOf { it.downloadedPages }
                Series(
                    id = seriesId,
                    libraryId = 0,
                    name = firstTask.seriesName.ifBlank { firstTask.title },
                    coverUrl = firstTask.coverUrl,
                    totalPages = totalPages,
                    pagesRead = pagesRead
                )
            }
    }

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

    private suspend fun cleanupEmptyDirectories(file: File?) {
        if (file == null) return
        try {
            var currentDir: File? = if (file.isDirectory) file.parentFile else file.parentFile
            val settings = preferencesRepository.readerSettingsFlow.first()
            val rootPath = settings.downloadPath

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
        val localPath = task?.localPath ?: database.chapterDao().getChapterById(chapterId)?.localPath
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
        database.chapterDao().updateDownloadStatus(chapterId, false, null)
    }
}
