package com.zemmy.sigurvita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun libraryIsMangaByTypeOrByName() {
        assertTrue(Library(id = 1, name = "Books", type = 0).isManga)
        assertTrue(Library(id = 2, name = "My MANGA shelf", type = 1).isManga)
        assertFalse(Library(id = 3, name = "Comics", type = 1).isManga)
    }

    @Test
    fun seriesIsFullyReadByPagesIssuesOrCompletionFlag() {
        assertTrue(Series(id = 1, libraryId = 1, name = "a", totalPages = 10, pagesRead = 10).isFullyRead)
        assertTrue(Series(id = 1, libraryId = 1, name = "a", totalIssues = 5, issuesRead = 5).isFullyRead)
        assertTrue(Series(id = 1, libraryId = 1, name = "a", isCompleted = true).isFullyRead)
        assertFalse(Series(id = 1, libraryId = 1, name = "a", totalPages = 10, pagesRead = 9).isFullyRead)
        assertFalse(Series(id = 1, libraryId = 1, name = "a").isFullyRead)
    }

    @Test
    fun letterboxToNormMapsPaddedCoordinatesBackToThePage() {
        // 2000x1000 page letterboxed into 1024x1024: gain 0.512, vertical padding 256.
        val lb = LetterboxParams(
            inputSize = 1024,
            originalWidth = 2000,
            originalHeight = 1000,
            gain = 0.512f,
            padX = 0f,
            padY = 256f
        )
        val (x, y) = lb.letterboxToNorm(512f, 512f)
        assertEquals(0.5f, x, 1e-4f)
        assertEquals(0.5f, y, 1e-4f)
    }

    @Test
    fun letterboxToNormClampsOutOfRangeCoordinates() {
        val lb = LetterboxParams(1024, 2000, 1000, 0.512f, 0f, 256f)
        val (x, y) = lb.letterboxToNorm(-10f, 5000f)
        assertEquals(0f, x, 0f)
        assertEquals(1f, y, 0f)
    }
}
