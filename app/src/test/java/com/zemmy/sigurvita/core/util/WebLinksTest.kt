package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLinksTest {

    private fun meta(links: String?) = SeriesMetadataDto(webLinks = links)

    @Test
    fun mangaBakaSeriesPageShapesAreRecognised() {
        listOf(
            "https://mangabaka.org/1677/berserk",
            "https://mangabaka.org/manga/1677/berserk",
            "https://mangabaka.org/novel/55",
            "http://www.mangabaka.org/9",
            "HTTPS://MangaBaka.org/12"
        ).forEach { url ->
            val links = meta(url).externalLinks()
            assertEquals(url, 1, links.size)
            assertEquals(url, ExternalLinkProvider.MANGABAKA, links[0].provider)
            assertEquals(url, url, links[0].url)
        }
    }

    @Test
    fun mangaBakaPagesWithoutAnIdAreIgnored() {
        assertTrue(meta("https://mangabaka.org/about").externalLinks().isEmpty())
        assertTrue(meta("https://mangabaka.org/search?q=1").externalLinks().isEmpty())
    }

    @Test
    fun comicVineHostVariantsAreRecognised() {
        listOf(
            "https://comicvine.gamespot.com/batman/4050-796/",
            "https://www.comicvine.com/x"
        ).forEach { url ->
            val links = meta(url).externalLinks()
            assertEquals(url, 1, links.size)
            assertEquals(url, ExternalLinkProvider.COMIC_VINE, links[0].provider)
        }
    }

    @Test
    fun hostNameInsideAnotherUrlDoesNotMatch() {
        assertTrue(meta("https://example.com/mangabaka.org/1").externalLinks().isEmpty())
    }

    @Test
    fun commaSeparatedLinksAreTrimmedAndKeepWebLinksOrder() {
        val links = meta(" https://comicvine.gamespot.com/x/4050-1/ , https://mangabaka.org/1/a ,, ").externalLinks()
        assertEquals(
            listOf(ExternalLinkProvider.COMIC_VINE, ExternalLinkProvider.MANGABAKA),
            links.map { it.provider }
        )
        assertEquals("https://comicvine.gamespot.com/x/4050-1/", links[0].url)
        assertEquals("https://mangabaka.org/1/a", links[1].url)
    }

    @Test
    fun duplicateProvidersKeepOnlyTheFirstLink() {
        val links = meta("https://mangabaka.org/1/a,https://mangabaka.org/2/b").externalLinks()
        assertEquals(1, links.size)
        assertEquals("https://mangabaka.org/1/a", links[0].url)
    }

    @Test
    fun nullOrBlankWebLinksGiveNoLinks() {
        assertTrue(meta(null).externalLinks().isEmpty())
        assertTrue(meta("").externalLinks().isEmpty())
        assertTrue(meta("  ,  ").externalLinks().isEmpty())
    }

    @Test
    fun primaryLinkPrefersMangaBakaForMangaAndComicVineForComics() {
        val both = meta("https://comicvine.gamespot.com/x/4050-1/,https://mangabaka.org/1/a")
        assertEquals(ExternalLinkProvider.MANGABAKA, both.primaryExternalLink(isManga = true)?.provider)
        assertEquals(ExternalLinkProvider.COMIC_VINE, both.primaryExternalLink(isManga = false)?.provider)
    }

    @Test
    fun primaryLinkFallsBackToAnyRecognisedLink() {
        val onlyComicVine = meta("https://comicvine.gamespot.com/x/4050-1/")
        assertEquals(ExternalLinkProvider.COMIC_VINE, onlyComicVine.primaryExternalLink(isManga = true)?.provider)
        val onlyMangaBaka = meta("https://mangabaka.org/1/a")
        assertEquals(ExternalLinkProvider.MANGABAKA, onlyMangaBaka.primaryExternalLink(isManga = false)?.provider)
    }

    @Test
    fun primaryLinkIsNullWhenNothingIsRecognised() {
        assertNull(meta(null).primaryExternalLink(isManga = true))
        assertNull(meta("https://example.com/").primaryExternalLink(isManga = false))
    }
}
