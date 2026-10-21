package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto

private val ANILIST_MANGA = Regex("""^https?://(www\.)?anilist\.co/manga/\d+""", RegexOption.IGNORE_CASE)

/** First AniList manga URL in the series' web links, or null. */
fun SeriesMetadataDto.aniListUrl(): String? =
    webLinks?.split(',')?.map { it.trim() }?.firstOrNull { ANILIST_MANGA.containsMatchIn(it) }