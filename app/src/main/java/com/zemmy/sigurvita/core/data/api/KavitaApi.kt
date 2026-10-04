package com.zemmy.sigurvita.core.data.api

import com.zemmy.sigurvita.core.data.api.dto.ChapterDto
import com.zemmy.sigurvita.core.data.api.dto.FilterV2Dto
import com.zemmy.sigurvita.core.data.api.dto.GroupedSeriesDto
import com.zemmy.sigurvita.core.data.api.dto.LibraryDto
import com.zemmy.sigurvita.core.data.api.dto.MarkReadDto
import com.zemmy.sigurvita.core.data.api.dto.MarkVolumesReadDto
import com.zemmy.sigurvita.core.data.api.dto.ProgressDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListDto
import com.zemmy.sigurvita.core.data.api.dto.ReadingListItemDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesDetailDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto
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
    ): Response<UserDto>

    @GET("api/Account")
    suspend fun getAccount(): Response<UserDto>

    @GET("api/Library/libraries")
    suspend fun getLibraries(): Response<List<LibraryDto>>

    @POST("api/Series/v2")
    suspend fun getSeriesV2(
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
    ): Response<List<GroupedSeriesDto>>

    @POST("api/Series/recently-added-v2")
    suspend fun getRecentlyAddedSeries(
        @Body filter: FilterV2Dto = FilterV2Dto(),
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 20
    ): Response<List<SeriesDto>>

    @GET("api/Series/series-detail")
    suspend fun getSeriesDetail(
        @Query("seriesId") seriesId: Int
    ): Response<SeriesDetailDto>

    @GET("api/Series/metadata")
    suspend fun getSeriesMetadata(
        @Query("seriesId") seriesId: Int
    ): Response<SeriesMetadataDto>

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
        @Query("includePromoted") includePromoted: Boolean = true,
        @Query("sortByLastModified") sortByLastModified: Boolean = true,
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 100
    ): Response<List<ReadingListDto>>

    @GET("api/ReadingList/lists-for-series")
    suspend fun getReadingListsForSeries(
        @Query("seriesId") seriesId: Int
    ): Response<List<ReadingListDto>>

    @GET("api/ReadingList/lists-for-chapter")
    suspend fun getReadingListsForChapter(
        @Query("chapterId") chapterId: Int
    ): Response<List<ReadingListDto>>

    @GET("api/ReadingList/items")
    suspend fun getReadingListItems(
        @Query("readingListId") readingListId: Int
    ): Response<List<ReadingListItemDto>>

    @GET("api/Reader/get-progress")
    suspend fun getReaderProgress(
        @Query("chapterId") chapterId: Int
    ): Response<ProgressDto>

    @GET("api/Reader/continue-point")
    suspend fun getContinuePoint(
        @Query("seriesId") seriesId: Int
    ): Response<ChapterDto>

    @POST("api/Reader/progress")
    suspend fun setReaderProgress(
        @Body body: ProgressDto
    ): Response<Unit>

    @POST("api/Reader/mark-multiple-read")
    suspend fun markMultipleRead(
        @Body body: MarkVolumesReadDto
    ): Response<Unit>

    @POST("api/Reader/mark-multiple-unread")
    suspend fun markMultipleUnread(
        @Body body: MarkVolumesReadDto
    ): Response<Unit>

    @POST("api/Reader/mark-read")
    suspend fun markSeriesRead(
        @Body body: MarkReadDto
    ): Response<Unit>

    @POST("api/Reader/mark-unread")
    suspend fun markSeriesUnread(
        @Body body: MarkReadDto
    ): Response<Unit>
}
