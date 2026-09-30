package com.zemmy.sigurvita.core.data.api

import com.zemmy.sigurvita.core.data.api.dto.ChapterDto
import com.zemmy.sigurvita.core.data.api.dto.FilterV2Dto
import com.zemmy.sigurvita.core.data.api.dto.LibraryDto
import com.zemmy.sigurvita.core.data.api.dto.MarkMultipleDto
import com.zemmy.sigurvita.core.data.api.dto.MarkSeriesDto
import com.zemmy.sigurvita.core.data.api.dto.ReaderProgressDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListItemDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesDetailDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesDto
import com.zemmy.sigurvita.core.data.api.dto.TokenResponseDto
import com.zemmy.sigurvita.core.data.api.dto.UserDto
import com.zemmy.sigurvita.core.data.api.dto.VolumeDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface KavitaApi {

    @POST("api/Plugin/authenticate")
    suspend fun authenticatePlugin(
        @Query("apiKey") apiKey: String,
        @Query("pluginName") pluginName: String = "Sigurvita"
    ): Response<TokenResponseDto>

    @GET("api/Account")
    suspend fun getAccount(): Response<UserDto>

    @GET("api/Library")
    suspend fun getLibraries(): Response<List<LibraryDto>>

    @GET("api/library/libraries")
    suspend fun getLibrariesFilter(): Response<List<LibraryDto>>

    @POST("api/Series/v2")
    suspend fun getSeriesV2(
        @Query("libraryId") libraryId: Int = 0,
        @Body filter: FilterV2Dto = FilterV2Dto(),
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 5000
    ): Response<List<SeriesDto>>

    @POST("api/Series/on-deck")
    suspend fun getOnDeckSeries(
        @Query("libraryId") libraryId: Int = 0,
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 20
    ): Response<List<SeriesDto>>

    @POST("api/Series/recently-updated-series")
    suspend fun getRecentlyUpdatedSeries(
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 20
    ): Response<List<SeriesDto>>

    @GET("api/Series/series-detail")
    suspend fun getSeriesDetail(
        @Query("seriesId") seriesId: Int
    ): Response<SeriesDetailDto>

    @GET("api/Series/{seriesId}")
    suspend fun getSeriesById(
        @Path("seriesId") seriesId: Int
    ): Response<SeriesDto>

    @GET("api/Series/volumes")
    suspend fun getSeriesVolumes(
        @Query("seriesId") seriesId: Int
    ): Response<List<VolumeDto>>

    @GET("api/Series/chapter")
    suspend fun getChapterById(
        @Query("chapterId") chapterId: Int
    ): Response<ChapterDto>

    @POST("api/ReadingList/lists")
    suspend fun getReadingLists(
        @Body filter: FilterV2Dto = FilterV2Dto(),
        @Query("includePromoted") includePromoted: Boolean = true,
        @Query("sortByLastModified") sortByLastModified: Boolean = true,
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 100
    ): Response<List<ReadingListDto>>

    @GET("api/ReadingList/lists")
    suspend fun getReadingListsGet(
        @Query("includePromoted") includePromoted: Boolean = true,
        @Query("sortByLastModified") sortByLastModified: Boolean = true,
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 100
    ): Response<List<ReadingListDto>>

    @GET("api/ReadingList")
    suspend fun getReadingListsAll(): Response<List<ReadingListDto>>

    @GET("api/ReadingList/{id}")
    suspend fun getReadingListItems(
        @Path("id") readingListId: Int
    ): Response<List<ReadingListItemDto>>

    @GET("api/ReadingList/items")
    suspend fun getReadingListItemsQuery(
        @Query("userReadingListId") readingListId: Int
    ): Response<List<ReadingListItemDto>>

    @GET("api/ReadingList/items")
    suspend fun getReadingListItemsReadingListId(
        @Query("readingListId") readingListId: Int
    ): Response<List<ReadingListItemDto>>

    @GET("api/Reader/get-progress")
    suspend fun getReaderProgress(
        @Query("chapterId") chapterId: Int
    ): Response<ReaderProgressDto>

    @POST("api/reader/progress")
    suspend fun setReaderProgress(
        @Body body: ReaderProgressDto
    ): Response<Unit>

    @POST("api/reader/mark-multiple-read")
    suspend fun markMultipleRead(
        @Body body: MarkMultipleDto
    ): Response<Unit>

    @POST("api/reader/mark-multiple-unread")
    suspend fun markMultipleUnread(
        @Body body: MarkMultipleDto
    ): Response<Unit>

    @POST("api/reader/mark-read")
    suspend fun markSeriesRead(
        @Body body: MarkSeriesDto
    ): Response<Unit>

    @POST("api/reader/mark-unread")
    suspend fun markSeriesUnread(
        @Body body: MarkSeriesDto
    ): Response<Unit>
}
