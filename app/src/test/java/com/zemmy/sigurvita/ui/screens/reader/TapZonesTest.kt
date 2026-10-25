package com.zemmy.sigurvita.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class TapZonesTest {

    private val width = 1000f

    @Test
    fun ltrLeftEdgeIsPreviousAndRightEdgeIsNext() {
        assertEquals(TapZone.PREVIOUS, classifyTapZone(0f, width, isRtl = false))
        assertEquals(TapZone.PREVIOUS, classifyTapZone(299f, width, isRtl = false))
        assertEquals(TapZone.NEXT, classifyTapZone(701f, width, isRtl = false))
        assertEquals(TapZone.NEXT, classifyTapZone(1000f, width, isRtl = false))
    }

    @Test
    fun rtlFlipsWhichEdgeIsNext() {
        assertEquals(TapZone.NEXT, classifyTapZone(10f, width, isRtl = true))
        assertEquals(TapZone.PREVIOUS, classifyTapZone(990f, width, isRtl = true))
    }

    @Test
    fun theMiddleStretchIsTheCentreInBothDirections() {
        for (isRtl in listOf(false, true)) {
            assertEquals(TapZone.CENTER, classifyTapZone(301f, width, isRtl))
            assertEquals(TapZone.CENTER, classifyTapZone(500f, width, isRtl))
            assertEquals(TapZone.CENTER, classifyTapZone(699f, width, isRtl))
        }
    }

    @Test
    fun theBoundariesBelongToTheCentre() {
        // Strict comparisons, exactly as the original inline tap handler had them.
        for (isRtl in listOf(false, true)) {
            assertEquals(TapZone.CENTER, classifyTapZone(width * 0.3f, width, isRtl))
            assertEquals(TapZone.CENTER, classifyTapZone(width * 0.7f, width, isRtl))
        }
    }

    @Test
    fun aNonPositiveWidthIsAlwaysTheCentre() {
        assertEquals(TapZone.CENTER, classifyTapZone(0f, 0f, isRtl = false))
        assertEquals(TapZone.CENTER, classifyTapZone(5f, -10f, isRtl = true))
    }
}
