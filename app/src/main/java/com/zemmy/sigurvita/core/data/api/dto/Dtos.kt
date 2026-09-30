package com.zemmy.sigurvita.core.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponseDto(
    val token: String? = null,
    val refreshToken: String? = null,
    val username: String? = null
)

@Serializable
data class UserDto(
    val id: Int = 0,
    val username: String = "",
    val email: String? = null,
    val apiKey: String? = null
)

@Serializable
data class LibraryDto(
    val id: Int = 0,
    val name: String = "",
    val type: Int = 0,
    val coverImage: String? = null,
    val folder: String? = null
)

@Serializable
data class SeriesDto(
    val id: Int = 0,
    val libraryId: Int = 0,
    val name: String = "",
    val localizedName: String? = null,
    val coverImage: String? = null,
    val summary: String? = null,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val format: Int = 0,
    val userRating: Float = 0f
)

@Serializable
data class SeriesDetailDto(
    val id: Int = 0,
    val name: String = "",
    val summary: String? = null,
    val coverImage: String? = null,
    val libraryId: Int = 0,
    val isCompleted: Boolean = false,
    val totalPages: Int = 0,
    val pagesRead: Int = 0
)

@Serializable
data class VolumeDto(
    val id: Int = 0,
    val seriesId: Int = 0,
    val name: String = "",
    val number: Float = 0f,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val chapters: List<ChapterDto> = emptyList()
)

@Serializable
data class ChapterDto(
    val id: Int = 0,
    val volumeId: Int = 0,
    val seriesId: Int = 0,
    val number: String = "",
    val title: String = "",
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isRead: Boolean = false,
    val coverImage: String? = null
)

@Serializable
data class ReadingListDto(
    val id: Int = 0,
    val title: String = "",
    val summary: String? = null,
    val itemCount: Int = 0,
    val coverImage: String? = null,
    val promoted: Boolean = false
)

@Serializable
data class ReadingListItemDto(
    val id: Int = 0,
    val readingListId: Int = 0,
    val seriesId: Int = 0,
    val seriesName: String = "",
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val title: String = "",
    val coverImage: String? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isRead: Boolean = false
)

@Serializable
data class FilterV2Dto(
    val statements: List<FilterStatementDto> = emptyList(),
    val combination: Int = 0,
    val sortOptions: SortOptionsDto? = null
)

@Serializable
data class FilterStatementDto(
    val field: Int = 0,
    val comparison: Int = 0,
    val value: String = ""
)

@Serializable
data class SortOptionsDto(
    val sortField: Int = 0,
    val isAscending: Boolean = true
)

@Serializable
data class ReaderProgressDto(
    val volumeId: Int = 0,
    val chapterId: Int = 0,
    val pageNum: Int = 0,
    val seriesId: Int = 0,
    val libraryId: Int = 0,
    val bookScrollId: String? = null,
    val lastModifiedUtc: String? = null
)

@Serializable
data class MarkSeriesDto(
    val seriesId: Int = 0
)

@Serializable
data class MarkMultipleDto(
    val seriesId: Int = 0,
    val chapterIds: List<Int> = emptyList()
)
