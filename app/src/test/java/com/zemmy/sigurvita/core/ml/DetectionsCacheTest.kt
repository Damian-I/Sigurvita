package com.zemmy.sigurvita.core.ml

import com.zemmy.sigurvita.core.model.BubbleMask
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.util.PanelInfo
import com.zemmy.sigurvita.core.util.PanelMap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionsCacheTest {

    private val letterbox = LetterboxParams(
        inputSize = 1024,
        originalWidth = 2000,
        originalHeight = 1000,
        gain = 0.512f,
        padX = 0f,
        padY = 256f
    )

    @Test
    fun bubblesSurviveAFullEncodeDecodeRoundTrip() {
        val probs = byteArrayOf(0, 1, 64, 127, -128, -1, 42, 7)
        val original = PageBubbles(
            pageIndex = 2,
            pageWidth = 1000,
            pageHeight = 1500,
            letterbox = letterbox,
            bubbles = listOf(
                DetectedBubble(
                    id = 5,
                    box = NormRect(0.1f, 0.2f, 0.3f, 0.4f),
                    score = 0.9f,
                    classId = 0,
                    mask = BubbleMask(size = 4, probs = probs)
                ),
                DetectedBubble(
                    id = 6,
                    box = NormRect(0.5f, 0.5f, 0.6f, 0.7f),
                    score = 0.25f,
                    classId = 0,
                    mask = BubbleMask(size = 0, probs = ByteArray(0))
                )
            )
        )

        val data = DetectionsCacheHelper.fromPageData(2, original, null)
        val restored = DetectionsCacheHelper.toPageBubbles(data, 2)

        assertEquals(2, restored.pageIndex)
        assertEquals(1000, restored.pageWidth)
        assertEquals(1500, restored.pageHeight)
        assertEquals(letterbox, restored.letterbox)
        assertEquals(2, restored.bubbles.size)

        val first = restored.bubbles[0]
        assertEquals(5, first.id)
        assertEquals(0.1f, first.box.left, 0f)
        assertEquals(0.2f, first.box.top, 0f)
        assertEquals(0.3f, first.box.right, 0f)
        assertEquals(0.4f, first.box.bottom, 0f)
        assertEquals(0.9f, first.score, 0f)
        assertEquals(4, first.mask.size)
        assertArrayEquals(probs, first.mask.probs)

        assertTrue(restored.bubbles[1].mask.probs.isEmpty())
    }

    @Test
    fun panelMapLabelsKeepEveryShortValueIncludingNegativesAndLargeOnes() {
        val labels = shortArrayOf(0, 1, 255, 256, 258, -1, Short.MAX_VALUE, Short.MIN_VALUE)
        val panels = listOf(
            PanelInfo(label = 1, box = NormRect(0f, 0f, 0.5f, 0.5f), cx = 0.25f, cy = 0.25f, area = 0.25f),
            PanelInfo(label = 2, box = NormRect(0.5f, 0f, 1f, 1f), cx = 0.75f, cy = 0.5f, area = 0.5f)
        )
        val original = PanelMap(gridW = 4, gridH = 2, labels = labels, letterbox = letterbox, panels = panels)

        val data = DetectionsCacheHelper.fromPageData(0, null, original)
        val restored = DetectionsCacheHelper.toPanelMap(data)

        assertEquals(4, restored.gridW)
        assertEquals(2, restored.gridH)
        assertArrayEquals(labels, restored.labels)
        assertEquals(letterbox, restored.letterbox)
        assertEquals(panels.map { it.label }, restored.panels.map { it.label })
        assertEquals(0.5f, restored.panels[1].area, 0f)
        assertEquals(1f, restored.panels[1].box.bottom, 0f)
    }

    @Test
    fun pageWithNoPanelsStillDecodesToAnEmptyPanelMap() {
        // Pages without panels (covers, splash pages) are cached too; they must come back as a map, not null.
        val empty = PanelMap(gridW = 4, gridH = 3, labels = ShortArray(0), letterbox = letterbox, panels = emptyList())

        val data = DetectionsCacheHelper.fromPageData(7, null, empty)
        val restored = DetectionsCacheHelper.toPanelMap(data)

        assertTrue(restored.panels.isEmpty())
        assertEquals(4, restored.gridW)
        assertEquals(3, restored.gridH)
        assertEquals(12, restored.labels.size)
        assertTrue(restored.labels.all { it.toInt() == 0 })
    }

    @Test
    fun missingBubblesAndPanelsFallBackToDefaultsWithoutCrashing() {
        val data = DetectionsCacheHelper.fromPageData(1, null, null)

        assertEquals(1, data.pageIndex)
        assertTrue(data.bubbles.isEmpty())
        assertTrue(data.panels.isEmpty())
        assertEquals("", data.panelLabelsBase64)
        assertEquals(224, data.panelGridW)
        assertEquals(224, data.panelGridH)
    }

    @Test
    fun letterboxFromBubblesWinsOverTheOneFromPanels() {
        val other = LetterboxParams(640, 800, 1200, 0.5f, 10f, 20f)
        val bubbles = PageBubbles(0, 800, 1200, other, emptyList())
        val panels = PanelMap(gridW = 1, gridH = 1, labels = ShortArray(1), letterbox = letterbox, panels = emptyList())

        val data = DetectionsCacheHelper.fromPageData(0, bubbles, panels)

        assertEquals(640, data.letterbox.inputSize)
        assertEquals(800, data.letterbox.originalWidth)
        assertEquals(10f, data.letterbox.padX, 0f)
    }

    @Test
    fun writerNeverEmitsLineBreaksInBase64() {
        // The writer must never emit line breaks: the reader uses the strict decoder.
        val big = ByteArray(300) { (it % 251).toByte() }
        val bubbles = PageBubbles(
            0, 10, 10, letterbox,
            listOf(DetectedBubble(1, NormRect(0f, 0f, 1f, 1f), 1f, 0, BubbleMask(10, big)))
        )

        val data = DetectionsCacheHelper.fromPageData(0, bubbles, null)

        assertTrue(data.bubbles[0].maskProbsBase64.none { it == '\n' || it == '\r' })
        assertArrayEquals(big, DetectionsCacheHelper.toPageBubbles(data, 0).bubbles[0].mask.probs)
    }
}
