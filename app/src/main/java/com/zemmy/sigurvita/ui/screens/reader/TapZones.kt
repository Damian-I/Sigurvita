package com.zemmy.sigurvita.ui.screens.reader

/** What a tap means once the screen is split into two page-transition zones and a centre. */
internal enum class TapZone { PREVIOUS, NEXT, CENTER }

/** The left transition zone ends here, as a fraction of the width. */
private const val LEFT_ZONE_END = 0.3f

/** The right transition zone starts here, as a fraction of the width. */
private const val RIGHT_ZONE_START = 0.7f

/**
 * Classifies a horizontal tap position: the outer 30% on each side are the page-transition zones,
 * everything between is the centre. Which edge means "next" follows the reading direction
 * (RTL: the left edge goes to the next page).
 *
 * Shared by the page ([ImageReader]) and the speech-bubble magnifier overlay so both agree on exactly
 * where the zones are. A non-positive [width] is always [TapZone.CENTER].
 */
internal fun classifyTapZone(x: Float, width: Float, isRtl: Boolean): TapZone {
    if (width <= 0f) return TapZone.CENTER
    return when {
        x < width * LEFT_ZONE_END -> if (isRtl) TapZone.NEXT else TapZone.PREVIOUS
        x > width * RIGHT_ZONE_START -> if (isRtl) TapZone.PREVIOUS else TapZone.NEXT
        else -> TapZone.CENTER
    }
}
