package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.datastore.SeriesReadContext
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.ui.theme.Dimens
import com.zemmy.sigurvita.ui.theme.SigurvitaTheme
import com.zemmy.sigurvita.ui.theme.Spacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    readContext: SeriesReadContext? = null
) {
    val clickModifier = if (onLongClick != null) {
        modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        modifier.clickable(onClick = onClick)
    }

    Card(
        modifier = clickModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column {
            Box {
                CoverImage(
                    url = series.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(Dimens.CoverAspect),
                    contentDescription = series.name
                )

                val isListContext = readContext?.originType == "READING_LIST"
                val contextLabel = when {
                    isListContext -> cleanReadingListTitle(readContext.readingListTitle).ifBlank { "List" }
                    readContext?.originType == "LIBRARY" -> "Library"
                    else -> null
                }

                if (!contextLabel.isNullOrBlank()) {
                    OverlayBadge(
                        text = if (isListContext) "List: $contextLabel" else contextLabel,
                        icon = if (isListContext) Icons.Default.Bookmark else Icons.Default.Folder,
                        container = if (isListContext) SigurvitaTheme.colors.incognito.copy(alpha = 0.92f)
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                        content = if (isListContext) SigurvitaTheme.colors.onIncognito
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .widthIn(max = 96.dp)
                    )
                }

                if (series.isFullyRead) {
                    OverlayBadge(
                        text = "READ",
                        icon = Icons.Default.CheckCircle,
                        container = SigurvitaTheme.colors.successContainer.copy(alpha = 0.94f),
                        content = SigurvitaTheme.colors.onSuccessContainer,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                } else if (series.pagesRead > 0 && series.totalPages > 0) {
                    OverlayBadge(
                        text = "${(series.pagesRead * 100 / series.totalPages)}%",
                        container = SigurvitaTheme.colors.infoContainer.copy(alpha = 0.94f),
                        content = SigurvitaTheme.colors.onInfoContainer,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(
                    start = Spacing.sm,
                    end = Spacing.sm,
                    top = Spacing.xs,
                    bottom = Spacing.sm
                )
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 2500
                    )
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                SeriesReadIndicator(
                    pagesRead = series.pagesRead,
                    totalPages = series.totalPages,
                    issuesRead = series.issuesRead,
                    totalIssues = series.totalIssues,
                    isRead = series.isFullyRead,
                    compact = true
                )
            }
        }
    }
}

@Composable
private fun OverlayBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 10.dp, bottomStart = 0.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (icon == null && text.contains(":")) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2500)
                } else Modifier
            )
        }
    }
}
