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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
