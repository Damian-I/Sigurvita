package com.zemmy.sigurvita.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

/** Primary color nudged until it is readable (3:1) on the current surface. */
@Composable
fun ColorScapeColors.accent(): Color = primary.ensureContrast(MaterialTheme.colorScheme.surface, 3f)

/** Top-down wash: primary -> secondary -> transparent. Low alpha keeps text readable in light and dark. */
fun ColorScapeColors.headerBrush(): Brush = Brush.verticalGradient(
    listOf(primary.copy(alpha = 0.32f), secondary.copy(alpha = 0.12f), Color.Transparent)
)

fun Color.onAccent(): Color = if (luminance() > 0.5f) Color.Black else Color.White

/** SeriesDetailViewModel builds placeholder chapters (id == volumeId, seriesId == 0) for empty volumes. */
fun Chapter.isSyntheticPlaceholder(): Boolean = seriesId == 0 && id == volumeId

@Composable
fun colorScapeBorder(isNextUp: Boolean, accent: Color?): BorderStroke? = when {
    accent != null && isNextUp -> BorderStroke(2.dp, accent)
    accent != null -> BorderStroke(1.dp, accent.copy(alpha = 0.35f))
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