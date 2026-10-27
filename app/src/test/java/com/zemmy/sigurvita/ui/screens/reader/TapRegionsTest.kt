package com.zemmy.sigurvita.ui.screens.reader

import com.zemmy.sigurvita.core.model.CustomTapZone
import com.zemmy.sigurvita.core.model.NormPoint
import com.zemmy.sigurvita.core.model.TapAction
import com.zemmy.sigurvita.core.model.TapZoneLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapRegionsTest {

    private val w = 1000f
    private val h = 2000f

    private fun zone(
        layout: TapZoneLayout,
        x: Float,
        y: Float,
        isRtl: Boolean = false,
        custom: List<CustomTapZone> = emptyList(),
        mirrorCustom: Boolean = true
    ): TapZone = classifyTap(x * w, y * h, w, h, tapRegionsFor(layout, custom, mirrorCustom, isRtl))

    private fun rect(action: TapAction, l: Float, t: Float, r: Float, b: Float) = CustomTapZone(
        action,
        listOf(NormPoint(l, t), NormPoint(r, t), NormPoint(r, b), NormPoint(l, b))
    )

    @Test
    fun edgesMatchTheLegacyClassifierAwayFromTheBoundaries() {
        for (isRtl in listOf(false, true)) {
            for (x in listOf(0.001f, 0.1f, 0.29f, 0.31f, 0.5f, 0.69f, 0.71f, 0.9f, 0.999f)) {
                assertEquals(
                    "x=$x rtl=$isRtl",
                    classifyTapZone(x * w, w, isRtl),
                    zone(TapZoneLayout.EDGES, x, 0.5f, isRtl)
                )
            }
        }
    }

    @Test
    fun topBottom() {
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.TOP_BOTTOM, 0.5f, 0.1f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.TOP_BOTTOM, 0.5f, 0.9f))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.TOP_BOTTOM, 0.5f, 0.5f))
        // Vertical layout: reading direction does not change it.
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.TOP_BOTTOM, 0.5f, 0.1f, isRtl = true))
    }

    @Test
    fun lShapeBackIsBottomLeftNextIsTopRight() {
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.L_SHAPE, 0.1f, 0.9f))
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.L_SHAPE, 0.1f, 0.5f))
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.L_SHAPE, 0.5f, 0.9f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.L_SHAPE, 0.9f, 0.1f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.L_SHAPE, 0.9f, 0.5f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.L_SHAPE, 0.5f, 0.1f))
        // Centre and the two other corners are not zones.
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.L_SHAPE, 0.5f, 0.5f))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.L_SHAPE, 0.1f, 0.1f))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.L_SHAPE, 0.9f, 0.9f))
    }

    @Test
    fun lShapeMirrorsInRtl() {
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.L_SHAPE, 0.9f, 0.9f, isRtl = true))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.L_SHAPE, 0.1f, 0.1f, isRtl = true))
    }

    @Test
    fun kindle() {
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.KINDLE, 0.1f, 0.5f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.KINDLE, 0.5f, 0.5f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.KINDLE, 0.9f, 0.9f))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.KINDLE, 0.5f, 0.05f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.KINDLE, 0.1f, 0.5f, isRtl = true))
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.KINDLE, 0.9f, 0.5f, isRtl = true))
    }

    @Test
    fun customWithNoZonesIsAlwaysTheCentre() {
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.CUSTOM, 0.5f, 0.5f))
    }

    @Test
    fun customZoneAndMirroring() {
        val custom = listOf(rect(TapAction.NEXT, 0.2f, 0.2f, 0.4f, 0.4f))
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.CUSTOM, 0.3f, 0.3f, custom = custom))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.CUSTOM, 0.7f, 0.3f, custom = custom))
        // RTL with mirroring on: the zone moves to the other side.
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.CUSTOM, 0.7f, 0.3f, isRtl = true, custom = custom))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.CUSTOM, 0.3f, 0.3f, isRtl = true, custom = custom))
        // RTL with mirroring off: it stays put.
        assertEquals(
            TapZone.NEXT,
            zone(TapZoneLayout.CUSTOM, 0.3f, 0.3f, isRtl = true, custom = custom, mirrorCustom = false)
        )
    }

    @Test
    fun theLastOverlappingZoneWins() {
        val custom = listOf(
            rect(TapAction.PREVIOUS, 0.1f, 0.1f, 0.6f, 0.6f),
            rect(TapAction.NEXT, 0.4f, 0.4f, 0.9f, 0.9f)
        )
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.CUSTOM, 0.5f, 0.5f, custom = custom))
        assertEquals(TapZone.PREVIOUS, zone(TapZoneLayout.CUSTOM, 0.2f, 0.2f, custom = custom))
    }

    @Test
    fun aHandDrawnTriangle() {
        val triangle = CustomTapZone(
            TapAction.NEXT,
            listOf(NormPoint(0.1f, 0.1f), NormPoint(0.9f, 0.1f), NormPoint(0.5f, 0.9f))
        )
        assertEquals(TapZone.NEXT, zone(TapZoneLayout.CUSTOM, 0.5f, 0.3f, custom = listOf(triangle)))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.CUSTOM, 0.1f, 0.8f, custom = listOf(triangle)))
    }

    @Test
    fun aZoneWithFewerThanThreePointsNeverHits() {
        val line = CustomTapZone(TapAction.NEXT, listOf(NormPoint(0.1f, 0.1f), NormPoint(0.9f, 0.9f)))
        assertEquals(TapZone.CENTER, zone(TapZoneLayout.CUSTOM, 0.5f, 0.5f, custom = listOf(line)))
    }

    @Test
    fun aNonPositiveSizeIsTheCentre() {
        val regions = tapRegionsFor(TapZoneLayout.EDGES, emptyList(), true, false)
        assertEquals(TapZone.CENTER, classifyTap(10f, 10f, 0f, 100f, regions))
        assertEquals(TapZone.CENTER, classifyTap(10f, 10f, 100f, -1f, regions))
    }

    @Test
    fun labelPointIsInsideEveryPresetRegion() {
        for (layout in TapZoneLayout.entries) {
            for (region in tapRegionsFor(layout, emptyList(), true, false)) {
                val p = labelPoint(region.points)
                assertTrue("$layout ${region.action}", containsPoint(region.points, p.x, p.y))
            }
        }
    }
}