package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.BubbleMask
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.NormRect
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest {

    private fun createBubble(id: Int, left: Float, top: Float, right: Float, bottom: Float): DetectedBubble {
        return DetectedBubble(
            id = id,
            box = NormRect(left, top, right, bottom),
            score = 0.9f,
            classId = 0,
            mask = BubbleMask(0, byteArrayOf())
        )
    }

    @Test
    fun testEmptyAndSingleBubble() {
        assertEquals(emptyList<DetectedBubble>(), sortBubblesForReading(emptyList(), rtl = false))

        val single = listOf(createBubble(1, 0.1f, 0.1f, 0.2f, 0.2f))
        assertEquals(single, sortBubblesForReading(single, rtl = false))
    }

    @Test
    fun testLtrSameRowOrdering() {
        val b1 = createBubble(1, 0.5f, 0.1f, 0.7f, 0.2f) // right side
        val b2 = createBubble(2, 0.1f, 0.12f, 0.3f, 0.22f) // left side

        val sorted = sortBubblesForReading(listOf(b1, b2), rtl = false)
        assertEquals(listOf(2, 1), sorted.map { it.id })
    }

    @Test
    fun testRtlSameRowOrdering() {
        val b1 = createBubble(1, 0.1f, 0.1f, 0.3f, 0.2f) // left side
        val b2 = createBubble(2, 0.5f, 0.12f, 0.7f, 0.22f) // right side

        val sorted = sortBubblesForReading(listOf(b1, b2), rtl = true)
        assertEquals(listOf(2, 1), sorted.map { it.id })
    }

    @Test
    fun testDistinctRowsOrdering() {
        val bTop = createBubble(1, 0.1f, 0.05f, 0.3f, 0.15f)
        val bBottom = createBubble(2, 0.1f, 0.40f, 0.3f, 0.50f)

        val sorted = sortBubblesForReading(listOf(bBottom, bTop), rtl = false)
        assertEquals(listOf(1, 2), sorted.map { it.id })
    }

    @Test
    fun testTallAndShortBubbleInSameRow() {
        // Tall bubble starting slightly earlier, short neighbor next to it
        val bTall = createBubble(1, 0.1f, 0.10f, 0.3f, 0.30f)
        val bShort = createBubble(2, 0.4f, 0.12f, 0.6f, 0.22f)

        val sortedLtr = sortBubblesForReading(listOf(bShort, bTall), rtl = false)
        assertEquals(listOf(1, 2), sortedLtr.map { it.id })
    }

    @Test
    fun testTopRightBubbleDoesNotPrecedeTopLeftPanel() {
        // Panel 1 on the top left
        val bLeft1 = createBubble(1, 0.05f, 0.08f, 0.25f, 0.18f)
        val bLeft2 = createBubble(2, 0.05f, 0.20f, 0.25f, 0.30f)

        // Panel 2 on the top right (bubble starts slightly higher at top = 0.04f)
        val bRight1 = createBubble(3, 0.65f, 0.04f, 0.85f, 0.12f)

        val sorted = sortBubblesForReading(listOf(bRight1, bLeft2, bLeft1), rtl = false)
        // Expected order: Panel 1 (left) bubbles first (1, 2), then Panel 2 (right) bubble (3)
        assertEquals(listOf(1, 2, 3), sorted.map { it.id })
    }

    @Test
    fun testContractConsistencyLargeList() {
        val bubbles = (0 until 50).map { i ->
            val top = (i * 17 % 100) / 100f
            val left = (i * 23 % 100) / 100f
            createBubble(i, left, top, (left + 0.1f).coerceAtMost(1f), (top + 0.1f).coerceAtMost(1f))
        }

        // Must complete without throwing IllegalArgumentException (comparator contract check)
        val sorted = sortBubblesForReading(bubbles, rtl = false)
        assertEquals(50, sorted.size)
    }
}
