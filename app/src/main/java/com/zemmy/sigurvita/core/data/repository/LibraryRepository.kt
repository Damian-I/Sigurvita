package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.FilterStatementDto
import com.zemmy.sigurvita.core.data.api.dto.FilterV2Dto
import com.zemmy.sigurvita.core.data.api.dto.LibraryDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesDto
import com.zemmy.sigurvita.core.data.api.dto.SortOptionsDto
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.util.getSeriesCoverUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

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
        } catch (e: CancellationException) {
            throw e
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
                totalIssues = entity.totalIssues,
                issuesRead = entity.issuesRead,
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
                val cachedSeries = database.seriesDao().getSeriesById(seriesId)
                Series(
                    id = seriesId,
                    libraryId = if (libraryId > 0) libraryId else 1,
                    name = first.seriesName.ifBlank { first.title },
                    coverUrl = first.coverUrl,
                    totalPages = tasks.sumOf { it.totalPages },
                    pagesRead = cachedSeries?.pagesRead ?: 0
                )
            }
    }

    private val detailLimiter = Semaphore(4)

    private suspend fun resolveSeriesIssueCounts(seriesDtos: List<SeriesDto>): List<Series> = coroutineScope {
        if (seriesDtos.isEmpty()) return@coroutineScope emptyList()
        val config = preferencesRepository.serverConfigFlow.first()
        val ids = seriesDtos.map { it.id }
        val cachedMap = database.seriesDao().getSeriesByIds(ids).associateBy { it.id }

        val deferreds = seriesDtos.map { dto ->
            async {
                val cached = cachedMap[dto.id]
                var totalIssues = cached?.totalIssues ?: 0
                var issuesRead = cached?.issuesRead ?: 0

                if (totalIssues == 0) {
                    try {
                        detailLimiter.acquire()
                        try {
                            val detailRes = api.getSeriesDetail(dto.id)
                            if (detailRes.isSuccessful && detailRes.body() != null) {
                                val detail = detailRes.body()!!
                                totalIssues = detail.totalCount
                                issuesRead = (detail.totalCount - detail.unreadCount).coerceAtLeast(0)
                            }
                        } finally {
                            detailLimiter.release()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }

                Series(
                    id = dto.id,
                    libraryId = dto.libraryId,
                    name = dto.name,
                    localizedName = dto.localizedName,
                    coverUrl = config.getSeriesCoverUrl(dto.id),
                    summary = dto.summary,
                    pagesRead = dto.pagesRead,
                    totalPages = dto.pages,
                    totalIssues = totalIssues,
                    issuesRead = issuesRead,
                    userRating = dto.userRating
                )
            }
        }
        val seriesList = deferreds.awaitAll()

        if (seriesList.isNotEmpty()) {
            val toInsert = seriesList.map {
                CachedSeriesEntity(
                    id = it.id,
                    libraryId = it.libraryId,
                    name = it.name,
                    localizedName = it.localizedName,
                    coverUrl = it.coverUrl,
                    summary = it.summary,
                    pagesRead = it.pagesRead,
                    totalPages = it.totalPages,
                    totalIssues = it.totalIssues,
                    issuesRead = it.issuesRead,
                    isCompleted = it.isCompleted,
                    userRating = it.userRating
                )
            }.filter { entity ->
                val cached = cachedMap[entity.id]
                cached == null || cached != entity
            }

            if (toInsert.isNotEmpty()) {
                database.seriesDao().insertSeries(toInsert)
            }
        }
        seriesList
    }

    suspend fun getOnDeckSeries(): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val onDeckRes = api.getOnDeckSeries()
            var dtoList = if (onDeckRes.isSuccessful) onDeckRes.body() else null

            if (dtoList.isNullOrEmpty()) {
                val fallbackRes = api.getSeriesV2(filter = FilterV2Dto(), pageNumber = 1, pageSize = 20)
                if (fallbackRes.isSuccessful) {
                    dtoList = fallbackRes.body()
                }
            }

            val validDtos = dtoList?.filter { it.id > 0 && it.name.isNotBlank() } ?: emptyList()
            val seriesList = resolveSeriesIssueCounts(validDtos)

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
                val cachedMap = database.seriesDao().getAllSeriesList().associateBy { it.id }
                groupedRes.body()!!.filter { it.seriesId > 0 && !it.seriesName.isNullOrBlank() }.map { dto ->
                    val cached = cachedMap[dto.seriesId]
                    Series(
                        id = dto.seriesId,
                        libraryId = dto.libraryId,
                        name = dto.seriesName ?: "",
                        localizedName = dto.localizedSeriesName,
                        coverUrl = config.getSeriesCoverUrl(dto.seriesId),
                        pagesRead = cached?.pagesRead ?: 0,
                        totalPages = cached?.totalPages ?: 0,
                        totalIssues = if (dto.count > 0) dto.count else cached?.totalIssues ?: 0,
                        issuesRead = cached?.issuesRead ?: 0
                    )
                }
            } else {
                val fallbackList = api.getSeriesV2(filter = FilterV2Dto(), pageNumber = 1, pageSize = 20).body() ?: emptyList()
                resolveSeriesIssueCounts(fallbackList.filter { it.id > 0 && it.name.isNotBlank() })
            }

            Result.success(seriesList)
        } catch (e: Exception) {
            val fallback = getOfflineSeriesFallback()
            if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e)
        }
    }

    suspend fun getNewlyAddedSeries(): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
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

            val validDtos = dtoList?.filter { it.id > 0 && it.name.isNotBlank() } ?: emptyList()
            val seriesList = resolveSeriesIssueCounts(validDtos)

            Result.success(seriesList)
        } catch (e: Exception) {
            val fallback = getOfflineSeriesFallback()
            if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e)
        }
    }

    suspend fun getSeriesForLibrary(libraryId: Int): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
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

            val validDtos = dtoList?.filter { it.id > 0 && it.name.isNotBlank() } ?: emptyList()
            val seriesList = resolveSeriesIssueCounts(validDtos)

            Result.success(seriesList)
        } catch (e: Exception) {
            val fallback = getOfflineSeriesFallback(libraryId)
            if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e)
        }
    }
}
