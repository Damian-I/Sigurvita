package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.BubbleMask
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.model.PageBubbles
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderUnitTest {

    private val defaultLetterbox = LetterboxParams(
        inputSize = 640,
        originalWidth = 2000,
        originalHeight = 3000,
        gain = 0.21333334f,
        padX = 0f,
        padY = 0f
    )

    private fun createDummyBubble(id: Int, left: Float, top: Float, right: Float, bottom: Float): DetectedBubble {
        return DetectedBubble(
            id = id,
            box = NormRect(left, top, right, bottom),
            score = 0.9f,
            classId = 0,
            mask = BubbleMask(16, ByteArray(256) { 0 })
        )
    }

    private fun createMaskBubble(
        id: Int,
        box: NormRect,
        panelLabel: Int,
        gridW: Int = 10,
        gridH: Int = 10,
        map: PanelMap? = null
    ): DetectedBubble {
        val maskSize = 16
        val probs = ByteArray(maskSize * maskSize) { 0 }
        // Fill center of bubble's bounding box in mask grid with prob = 200
        val lb = map?.letterbox ?: defaultLetterbox
        val cxNorm = (box.left + box.right) / 2f
        val cyNorm = (box.top + box.bottom) / 2f

        val scale = lb.inputSize.toFloat() / maskSize
        for (gy in 0 until maskSize) {
            for (gx in 0 until maskSize) {
                val (nx, ny) = lb.letterboxToNorm(gx * scale, gy * scale)
                if (nx >= box.left && nx <= box.right && ny >= box.top && ny <= box.bottom) {
                    probs[gy * maskSize + gx] = 200.toByte()
                }
            }
        }
        return DetectedBubble(
            id = id,
            box = box,
            score = 0.9f,
            classId = 0,
            mask = BubbleMask(maskSize, probs)
        )
    }

    @Test
    fun testTallLeftPlusStackedRight_LTR_and_RTL() {
        val pLeft = PanelInfo(1, NormRect(0.0f, 0.0f, 0.45f, 1.0f), 0.225f, 0.5f, 0.45f)
        val pRightTop = PanelInfo(2, NormRect(0.55f, 0.0f, 1.0f, 0.45f), 0.775f, 0.225f, 0.2025f)
        val pRightBot = PanelInfo(3, NormRect(0.55f, 0.55f, 1.0f, 1.0f), 0.775f, 0.775f, 0.2025f)
        val panels = listOf(pLeft, pRightTop, pRightBot)

        val orderedLTR = orderPanels(panels, rtl = false, isSpread = false)
        assertEquals(listOf(1, 2, 3), orderedLTR.map { it.label })

        val orderedRTL = orderPanels(panels, rtl = true, isSpread = false)
        assertEquals(listOf(2, 3, 1), orderedRTL.map { it.label })
    }

    @Test
    fun testGrid2x2_LTR_and_RTL() {
        val pTopLeft = PanelInfo(1, NormRect(0.0f, 0.0f, 0.45f, 0.45f), 0.225f, 0.225f, 0.2025f)
        val pTopRight = PanelInfo(2, NormRect(0.55f, 0.0f, 1.0f, 0.45f), 0.775f, 0.225f, 0.2025f)
        val pBotLeft = PanelInfo(3, NormRect(0.0f, 0.55f, 0.45f, 1.0f), 0.225f, 0.775f, 0.2025f)
        val pBotRight = PanelInfo(4, NormRect(0.55f, 0.55f, 1.0f, 1.0f), 0.775f, 0.775f, 0.2025f)
        val panels = listOf(pTopLeft, pTopRight, pBotLeft, pBotRight)

        val orderedLTR = orderPanels(panels, rtl = false, isSpread = false)
        assertEquals(listOf(1, 2, 3, 4), orderedLTR.map { it.label })

        val orderedRTL = orderPanels(panels, rtl = true, isSpread = false)
        assertEquals(listOf(2, 1, 4, 3), orderedRTL.map { it.label })
    }

    @Test
    fun testDiagonalLayout() {
        // Angled / staggered panels
        val p1 = PanelInfo(1, NormRect(0.0f, 0.0f, 0.6f, 0.35f), 0.3f, 0.175f, 0.21f)
        val p2 = PanelInfo(2, NormRect(0.4f, 0.3f, 1.0f, 0.65f), 0.7f, 0.475f, 0.21f)
        val p3 = PanelInfo(3, NormRect(0.0f, 0.6f, 0.6f, 1.0f), 0.3f, 0.8f, 0.24f)

        val ordered = orderPanels(listOf(p1, p2, p3), rtl = false, isSpread = false)
        assertEquals(listOf(1, 2, 3), ordered.map { it.label })
    }

    @Test
    fun testPinwheelLayout() {
        val pTop = PanelInfo(1, NormRect(0.0f, 0.0f, 1.0f, 0.3f), 0.5f, 0.15f, 0.3f)
        val pRight = PanelInfo(2, NormRect(0.7f, 0.3f, 1.0f, 1.0f), 0.85f, 0.65f, 0.21f)
        val pBottom = PanelInfo(3, NormRect(0.0f, 0.7f, 0.7f, 1.0f), 0.35f, 0.85f, 0.21f)
        val pLeft = PanelInfo(4, NormRect(0.0f, 0.3f, 0.3f, 0.7f), 0.15f, 0.5f, 0.12f)

        val orderedLTR = orderPanels(listOf(pTop, pRight, pBottom, pLeft), rtl = false, isSpread = false)
        assertEquals(listOf(1, 4, 2, 3), orderedLTR.map { it.label })
    }

    @Test
    fun testInsetPanel() {
        val pOuter = PanelInfo(1, NormRect(0.0f, 0.0f, 1.0f, 1.0f), 0.5f, 0.5f, 1.0f)
        val pInset = PanelInfo(2, NormRect(0.6f, 0.1f, 0.9f, 0.4f), 0.75f, 0.25f, 0.09f)

        val ordered = orderPanels(listOf(pOuter, pInset), rtl = false, isSpread = false)
        // Inset panel is higher up (cy = 0.25 vs 0.5)
        assertEquals(listOf(2, 1), ordered.map { it.label })
    }

    @Test
    fun testSpreadWithoutFoldCrossing_LTR_and_RTL() {
        val pLeft1 = PanelInfo(1, NormRect(0.05f, 0.1f, 0.45f, 0.45f), 0.25f, 0.275f, 0.14f)
        val pLeft2 = PanelInfo(2, NormRect(0.05f, 0.55f, 0.45f, 0.9f), 0.25f, 0.725f, 0.14f)
        val pRight1 = PanelInfo(3, NormRect(0.55f, 0.1f, 0.95f, 0.45f), 0.75f, 0.275f, 0.14f)
        val pRight2 = PanelInfo(4, NormRect(0.55f, 0.55f, 0.95f, 0.9f), 0.75f, 0.725f, 0.14f)

        val panels = listOf(pLeft1, pLeft2, pRight1, pRight2)

        val orderedLTR = orderPanels(panels, rtl = false, isSpread = true)
        assertEquals(listOf(1, 2, 3, 4), orderedLTR.map { it.label })

        val orderedRTL = orderPanels(panels, rtl = true, isSpread = true)
        assertEquals(listOf(3, 4, 1, 2), orderedRTL.map { it.label })
    }

    @Test
    fun testSpreadWithFoldCrossingSplashPanel() {
        val pSplash = PanelInfo(1, NormRect(0.2f, 0.1f, 0.8f, 0.9f), 0.5f, 0.5f, 0.48f)
        val pCorner = PanelInfo(2, NormRect(0.85f, 0.1f, 0.95f, 0.3f), 0.9f, 0.2f, 0.02f)

        val panels = listOf(pSplash, pCorner)
        val ordered = orderPanels(panels, rtl = false, isSpread = true)
        assertEquals(listOf(2, 1), ordered.map { it.label })
    }

    @Test
    fun testSanityGateFallback_NullOrEmptyMap() {
        val b1 = createDummyBubble(1, 0.1f, 0.1f, 0.3f, 0.2f)
        val b2 = createDummyBubble(2, 0.1f, 0.3f, 0.3f, 0.4f)
        val pb = PageBubbles(0, 2000, 3000, defaultLetterbox, listOf(b1, b2))

        val resultNull = orderBubbles(pb, null, rtl = false)
        assertEquals(2, resultNull.size)

        val emptyMap = PanelMap(10, 10, ShortArray(100), defaultLetterbox, emptyList())
        val resultEmpty = orderBubbles(pb, emptyMap, rtl = false)
        assertEquals(2, resultEmpty.size)
    }

    @Test
    fun testSanityGateFallback_LowCoverage() {
        val b1 = createDummyBubble(1, 0.1f, 0.1f, 0.3f, 0.2f)
        val pb = PageBubbles(0, 2000, 3000, defaultLetterbox, listOf(b1))

        // Tiny panel covering 0.01 area (below 0.15 threshold)
        val pTiny = PanelInfo(1, NormRect(0.0f, 0.0f, 0.1f, 0.1f), 0.05f, 0.05f, 0.01f)
        val map = PanelMap(10, 10, ShortArray(100) { 1 }, defaultLetterbox, listOf(pTiny))

        val result = orderBubbles(pb, map, rtl = false)
        assertEquals(1, result.size)
    }

    @Test
    fun testContainerPanelWithInsetAndUnassignedBubbles_MatchesExpectedLayout() {
        // Layout matching expected.png:
        // Panel 1: Top-Left (0.0, 0.0 to 0.45, 0.48)
        // Panel 2: Top-Right (0.52, 0.0 to 1.0, 0.48)
        // Panel 3: Inset Middle-Right (0.52, 0.52 to 0.98, 0.76)
        // Panel 4: Container Background covering bottom half (0.0, 0.50 to 1.0, 1.0)
        val p1 = PanelInfo(1, NormRect(0.0f, 0.0f, 0.45f, 0.48f), 0.225f, 0.24f, 0.216f)
        val p2 = PanelInfo(2, NormRect(0.52f, 0.0f, 1.0f, 0.48f), 0.76f, 0.24f, 0.23f)
        val p3 = PanelInfo(3, NormRect(0.52f, 0.52f, 0.98f, 0.76f), 0.75f, 0.64f, 0.11f)
        val p4 = PanelInfo(4, NormRect(0.0f, 0.50f, 1.0f, 1.0f), 0.50f, 0.75f, 0.50f)
        val panels = listOf(p1, p2, p3, p4)

        val bTopLeft = createDummyBubble(1, 0.1f, 0.1f, 0.3f, 0.2f)
        val bTopRight = createDummyBubble(2, 0.6f, 0.1f, 0.8f, 0.2f)
        val bMidLeft = createDummyBubble(3, 0.15f, 0.53f, 0.35f, 0.62f)
        val bInsetRight = createDummyBubble(4, 0.6f, 0.55f, 0.85f, 0.65f)
        val bBottom = createDummyBubble(5, 0.2f, 0.8f, 0.5f, 0.9f)

        // Labels matrix mapping:
        // Panel 1 (1), Panel 2 (2), Panel 3 (3), Panel 4 (4)
        val gridDim = 10
        val labels = ShortArray(gridDim * gridDim) { 4.toShort() }
        // Fill top-left with 1, top-right with 2, inset with 3
        for (gy in 0..4) {
            for (gx in 0..4) labels[gy * gridDim + gx] = 1.toShort()
            for (gx in 5..9) labels[gy * gridDim + gx] = 2.toShort()
        }
        for (gy in 5..7) {
            for (gx in 5..9) labels[gy * gridDim + gx] = 3.toShort()
        }

        val map = PanelMap(gridDim, gridDim, labels, defaultLetterbox, panels)
        val pb = PageBubbles(0, 2000, 3000, defaultLetterbox, listOf(bTopLeft, bTopRight, bMidLeft, bInsetRight, bBottom))

        val ordered = orderBubbles(pb, map, rtl = false)
        val ids = ordered.map { it.id }

        // Expected sequence: Top-Left (1), Top-Right (2), Middle-Left in container (3), Inset Middle-Right (4), Bottom in container (5)
        assertEquals(listOf(1, 2, 3, 4, 5), ids)
    }

    @Test
    fun testIntraPanelBubbleOrdering_LTR_and_RTL() {
        // Bubble 1: Top-Left (0.1, 0.10)
        val bTopLeft = createDummyBubble(1, 0.1f, 0.10f, 0.35f, 0.20f)
        // Bubble 2: Top-Right (0.6, 0.12) - same dialogue line as Bubble 1 (high vertical overlap)
        val bTopRight = createDummyBubble(2, 0.6f, 0.12f, 0.85f, 0.22f)
        // Bubble 3: Bottom-Left (0.15, 0.40) - lower dialogue line
        val bBottom = createDummyBubble(3, 0.15f, 0.40f, 0.40f, 0.50f)

        val bubbles = listOf(bTopRight, bBottom, bTopLeft)

        // LTR: Top-Left (1) -> Top-Right (2) -> Bottom (3)
        val orderedLTR = orderBubblesInPanel(bubbles, rtl = false)
        assertEquals(listOf(1, 2, 3), orderedLTR.map { it.id })

        // RTL: Top-Right (2) -> Top-Left (1) -> Bottom (3)
        val orderedRTL = orderBubblesInPanel(bubbles, rtl = true)
        assertEquals(listOf(2, 1, 3), orderedRTL.map { it.id })
    }
}
