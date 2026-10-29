package com.zemmy.sigurvita.core.model

import androidx.annotation.StringRes
import com.zemmy.sigurvita.R
import kotlinx.serialization.Serializable

enum class ReadingMode(@param:StringRes val stringRes: Int) {
    RTL(R.string.reading_mode_rtl),
    LTR(R.string.reading_mode_ltr),
    WEBTOON(R.string.reading_mode_webtoon)
}

enum class FitMode(@param:StringRes val stringRes: Int) {
    FIT_WIDTH(R.string.fit_mode_width),
    FIT_HEIGHT(R.string.fit_mode_height),
    FIT_SCREEN(R.string.fit_mode_screen)
}

enum class ReaderOrientation(@param:StringRes val stringRes: Int) {
    FREE(R.string.orientation_free),
    PORTRAIT(R.string.orientation_portrait),
    LOCKED_PORTRAIT(R.string.orientation_locked_portrait),
    REVERSE_PORTRAIT(R.string.orientation_reverse_portrait),
    LANDSCAPE(R.string.orientation_landscape),
    LOCKED_LANDSCAPE(R.string.orientation_locked_landscape)
}

enum class ReaderBackground(@param:StringRes val stringRes: Int) {
    BLACK(R.string.background_black),
    GRAY(R.string.background_gray),
    WHITE(R.string.background_white),
    AUTO(R.string.background_auto)
}

enum class AppThemeMode(@param:StringRes val stringRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    AMOLED(R.string.theme_amoled)
}

enum class AutoDeleteOption(@param:StringRes val stringRes: Int) {
    NEVER(R.string.auto_delete_never),
    IMMEDIATELY(R.string.auto_delete_immediately),
    AFTER_1_ISSUE(R.string.auto_delete_after_1),
    AFTER_2_ISSUES(R.string.auto_delete_after_2),
    AFTER_5_ISSUES(R.string.auto_delete_after_5)
}

enum class TapZoneLayout(@param:StringRes val stringRes: Int) {
    EDGES(R.string.tap_layout_edges),
    TOP_BOTTOM(R.string.tap_layout_top_bottom),
    L_SHAPE(R.string.tap_layout_l_shape),
    KINDLE(R.string.tap_layout_kindle),
    CUSTOM(R.string.tap_layout_custom)
}

@Serializable
enum class TapAction { PREVIOUS, NEXT }

/** A point as a fraction (0..1) of the screen width/height. */
@Serializable
data class NormPoint(val x: Float, val y: Float)

/** A user-drawn zone: a polygon (a rectangle is 4 points) plus what tapping it does. */
@Serializable
data class CustomTapZone(val action: TapAction, val points: List<NormPoint>)
data class ReaderSettings(
    val readingMode: ReadingMode = ReadingMode.RTL,
    val fitMode: FitMode = FitMode.FIT_WIDTH,
    val volumeKeyNavigationEnabled: Boolean = true,
    val volumeKeyInvert: Boolean = false,
    val touchEdgeNavigationEnabled: Boolean = true,
    val tapZoneLayout: TapZoneLayout = TapZoneLayout.EDGES,
    val showTapZones: Boolean = true,
    val customTapZones: List<CustomTapZone> = emptyList(),
    val customZonesMirrorInRtl: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoDeleteOption: AutoDeleteOption = AutoDeleteOption.NEVER,
    val isIncognitoMode: Boolean = false,
    val speechBubbleDetectionEnabled: Boolean = true,
    val speechBubbleBoxesEnabled: Boolean = true,
    val speechBubbleConfidence: Float = 0.10f,
    val panelAwareOrderEnabled: Boolean = true,
    val panelDebugOverlayEnabled: Boolean = false,
    val largePageAspectThreshold: Float = 1.1f,
    val showPageNumber: Boolean = false,
    val orientation: ReaderOrientation = ReaderOrientation.FREE,
    val readerBackground: ReaderBackground = ReaderBackground.BLACK,
    val colorScapeEnabled: Boolean = true
)

data class AppearanceSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)
