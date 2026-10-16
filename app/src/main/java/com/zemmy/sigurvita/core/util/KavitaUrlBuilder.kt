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

fun ServerConfig.getReaderImageUrl(chapterId: Int, pageIndex: Int): String {
    val cleanUrl = serverUrl.trim().removeSuffix("/")
    return "$cleanUrl/api/Reader/image?chapterId=$chapterId&page=$pageIndex&apiKey=$apiKey"
}

fun ServerConfig.resolvePageImageUri(currentUrl: String, chapterId: Int, pageIndex: Int): String {
    val cleanPath = currentUrl.removePrefix("file://")
    val file = java.io.File(cleanPath)
    return if ((currentUrl.startsWith("/") || currentUrl.startsWith("file://")) && !file.exists()) {
        if (serverUrl.isNotBlank()) getReaderImageUrl(chapterId, pageIndex) else currentUrl
    } else {
        currentUrl
    }
}
