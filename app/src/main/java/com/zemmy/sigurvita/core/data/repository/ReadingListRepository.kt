package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.ReadingListDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListItemDto
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ReadingListItem
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import com.zemmy.sigurvita.core.util.getChapterCoverUrl
import com.zemmy.sigurvita.core.util.getSeriesCoverUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ReadingListRepository(
    private val api: KavitaApi,
    private val database: SigurvitaDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val requestLimiter = Semaphore(4)
    suspend fun getReadingLists(): Result<List<ReadingList>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList: List<ReadingListDto>? = null

            val resPost = api.getReadingLists()
            if (resPost.isSuccessful && !resPost.body().isNullOrEmpty()) {
                dtoList = resPost.body()
            }

            val validDtos = dtoList?.filter { it.id > 0 && !it.title.isNullOrBlank() } ?: emptyList()

            val listsDeferred = validDtos.map { dto ->
                async {
                    var totalPages = 0
                    var pagesRead = 0
                    var issuesRead = 0

                    try {
                        val itemsRes = requestLimiter.withPermit { api.getReadingListItems(dto.id) }
                        if (itemsRes.isSuccessful && !itemsRes.body().isNullOrEmpty()) {
                            val items = itemsRes.body()!!
                            for (item in items) {
                                val itemPages = if (item.pagesTotal > 0) item.pagesTotal else item.pages
                                val itemPagesRead = item.pagesRead
                                totalPages += itemPages
                                pagesRead += itemPagesRead
                                if (itemPages in 1..itemPagesRead) {
                                    issuesRead++
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}

                    ReadingList(
                        id = dto.id,
                        title = dto.title ?: "",
                        summary = dto.summary,
                        itemCount = if (dto.itemCount > 0) dto.itemCount else 0,
                        coverUrl = dto.coverImage?.let { "${config.serverUrl}/$it" },
                        promoted = dto.promoted,
                        totalPages = totalPages,
                        pagesRead = pagesRead,
                        issuesRead = issuesRead
                    )
                }
            }

            val lists = listsDeferred.awaitAll()
            Result.success(lists)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private data class SeriesInfo(val name: String, val libraryId: Int)

    /**
     * @param resolveDetails false = ids, page counts and read state come straight from the list response
     * (a single request). Use it when only navigation data is needed (previous/next chapter, preloading,
     * "which lists contain this chapter"). true also fetches each chapter for its display title and resolves
     * series and library names, once per distinct series, for the list screens.
     */
    suspend fun getReadingListItems(
        readingListId: Int,
        resolveDetails: Boolean = true
    ): Result<List<ReadingListItem>> = withContext(Dispatchers.IO) {
        val ioScope = this
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

            if (!resolveDetails) {
                val lightItems = validDtos.map { dto ->
                    val pages = if (dto.pagesTotal > 0) dto.pagesTotal else dto.pages
                    ReadingListItem(
                        id = dto.id,
                        readingListId = dto.readingListId,
                        seriesId = dto.seriesId,
                        seriesName = dto.seriesName ?: "",
                        libraryName = null,
                        volumeId = dto.volumeId,
                        chapterId = dto.chapterId,
                        title = formatChapterDisplayTitle(dto.title ?: "", ""),
                        coverUrl = if (dto.chapterId > 0) {
                            config.getChapterCoverUrl(dto.chapterId)
                        } else {
                            config.getSeriesCoverUrl(dto.seriesId)
                        },
                        pages = pages,
                        pagesRead = dto.pagesRead,
                        isRead = pages in 1..dto.pagesRead
                    )
                }
                return@withContext Result.success(lightItems)
            }

            // One libraries request per call instead of one per item.
            val libraryNames = async {
                val names = HashMap<Int, String>()
                try {
                    api.getLibraries().body()?.forEach { lib -> lib.name?.let { names[lib.id] = it } }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                names
            }

            suspend fun loadSeriesInfo(seriesId: Int): SeriesInfo {
                val cached = try {
                    database.seriesDao().getSeriesById(seriesId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (cached != null && cached.libraryId > 0) return SeriesInfo(cached.name, cached.libraryId)
                return try {
                    val sDto = api.getSeriesById(seriesId).body()
                    SeriesInfo(
                        name = cached?.name?.takeIf { it.isNotBlank() } ?: sDto?.name ?: "",
                        libraryId = sDto?.libraryId ?: 0
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    SeriesInfo(cached?.name ?: "", 0)
                }
            }

            // One series lookup per distinct series instead of one (or two) per item.
            val seriesInfo = ConcurrentHashMap<Int, Deferred<SeriesInfo>>()

            val itemsDeferred = validDtos.map { dto ->
                async {
                    var pages = if (dto.pagesTotal > 0) dto.pagesTotal else dto.pages
                    var pagesRead = dto.pagesRead
                    var isRead = pages in 1..pagesRead

                    var resolvedTitle = dto.title ?: ""
                    if (dto.chapterId > 0) {
                        try {
                            val cRes = requestLimiter.withPermit { api.getChapterById(dto.chapterId) }
                            if (cRes.isSuccessful && cRes.body() != null) {
                                val cBody = cRes.body()!!
                                if (pages <= 0) pages = cBody.pages
                                if (pagesRead <= 0) pagesRead = cBody.pagesRead
                                isRead = isRead || (cBody.pages > 0 && cBody.pagesRead >= cBody.pages)
                                resolvedTitle = formatChapterDisplayTitle(cBody.titleName ?: cBody.title ?: "", cBody.number ?: "")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                    } else {
                        isRead = pages in 1..pagesRead
                    }

                    if (resolvedTitle.isBlank() || resolvedTitle.startsWith("Issue", ignoreCase = true)) {
                        resolvedTitle = formatChapterDisplayTitle(dto.title ?: "", "")
                    }

                    val coverUrl = if (dto.chapterId > 0) {
                        config.getChapterCoverUrl(dto.chapterId)
                    } else {
                        config.getSeriesCoverUrl(dto.seriesId)
                    }

                    var resolvedSeriesName = dto.seriesName ?: ""
                    var resolvedLibraryName: String? = null
                    if (dto.seriesId > 0) {
                        val info = seriesInfo.computeIfAbsent(dto.seriesId) {
                            ioScope.async { loadSeriesInfo(dto.seriesId) }
                        }.await()
                        if (resolvedSeriesName.isBlank()) resolvedSeriesName = info.name
                        if (info.libraryId > 0) resolvedLibraryName = libraryNames.await()[info.libraryId]
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
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        return@withContext map
    }
}
