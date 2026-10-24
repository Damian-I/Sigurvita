package com.zemmy.sigurvita.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.zemmy.sigurvita.core.model.AppThemeMode

private val SigurvitaLightColorScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = Graphite0,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary = SlateIndigo600,
    onSecondary = Graphite0,
    secondaryContainer = Graphite150,
    onSecondaryContainer = Graphite700,
    tertiary = Cyan700,
    onTertiary = Graphite0,
    tertiaryContainer = Color(0xFFC9F2FA),
    onTertiaryContainer = Color(0xFF002F3A),
    background = Graphite50,
    onBackground = Graphite900,
    surface = Graphite0,
    onSurface = Graphite900,
    surfaceVariant = Graphite150,
    onSurfaceVariant = Graphite500,
    surfaceContainerLowest = Graphite25,
    surfaceContainerLow = Graphite50,
    surfaceContainer = Graphite100,
    surfaceContainerHigh = Graphite150,
    surfaceContainerHighest = Graphite200,
    inverseSurface = Ink750,
    inverseOnSurface = Ink100,
    outline = Graphite200,
    outlineVariant = Color(0xFFD7DCE8),
    scrim = Ink0
)

private val SigurvitaDarkColorScheme = darkColorScheme(
    primary = Indigo300,
    onPrimary = Indigo900,
    primaryContainer = Indigo800,
    onPrimaryContainer = Indigo100,
    secondary = SlateIndigo300,
    onSecondary = Color(0xFF1C2436),
    secondaryContainer = Ink650,
    onSecondaryContainer = Ink300,
    tertiary = Cyan300,
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF004A58),
    onTertiaryContainer = Color(0xFFA9F0FF),
    background = Ink850,
    onBackground = Ink100,
    surface = Ink750,
    onSurface = Ink100,
    surfaceVariant = Ink600,
    onSurfaceVariant = Ink300,
    surfaceContainerLowest = Ink900,
    surfaceContainerLow = Ink800,
    surfaceContainer = Ink750,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink650,
    inverseSurface = Graphite50,
    inverseOnSurface = Graphite900,
    outline = Ink500,
    outlineVariant = Ink600,
    scrim = Ink0
)

private val SigurvitaAmoledColorScheme = darkColorScheme(
    primary = Indigo300,
    onPrimary = Indigo900,
    primaryContainer = Color(0xFF1A2F88),
    onPrimaryContainer = Indigo100,
    secondary = SlateIndigo300,
    onSecondary = Color(0xFF131A2B),
    secondaryContainer = Ink800,
    onSecondaryContainer = Ink300,
    tertiary = Cyan300,
    onTertiary = Color(0xFF002830),
    tertiaryContainer = Color(0xFF003B47),
    onTertiaryContainer = Color(0xFFA9F0FF),
    background = Ink0,
    onBackground = Ink100,
    surface = Ink0,
    onSurface = Ink100,
    surfaceVariant = Ink800,
    onSurfaceVariant = Ink300,
    surfaceContainerLowest = Ink0,
    surfaceContainerLow = Ink900,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink750,
    surfaceContainerHighest = Ink700,
    inverseSurface = Graphite50,
    inverseOnSurface = Graphite900,
    outline = Ink500,
    outlineVariant = Color(0xFF2A3446),
    scrim = Ink0
)

data class SigurvitaSemanticColors(
    val success: androidx.compose.ui.graphics.Color,
    val onSuccess: androidx.compose.ui.graphics.Color,
    val successContainer: androidx.compose.ui.graphics.Color,
    val onSuccessContainer: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val onWarning: androidx.compose.ui.graphics.Color,
    val warningContainer: androidx.compose.ui.graphics.Color,
    val onWarningContainer: androidx.compose.ui.graphics.Color,
    val info: androidx.compose.ui.graphics.Color,
    val onInfo: androidx.compose.ui.graphics.Color,
    val infoContainer: androidx.compose.ui.graphics.Color,
    val onInfoContainer: androidx.compose.ui.graphics.Color,
    val inProgress: androidx.compose.ui.graphics.Color,
    val onInProgress: androidx.compose.ui.graphics.Color,
    val completed: androidx.compose.ui.graphics.Color,
    val onCompleted: androidx.compose.ui.graphics.Color,
    val downloaded: androidx.compose.ui.graphics.Color,
    val onDownloaded: androidx.compose.ui.graphics.Color,
    val notStarted: androidx.compose.ui.graphics.Color,
    val onNotStarted: androidx.compose.ui.graphics.Color,
    val incognito: androidx.compose.ui.graphics.Color,
    val onIncognito: androidx.compose.ui.graphics.Color,
    val nextUp: androidx.compose.ui.graphics.Color,
    val onNextUp: androidx.compose.ui.graphics.Color
)

private val LightSemanticColors = SigurvitaSemanticColors(
    success = SuccessLight,
    onSuccess = Graphite0,
    successContainer = Color(0xFFD7F5E6),
    onSuccessContainer = Color(0xFF002B18),
    warning = WarningLight,
    onWarning = Graphite0,
    warningContainer = Color(0xFFFFF0CC),
    onWarningContainer = Color(0xFF2B1A00),
    info = InfoLight,
    onInfo = Graphite0,
    infoContainer = Indigo100,
    onInfoContainer = Indigo900,
    inProgress = Cyan700,
    onInProgress = Graphite0,
    completed = SuccessLight,
    onCompleted = Graphite0,
    downloaded = Indigo600,
    onDownloaded = Graphite0,
    notStarted = Graphite500,
    onNotStarted = Graphite0,
    incognito = Color(0xFF6B46C1),
    onIncognito = Graphite0,
    nextUp = Indigo600,
    onNextUp = Graphite0
)

private val DarkSemanticColors = SigurvitaSemanticColors(
    success = SuccessDark,
    onSuccess = Color(0xFF003820),
    successContainer = Color(0xFF00522F),
    onSuccessContainer = Color(0xFFB9F6D2),
    warning = WarningDark,
    onWarning = Color(0xFF362400),
    warningContainer = Color(0xFF553C00),
    onWarningContainer = Color(0xFFFFE3A3),
    info = InfoDark,
    onInfo = Indigo900,
    infoContainer = Indigo800,
    onInfoContainer = Indigo100,
    inProgress = Cyan300,
    onInProgress = Color(0xFF00333C),
    completed = SuccessDark,
    onCompleted = Color(0xFF003820),
    downloaded = Indigo300,
    onDownloaded = Indigo900,
    notStarted = Ink300,
    onNotStarted = Ink800,
    incognito = Color(0xFFB9A6FF),
    onIncognito = Color(0xFF2F1A5C),
    nextUp = Indigo300,
    onNextUp = Indigo900
)

private val AmoledSemanticColors = DarkSemanticColors

val LocalSigurvitaColors = staticCompositionLocalOf { LightSemanticColors }
val LocalIsAmoled = staticCompositionLocalOf { false }

@Composable
fun SigurvitaTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.AMOLED -> true
    }
    val isAmoled = themeMode == AppThemeMode.AMOLED

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isAmoled) {
                dynamicDarkColorScheme(context).copy(
                    background = Ink0,
                    surface = Ink0,
                    surfaceContainerLowest = Ink0,
                    surfaceContainerLow = Ink900,
                    surfaceContainer = Ink800,
                    surfaceContainerHigh = Ink750,
                    surfaceContainerHighest = Ink700
                )
            } else if (useDark) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        isAmoled -> SigurvitaAmoledColorScheme
        useDark -> SigurvitaDarkColorScheme
        else -> SigurvitaLightColorScheme
    }

    val semanticColors = when {
        isAmoled -> AmoledSemanticColors
        useDark -> DarkSemanticColors
        else -> LightSemanticColors
    }

    CompositionLocalProvider(
        LocalSigurvitaColors provides semanticColors,
        LocalIsAmoled provides isAmoled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = SigurvitaShapes,
            typography = Typography,
            content = content
        )
    }
}

object SigurvitaTheme {
    val colors: SigurvitaSemanticColors
        @Composable
        get() = LocalSigurvitaColors.current

    val isAmoled: Boolean
        @Composable
        get() = LocalIsAmoled.current
}
