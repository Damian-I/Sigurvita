package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaUrlBuilderTest {

    private val config = ServerConfig(serverUrl = "https://kavita.example.com", apiKey = "key-123")

    @Test
    fun coverUrlsCarryTheApiKey() {
        assertEquals(
            "https://kavita.example.com/api/image/series-cover?seriesId=7&apiKey=key-123",
            config.getSeriesCoverUrl(7)
        )
        assertEquals(
            "https://kavita.example.com/api/image/chapter-cover?chapterId=8&apiKey=key-123",
            config.getChapterCoverUrl(8)
        )
        assertEquals(
            "https://kavita.example.com/api/image/person-cover?personId=9&apiKey=key-123",
            config.getPersonCoverUrl(9)
        )
    }

    @Test
    fun readerImageUrlUsesChapterAndPageIndex() {
        assertEquals(
            "https://kavita.example.com/api/Reader/image?chapterId=5&page=12&apiKey=key-123",
            config.getReaderImageUrl(5, 12)
        )
    }

    @Test
    fun surroundingWhitespaceAndTrailingSlashAreRemovedFromTheServerUrl() {
        val messy = ServerConfig(serverUrl = "  https://kavita.example.com/ ", apiKey = "k")
        assertEquals(
            "https://kavita.example.com/api/image/series-cover?seriesId=1&apiKey=k",
            messy.getSeriesCoverUrl(1)
        )
    }

    @Test
    fun resolvePageImageUriKeepsRemoteUrls() {
        val url = "https://kavita.example.com/api/Reader/image?chapterId=1&page=0&apiKey=key-123"
        assertEquals(url, config.resolvePageImageUri(url, 1, 0))
    }

    @Test
    fun resolvePageImageUriFallsBackToStreamingWhenTheLocalFileIsGone() {
        val missing = "/definitely/not/here/page_0.webp"
        assertEquals(config.getReaderImageUrl(3, 4), config.resolvePageImageUri(missing, 3, 4))
        assertEquals(config.getReaderImageUrl(3, 4), config.resolvePageImageUri("file://$missing", 3, 4))
    }

    @Test
    fun resolvePageImageUriKeepsTheMissingPathWhenThereIsNoServer() {
        val offline = ServerConfig()
        val missing = "/definitely/not/here/page_0.webp"
        assertEquals(missing, offline.resolvePageImageUri(missing, 3, 4))
    }
}
