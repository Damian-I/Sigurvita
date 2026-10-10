package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.ServerConfig

/**
 * Extension functions for constructing authenticated Kavita image API URLs.
 */
fun ServerConfig.getSeriesCoverUrl(seriesId: Int): String {
    val cleanUrl = serverUrl.trim().removeSuffix("/")
    return "$cleanUrl/api/image/series-cover?seriesId=$seriesId&apiKey=$apiKey"
}

fun ServerConfig.getChapterCoverUrl(chapterId: Int): String {
    val cleanUrl = serverUrl.trim().removeSuffix("/")
    return "$cleanUrl/api/image/chapter-cover?chapterId=$chapterId&apiKey=$apiKey"
}

fun ServerConfig.getPersonCoverUrl(personId: Int): String {
    val cleanUrl = serverUrl.trim().removeSuffix("/")
    return "$cleanUrl/api/image/person-cover?personId=$personId&apiKey=$apiKey"
}
