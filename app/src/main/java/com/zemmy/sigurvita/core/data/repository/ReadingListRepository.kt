package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.ReadingListDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListItemDto
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ReadingListItem
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
                    var isRead = pages in 1..pagesRead

                    var resolvedTitle = dto.title ?: ""
                    if (dto.chapterId > 0) {
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
                    } else {
                        isRead = pages in 1..pagesRead
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
