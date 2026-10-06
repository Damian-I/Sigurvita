package com.zemmy.sigurvita.core.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int = 0,
    val username: String = "",
    val email: String? = null,
    val apiKey: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val roles: List<String>? = null,
    val kavitaVersion: String? = null
)

@Serializable
data class GroupedSeriesDto(
    val seriesId: Int = 0,
    val seriesName: String? = null,
    val localizedSeriesName: String? = null,
    val libraryId: Int = 0,
    val libraryType: Int = 0,
    val id: Int = 0,
    val format: Int = 0,
    val count: Int = 0,
    val chapterId: Int = 0,
    val volumeId: Int = 0
)

@Serializable
data class LibraryDto(
    val id: Int = 0,
    val name: String = "",
    val type: Int = 0,
    val coverImage: String? = null,
    val folders: List<String>? = null
)

@Serializable
data class SeriesDto(
    val id: Int = 0,
    val libraryId: Int = 0,
    val name: String = "",
    val originalName: String? = null,
    val localizedName: String? = null,
    val sortName: String? = null,
    val coverImage: String? = null,
    val summary: String? = null,
    val pagesRead: Int = 0,
    val pages: Int = 0,
    val format: Int = 0,
    val userRating: Float = 0f,
    val libraryName: String? = null,
    val isStandAlone: Boolean = false
)

@Serializable
data class SeriesDetailDto(
    val id: Int = 0,
    val name: String = "",
    val summary: String? = null,
    val coverImage: String? = null,
    val libraryId: Int = 0,
    val isCompleted: Boolean = false,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val libraryType: Int = 0,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
    val chapters: List<ChapterDto>? = null,
    val volumes: List<VolumeDto>? = null,
    val specials: List<ChapterDto>? = null
)

@Serializable
data class VolumeDto(
    val id: Int = 0,
    val seriesId: Int = 0,
    val name: String? = null,
    val number: Int = 0,
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
    val pagesRead: Int = 0,
    val pages: Int = 0,
    val chapters: List<ChapterDto> = emptyList()
)

@Serializable
data class MangaFileDto(
    val id: Int = 0,
    val filePath: String? = null,
    val pages: Int = 0,
    val bytes: Long = 0L,
    val format: Int = 0,
    val extension: String? = null
)

@Serializable
data class ChapterDto(
    val id: Int = 0,
    val volumeId: Int = 0,
    val range: String? = null,
    val number: String? = null,
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
    val sortOrder: Float = 0f,
    val title: String? = null,
    val titleName: String? = null,
    val summary: String? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isSpecial: Boolean = false,
    val files: List<MangaFileDto>? = null
)

@Serializable
data class ReadingListDto(
    val id: Int = 0,
    val title: String? = null,
    val summary: String? = null,
    val itemCount: Int = 0,
    val coverImage: String? = null,
    val promoted: Boolean = false,
    val startingYear: Int = 0,
    val endingYear: Int = 0,
    val ageRating: Int = 0,
    val ownerUserName: String? = null
)

@Serializable
data class ReadingListItemDto(
    val id: Int = 0,
    val readingListId: Int = 0,
    val seriesId: Int = 0,
    val seriesName: String? = null,
    val volumeId: Int = 0,
    val chapterId: Int = 0,
    val title: String? = null,
    val chapterTitleName: String? = null,
    val chapterNumber: String? = null,
    val volumeNumber: String? = null,
    val pages: Int = 0,
    val pagesTotal: Int = 0,
    val pagesRead: Int = 0,
    val order: Int = 0,
    val libraryId: Int = 0,
    val libraryName: String? = null
)

@Serializable
data class FilterV2Dto(
    val id: Int = 0,
    val name: String? = null,
    val statements: List<FilterStatementDto> = emptyList(),
    val combination: Int = 0,
    val sortOptions: SortOptionsDto? = null,
    val limitTo: Int = 0
)

@Serializable
data class FilterStatementDto(
    val field: Int = 0,
    val comparison: Int = 0,
    val value: String? = null
)

@Serializable
data class SortOptionsDto(
    val sortField: Int = 0,
    val isAscending: Boolean = true
)

@Serializable
data class ProgressDto(
    val volumeId: Int = 0,
    val chapterId: Int = 0,
    val pageNum: Int = 0,
    val seriesId: Int = 0,
    val libraryId: Int = 0,
    val bookScrollId: String? = null,
    val lastModifiedUtc: String? = null
)

typealias ReaderProgressDto = ProgressDto

@Serializable
data class PersonDto(
    val id: Int = 0,
    val name: String = "",
    val role: Int = 0
)

@Serializable
data class GenreTagDto(
    val id: Int = 0,
    val title: String = ""
)

@Serializable
data class TagDto(
    val id: Int = 0,
    val title: String = ""
)

@Serializable
data class SeriesMetadataDto(
    val id: Int = 0,
    val summary: String? = null,
    val writers: List<PersonDto>? = null,
    val coverArtists: List<PersonDto>? = null,
    val publishers: List<PersonDto>? = null,
    val characters: List<PersonDto>? = null,
    val pencillers: List<PersonDto>? = null,
    val inkers: List<PersonDto>? = null,
    val colorists: List<PersonDto>? = null,
    val letterers: List<PersonDto>? = null,
    val editors: List<PersonDto>? = null,
    val imprints: List<PersonDto>? = null,
    val teams: List<PersonDto>? = null,
    val locations: List<PersonDto>? = null,
    val genres: List<GenreTagDto>? = null,
    val tags: List<TagDto>? = null,
    val releaseYear: Int? = null,
    val ageRating: Int? = null,
    val publicationStatus: Int? = null,
    val language: String? = null
)

@Serializable
data class MarkReadDto(
    val seriesId: Int = 0,
    val generateReadingSession: Boolean = false
)

typealias MarkSeriesDto = MarkReadDto

@Serializable
data class MarkVolumeReadDto(
    val seriesId: Int = 0,
    val volumeId: Int = 0,
    val generateReadingSession: Boolean = false
)

@Serializable
data class MarkVolumesReadDto(
    val seriesId: Int = 0,
    val volumeIds: List<Int>? = null,
    val chapterIds: List<Int>? = null,
    val generateReadingSession: Boolean = false
)

typealias MarkMultipleDto = MarkVolumesReadDto
