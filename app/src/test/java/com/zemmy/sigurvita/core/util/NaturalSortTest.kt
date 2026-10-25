package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.ReadingList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortTest {

    // ---- naturalCompare -------------------------------------------------------------------

    @Test
    fun numbersAreComparedNumerically() {
        assertTrue(naturalCompare("page2", "page10") < 0)
        assertTrue(naturalCompare("page10", "page2") > 0)
        val sorted = listOf("10.jpg", "2.jpg", "1.jpg").sortedWith { a, b -> naturalCompare(a, b) }
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), sorted)
    }

    @Test
    fun lettersAreComparedIgnoringCase() {
        assertEquals(0, naturalCompare("A1", "a1"))
        assertTrue(naturalCompare("abc", "ABD") < 0)
    }

    @Test
    fun shorterPrefixSortsFirst() {
        assertTrue(naturalCompare("ab", "abc") < 0)
        assertEquals(0, naturalCompare("same", "same"))
    }

    @Test
    fun equalNumbersWithLeadingZerosTieBreakByLength() {
        assertTrue(naturalCompare("p01", "p1") > 0)
        assertTrue(naturalCompare("p1", "p01") < 0)
    }

    // ---- extractChapterNumber -------------------------------------------------------------

    @Test
    fun explicitNumberWinsOverTheTitle() {
        assertEquals("5", extractChapterNumber("5", "Chapter 9"))
        assertEquals("12.5", extractChapterNumber(" 12.5 ", "x"))
    }

    @Test
    fun blankOrZeroNumberIsRecoveredFromTheTitle() {
        assertEquals("7", extractChapterNumber("0", "Chapter 7: Foo"))
        assertEquals("12", extractChapterNumber("", "#12 - Title"))
        assertEquals("42", extractChapterNumber("0.0", "42 The Answer"))
        assertEquals("3a", extractChapterNumber("", "Issue 3a"))
    }

    @Test
    fun noNumberAnywhereFallsBackToZero() {
        assertEquals("0", extractChapterNumber("", ""))
        assertEquals("0", extractChapterNumber("0", ""))
        assertEquals("0", extractChapterNumber("0", "No numbers here"))
    }

    // ---- formatChapterDisplayTitle --------------------------------------------------------

    @Test
    fun comicTitleShowsNumberAndName() {
        assertEquals("#1 - The Beginning", formatChapterDisplayTitle("The Beginning", "1"))
    }

    @Test
    fun mangaTitleUsesTheChapterPrefix() {
        assertEquals("Chapter #3 - Awakening", formatChapterDisplayTitle("Awakening", "3", isManga = true))
    }

    @Test
    fun titleThatOnlyRepeatsTheNumberIsDropped() {
        assertEquals("#5", formatChapterDisplayTitle("Issue 5", "5"))
    }

    @Test
    fun sentinelChapterNumbersAreHidden() {
        assertEquals("Special", formatChapterDisplayTitle("Special", "100000"))
    }

    @Test
    fun emptyTitleAndNumberFallBackToAGenericLabel() {
        assertEquals("Issue", formatChapterDisplayTitle("", ""))
        assertEquals("Chapter", formatChapterDisplayTitle("", "", isManga = true))
    }

    // ---- reading-list titles ----------------------------------------------------------------

    @Test
    fun readingListTitleLosesBracketsYearsAndTrailingSuffixes() {
        assertEquals("Heroes Relaunched", cleanReadingListTitle("[Marvel] Heroes Relaunched 1997-2004 (WEB)"))
    }

    @Test
    fun readingListTitleThatWouldBecomeEmptyIsKept() {
        assertEquals("[Marvel]", cleanReadingListTitle("[Marvel]"))
    }

    @Test
    fun eraIsTakenFromKnownYearRanges() {
        val era = extractEraInfo("1997-2004 Heroes Relaunched")
        assertEquals("Heroes Relaunched", era.name)
        assertEquals("1997-2004", era.years)
    }

    @Test
    fun unknownYearRangeUsesTheRangeAsTheName() {
        assertEquals("1990-1995", extractEraInfo("Unknown 1990-1995").name)
    }

    @Test
    fun starWarsRuleBeatsYearRanges() {
        val era = extractEraInfo("Star Wars 1977-1984")
        assertEquals("Star Wars", era.name)
        assertNull(era.years)
    }

    @Test
    fun bracketPrefixBecomesTheEraUnlessItIsMarvel() {
        assertEquals("DC", extractEraInfo("[DC] Rebirth").name)
        assertEquals("General", extractEraInfo("[Marvel] Random").name)
        assertEquals("General", extractEraInfo("Plain title").name)
    }

    @Test
    fun partAndSubpartAreSplitOutOfTheTitle() {
        assertEquals("Part 02" to "2.3 The Fall", parsePartAndSubpart("Part 2.3 - The Fall"))
        assertEquals("Part 04" to "4 Finale", parsePartAndSubpart("Part 4 Finale"))
        assertEquals("Part 01" to "Random list", parsePartAndSubpart("Random list"))
    }

    @Test
    fun hierarchyGroupsByEraThenPartAndSortsSubparts() {
        fun list(id: Int, title: String) = ReadingList(id = id, title = title)

        val eras = parseReadingListHierarchy(
            listOf(
                list(1, "1997-2004 Part 2.2"),
                list(2, "Star Wars Part 1"),
                list(3, "1997-2004 Part 1"),
                list(4, "1997-2004 Part 2.1")
            )
        )

        assertEquals(listOf("Heroes Relaunched", "Star Wars"), eras.map { it.eraName })

        val heroes = eras[0]
        assertEquals(listOf("Part 1", "Part 2"), heroes.parts.map { it.partDisplayName })
        assertEquals(listOf("2.1", "2.2"), heroes.parts[1].subparts.map { it.subpartDisplayTitle })
        assertEquals(listOf(4, 1), heroes.parts[1].subparts.map { it.list.id })
    }
}
