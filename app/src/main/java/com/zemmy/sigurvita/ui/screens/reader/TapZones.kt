package com.zemmy.sigurvita.ui.screens.reader

import com.zemmy.sigurvita.core.model.CustomTapZone
import com.zemmy.sigurvita.core.model.NormPoint
import com.zemmy.sigurvita.core.model.TapAction
import com.zemmy.sigurvita.core.model.TapZoneLayout

/** What a tap means once the screen is split into page-transition zones and "everywhere else". */
internal enum class TapZone { PREVIOUS, NEXT, CENTER }

/** The left transition zone ends here, as a fraction of the width. */
private const val LEFT_ZONE_END = 0.3f

/** The right transition zone starts here, as a fraction of the width. */
private const val RIGHT_ZONE_START = 0.7f

/** Legacy horizontal-only classifier. Superseded by [classifyTap]; kept for its unit tests. */
internal fun classifyTapZone(x: Float, width: Float, isRtl: Boolean): TapZone {
    if (width <= 0f) return TapZone.CENTER
    return when {
        x < width * LEFT_ZONE_END -> if (isRtl) TapZone.NEXT else TapZone.PREVIOUS
        x > width * RIGHT_ZONE_START -> if (isRtl) TapZone.PREVIOUS else TapZone.NEXT
        else -> TapZone.CENTER
    }
}

/**
 * One tappable region: a polygon in normalised screen coordinates plus its action.
 * Public because [ImageReader] (public) takes it as a parameter.
 * Actions are absolute ("next page"), so mirroring for RTL is already baked into [points].
 */
data class TapRegion(val action: TapAction, val points: List<NormPoint>)

private fun rect(action: TapAction, l: Float, t: Float, r: Float, b: Float) = TapRegion(
    action,
    listOf(NormPoint(l, t), NormPoint(r, t), NormPoint(r, b), NormPoint(l, b))
)

private fun poly(action: TapAction, vararg xy: Float) = TapRegion(
    action,
    xy.toList().chunked(2).map { NormPoint(it[0], it[1]) }
)

/** Preset layouts for left-to-right reading. Anything not covered toggles the controls. */
private fun presetRegions(layout: TapZoneLayout): List<TapRegion> = when (layout) {
    TapZoneLayout.EDGES -> listOf(
        rect(TapAction.PREVIOUS, 0f, 0f, LEFT_ZONE_END, 1f),
        rect(TapAction.NEXT, RIGHT_ZONE_START, 0f, 1f, 1f)
    )
    TapZoneLayout.TOP_BOTTOM -> listOf(
        rect(TapAction.PREVIOUS, 0f, 0f, 1f, 0.3f),
        rect(TapAction.NEXT, 0f, 0.7f, 1f, 1f)
    )
    // Back: left column (lower part) + bottom strip. Next: right column (upper part) + top strip.
    // The top-left and bottom-right corners and the centre are not zones.
    TapZoneLayout.L_SHAPE -> listOf(
        poly(TapAction.PREVIOUS, 0f, 0.3f, 0.3f, 0.3f, 0.3f, 0.7f, 0.7f, 0.7f, 0.7f, 1f, 0f, 1f),
        poly(TapAction.NEXT, 0.3f, 0f, 1f, 0f, 1f, 0.7f, 0.7f, 0.7f, 0.7f, 0.3f, 0.3f, 0.3f)
    )
    // Narrow back column, wide next area, a top strip left free for the controls.
    TapZoneLayout.KINDLE -> listOf(
        rect(TapAction.PREVIOUS, 0f, 0f, 0.2f, 1f),
        rect(TapAction.NEXT, 0.2f, 0.15f, 1f, 1f)
    )
    TapZoneLayout.CUSTOM -> emptyList()
}

val defaultTapRegions: List<TapRegion> = presetRegions(TapZoneLayout.EDGES)

/**
 * Regions for the current settings, mirrored horizontally when reading RTL
 * (custom zones only when [mirrorCustomInRtl] is on).
 */
internal fun tapRegionsFor(
    layout: TapZoneLayout,
    custom: List<CustomTapZone>,
    mirrorCustomInRtl: Boolean,
    isRtl: Boolean
): List<TapRegion> {
    val base = if (layout == TapZoneLayout.CUSTOM) {
        custom.map { TapRegion(it.action, it.points) }
    } else {
        presetRegions(layout)
    }
    val mirror = isRtl && (layout != TapZoneLayout.CUSTOM || mirrorCustomInRtl)
    return if (mirror) {
        base.map { region -> region.copy(points = region.points.map { NormPoint(1f - it.x, it.y) }) }
    } else {
        base
    }
}

/**
 * Classifies a tap. Later regions sit on top of earlier ones, so the last match wins.
 * A tap outside every region (or a non-positive size) is [TapZone.CENTER].
 * Shared by the page ([ImageReader]) and the bubble magnifier overlay so both agree on the zones.
 */
internal fun classifyTap(x: Float, y: Float, width: Float, height: Float, regions: List<TapRegion>): TapZone {
    if (width <= 0f || height <= 0f) return TapZone.CENTER
    val nx = x / width
    val ny = y / height
    val hit = regions.lastOrNull { containsPoint(it.points, nx, ny) } ?: return TapZone.CENTER
    return if (hit.action == TapAction.NEXT) TapZone.NEXT else TapZone.PREVIOUS
}

/** Ray-casting point-in-polygon test. Fewer than 3 points never contain anything. */
internal fun containsPoint(points: List<NormPoint>, x: Float, y: Float): Boolean {
    if (points.size < 3) return false
    var inside = false
    var j = points.lastIndex
    for (i in points.indices) {
        val pi = points[i]
        val pj = points[j]
        if ((pi.y > y) != (pj.y > y) &&
            x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/** A point inside the polygon, as close to its vertex average as a coarse grid allows. Used for labels. */
internal fun labelPoint(points: List<NormPoint>): NormPoint {
    if (points.isEmpty()) return NormPoint(0.5f, 0.5f)
    val cx = points.map { it.x }.average().toFloat()
    val cy = points.map { it.y }.average().toFloat()
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    var best = NormPoint(cx, cy)
    var bestDist = Float.MAX_VALUE
    val steps = 16
    for (i in 0..steps) {
        for (j in 0..steps) {
            val x = minX + (maxX - minX) * i / steps
            val y = minY + (maxY - minY) * j / steps
            if (containsPoint(points, x, y)) {
                val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                if (d < bestDist) {
                    bestDist = d
                    best = NormPoint(x, y)
                }
            }
        }
    }
    return best
}