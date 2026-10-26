package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.util.SeriesExternalLink
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.theme.ColorScapeColors
import com.zemmy.sigurvita.ui.theme.headerBrush
import com.zemmy.sigurvita.ui.theme.onAccent

/**
 * The pinned top section: app bar, cover, summary teaser, progress and the action buttons.
 *
 * The app bar lives inside this gradient container so the ColorScape wash starts at the very top of the
 * screen (behind the status bar) instead of behind a flat bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesHeader(
    series: Series,
    state: SeriesDetailUiState,
    seriesColors: ColorScapeColors?,
    seriesAccent: Color?,
    externalLink: SeriesExternalLink?,
    isSeriesCompleted: Boolean,
    onBackClick: () -> Unit,
    onShowInfo: () -> Unit,
    onLibraryClick: (libraryId: Int) -> Unit,
    onResume: () -> Unit,
    onMarkReadClick: () -> Unit,
    onToggleIncognito: () -> Unit,
    onBulkDownloadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (seriesColors != null) Modifier.background(seriesColors.headerBrush()) else Modifier)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = series.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            CoverImage(
                url = series.coverUrl,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .width(104.dp)
                    .height(150.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 150.dp)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowInfo() }
                    ) {
                        if (series.summary?.isNotBlank() == true) {
                            Text(
                                text = series.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Read more...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = seriesAccent ?: MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.isMangaLibrary) "${state.chaptersRead} / ${state.totalChapters} chapters read" else "${state.chaptersRead} / ${state.totalChapters} issues read",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = seriesAccent ?: MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${state.pagesRead} / ${state.totalPages} pages)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.totalPages > 0) {
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = {
                                (state.pagesRead.toFloat() / state.totalPages).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = seriesAccent ?: MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                    if (state.libraryName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { onLibraryClick(state.libraryId) }
                        ) {
                            Text(
                                text = "Library: ${state.libraryName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onResume,
                        colors = if (state.isIncognitoMode) {
                            ButtonDefaults.buttonColors(
                                containerColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.incognito,
                                contentColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onIncognito
                            )
                        } else {
                            seriesAccent?.let { ButtonDefaults.buttonColors(containerColor = it, contentColor = it.onAccent()) }
                                ?: ButtonDefaults.buttonColors()
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        externalLink?.let { link -> ExternalLinkButton(link) }
                        IconButton(
                            onClick = onMarkReadClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = if (isSeriesCompleted) "Mark Unread" else "Mark Read",
                                tint = if (isSeriesCompleted) com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.completed
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleIncognito,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Incognito Mode",
                                tint = if (state.isIncognitoMode) com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.incognito
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onBulkDownloadClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Bulk Download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
