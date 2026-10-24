package com.zemmy.sigurvita.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.dto.ColorScapeDto
import com.zemmy.sigurvita.core.data.repository.ColorScapeKind
import com.zemmy.sigurvita.core.model.Chapter

@Immutable
data class ColorScapeColors(val primary: Color, val secondary: Color)

/**
 * A contrast-checked accent set derived from a series/volume/chapter ColorScape.
 *
 * Screens should consume this through [LocalColorScapeAccent] instead of using
 * [ColorScapeColors.primary] directly, so text and controls stay legible on the
 * current theme surface.
 */
@Immutable
data class ColorScapeAccent(
    val source: ColorScapeColors,
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val tint: Color,
    val gradient: Brush,
    val headerBrush: Brush
)

val LocalColorScapeAccent = staticCompositionLocalOf<ColorScapeAccent?> { null }

private fun parseHex(raw: String?): Color? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    return try {
        Color(android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s"))
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun ColorScapeDto.toColors(): ColorScapeColors? {
    val p = parseHex(primary) ?: parseHex(secondary) ?: return null
    return ColorScapeColors(p, parseHex(secondary) ?: p)
}

private fun contrast(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

private fun Color.ensureContrast(background: Color, min: Float): Color {
    val target = if (background.luminance() > 0.5f) Color.Black else Color.White
    var c = this
    var steps = 0
    while (contrast(c, background) < min && steps < 10) {
        c = lerp(c, target, 0.1f)
        steps++
    }
    return c
}

fun Color.onAccent(): Color = if (luminance() > 0.5f) Color.Black else Color.White

/** Nudges this colour toward black/white until it is readable on [background]. */
fun Color.readableOn(background: Color, minContrast: Float = 3f): Color =
    ensureContrast(background, minContrast)

/** Builds a full accent set that is safe on the supplied scheme's surfaces. */
fun ColorScapeColors.toAccent(scheme: ColorScheme): ColorScapeAccent {
    val surface = scheme.surface
    val containerBase = scheme.surfaceContainerHigh
    val readableAccent = primary.ensureContrast(surface, 3f)
    val container = lerp(containerBase, primary, 0.18f)
    val onContainer = primary.ensureContrast(container, 4.5f)
    val tint = if (scheme.surface.luminance() < 0.5f) {
        lerp(Color.Black, primary, 0.16f)
    } else {
        lerp(Color.White, primary, 0.10f)
    }
    return ColorScapeAccent(
        source = this,
        accent = readableAccent,
        onAccent = readableAccent.onAccent(),
        container = container,
        onContainer = onContainer,
        tint = tint,
        gradient = Brush.linearGradient(
            listOf(
                primary.copy(alpha = 0.55f),
                secondary.copy(alpha = 0.30f),
                scheme.surface.copy(alpha = 0f)
            )
        ),
        headerBrush = Brush.verticalGradient(
            listOf(primary.copy(alpha = 0.34f), secondary.copy(alpha = 0.14f), Color.Transparent)
        )
    )
}

/** Scopes a series/volume/chapter accent to everything below it. */
@Composable
fun ColorScapeTheme(
    accent: ColorScapeAccent?,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalColorScapeAccent provides accent, content = content)
}

/** The current scoped accent, or the theme primary when ColorScape is unavailable. */
@Composable
fun colorScapeAccentOrPrimary(): Color =
    LocalColorScapeAccent.current?.accent ?: MaterialTheme.colorScheme.primary

/** Primary color nudged until it is readable (3:1) on the current surface. */
@Composable
fun ColorScapeColors.accent(): Color = primary.ensureContrast(MaterialTheme.colorScheme.surface, 3f)

/** Top-down wash: primary -> secondary -> transparent. Low alpha keeps text readable in light and dark. */
fun ColorScapeColors.headerBrush(): Brush = Brush.verticalGradient(
    listOf(primary.copy(alpha = 0.34f), secondary.copy(alpha = 0.14f), Color.Transparent)
)

/** SeriesDetailViewModel builds placeholder chapters (id == volumeId, seriesId == 0) for empty volumes. */
fun Chapter.isSyntheticPlaceholder(): Boolean = seriesId == 0 && id == volumeId

@Composable
fun colorScapeBorder(isNextUp: Boolean, accent: Color?): BorderStroke? = when {
    accent != null && isNextUp -> BorderStroke(2.dp, accent)
    accent != null -> BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    isNextUp -> CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary))
    else -> null
}

/** Null while loading, when disabled in Settings, on failure, or when the server has no colors. */
@Composable
fun rememberColorScape(kind: ColorScapeKind, id: Int): ColorScapeColors? {
    val repo = SigurvitaApp.instance.colorScapeRepository
    val enabled by repo.enabled.collectAsState()
    val colors by produceState<ColorScapeColors?>(
        initialValue = if (enabled && id > 0) repo.peek(kind, id)?.toColors() else null,
        kind, id, enabled
    ) {
        value = if (enabled && id > 0) repo.get(kind, id).getOrNull()?.toColors() else null
    }
    return colors
}

/** The scoped accent derived from [rememberColorScape], safe for the current theme. */
@Composable
fun rememberColorScapeAccent(kind: ColorScapeKind, id: Int): ColorScapeAccent? {
    val colors = rememberColorScape(kind, id) ?: return null
    val scheme = MaterialTheme.colorScheme
    return remember(colors, scheme) { colors.toAccent(scheme) }
}
