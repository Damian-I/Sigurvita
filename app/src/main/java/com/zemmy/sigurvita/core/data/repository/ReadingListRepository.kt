package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.ReadingListDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListItemDto
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.ReadingListItem
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import com.zemmy.sigurvita.core.util.getChapterCoverUrl
import com.zemmy.sigurvita.core.util.getSeriesCoverUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ReadingListRepository(
    private val api: KavitaApi,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val requestLimiter = Semaphore(4)
    suspend fun getReadingLists(onlyIds: Set<Int>? = null): Result<List<ReadingList>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var dtoList: List<ReadingListDto>? = null

            val resPost = api.getReadingLists()
            if (resPost.isSuccessful && !resPost.body().isNullOrEmpty()) {
                dtoList = resPost.body()
            }

            val validDtos = dtoList
                ?.filter { it.id > 0 && !it.title.isNullOrBlank() && (onlyIds == null || it.id in onlyIds) }
                ?: emptyList()

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

    /** Lists without progress: one request, no per-list item calls. Totals stay 0. Used to find the "next" list. */
    suspend fun getReadingListSummaries(): Result<List<ReadingList>> = withContext(Dispatchers.IO) {
        try {
            val res = api.getReadingLists()
            val dtos = if (res.isSuccessful) res.body().orEmpty() else emptyList()
            Result.success(
                dtos.filter { it.id > 0 && !it.title.isNullOrBlank() }.map { dto ->
                    ReadingList(
                        id = dto.id,
                        title = dto.title ?: "",
                        summary = dto.summary,
                        itemCount = if (dto.itemCount > 0) dto.itemCount else 0,
                        coverUrl = null,
                        promoted = dto.promoted,
                        totalPages = 0,
                        pagesRead = 0,
                        issuesRead = 0
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /**
     * @param resolveDetails false = everything comes straight from the list response (a single request).
     * Use it when only navigation data is needed (previous/next chapter, preloading, "which lists contain
     * this chapter"). true also fetches each chapter (4 at a time) for its display title and page counts,
     * for the list screens. Series and library names are provided by the server in the list response.
     */
    suspend fun getReadingListItems(
        readingListId: Int,
        resolveDetails: Boolean = true
    ): Result<List<ReadingListItem>> = withContext(Dispatchers.IO) {
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
                    if (resolveDetails && dto.chapterId > 0) {
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
                    }

                    if (!resolveDetails || resolvedTitle.isBlank() || resolvedTitle.startsWith("Issue", ignoreCase = true)) {
                        resolvedTitle = formatChapterDisplayTitle(dto.title ?: "", "")
                    }

                    val coverUrl = if (dto.chapterId > 0) {
                        config.getChapterCoverUrl(dto.chapterId)
                    } else {
                        config.getSeriesCoverUrl(dto.seriesId)
                    }

                    ReadingListItem(
                        id = dto.id,
                        readingListId = dto.readingListId,
                        seriesId = dto.seriesId,
                        seriesName = dto.seriesName ?: "",
                        libraryName = dto.libraryName?.takeIf { it.isNotBlank() },
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
                        val itemsRes = getReadingListItems(listId, resolveDetails = false)
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
