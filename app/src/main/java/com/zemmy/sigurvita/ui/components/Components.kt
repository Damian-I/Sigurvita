package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.datastore.SeriesReadContext
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.ui.theme.Dimens
import com.zemmy.sigurvita.ui.theme.SigurvitaTheme
import com.zemmy.sigurvita.ui.theme.Spacing

fun coverImageRequest(context: android.content.Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(coil.size.Size.ORIGINAL)
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .crossfade(false)
        .build()

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = MaterialTheme.shapes.small
) {
    val context = LocalContext.current
    if (!url.isNullOrBlank()) {
        val painter = rememberAsyncImagePainter(
            model = remember(url) { coverImageRequest(context, url) }
        )
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
            CoverPlaceholder(title = contentDescription, modifier = modifier, shape = shape)
        } else {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = modifier.clip(shape),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        CoverPlaceholder(title = contentDescription, modifier = modifier, shape = shape)
    }
}

@Composable
fun CoverPlaceholder(
    title: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(Spacing.sm)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                if (!title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesReadIndicator(
    pagesRead: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    issuesRead: Int = 0,
    totalIssues: Int = 0,
    isRead: Boolean = totalPages in 1..pagesRead,
    showProgressBar: Boolean = true,
    compact: Boolean = false
) {
    val progress = if (totalPages > 0) (pagesRead.toFloat() / totalPages).coerceIn(0f, 1f) else 0f
    val isPartiallyRead = (pagesRead > 0 || issuesRead > 0) && !isRead
    val colors = SigurvitaTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            val issuePart = if (totalIssues > 0) {
                if (isRead) "#$totalIssues issues"
                else if (isPartiallyRead) "$issuesRead/$totalIssues issues"
                else "#$totalIssues issues"
            } else ""

            val pagePart = if (totalPages > 0) {
                if (isRead) ""
                else if (isPartiallyRead) "$pagesRead/$totalPages pgs"
                else "$totalPages pgs"
            } else ""

            val leftText = when {
                isRead -> if (issuePart.isNotEmpty()) "Read ($issuePart)" else "Read"
                isPartiallyRead -> issuePart.ifEmpty { "$pagesRead pgs" }
                else -> issuePart.ifEmpty { "Unread" }
            }

            val statusColor = when {
                isRead -> colors.completed
                isPartiallyRead -> colors.inProgress
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                text = leftText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = if (isRead || isPartiallyRead) FontWeight.SemiBold else FontWeight.Normal
            )

            if (pagePart.isNotEmpty()) {
                Text(
                    text = pagePart,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = if (isRead || isPartiallyRead) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        if (showProgressBar && totalPages > 0) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 2.dp else 3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isRead) SigurvitaTheme.colors.completed else colors.inProgress,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun SidebarReadingListIndicator(
    pagesRead: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    issuesRead: Int = 0,
    totalIssues: Int = 0
) {
    if (totalPages <= 0 && totalIssues <= 0) return
    val isRead = (totalPages in 1..pagesRead) || (totalIssues in 1..issuesRead)
    val isPartiallyRead = (pagesRead > 0 || issuesRead > 0) && !isRead
    val progress = if (totalPages > 0) (pagesRead.toFloat() / totalPages).coerceIn(0f, 1f) else 0f
    val colors = SigurvitaTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = Spacing.xxs)
    ) {
        if (isRead) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Read",
                tint = colors.completed,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = if (totalIssues > 0) "READ ($totalIssues issues)" else "READ",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.completed
            )
        } else if (isPartiallyRead) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = colors.inProgress,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            val label = if (totalIssues > 0) "#$issuesRead/$totalIssues issues" else "${(progress * 100).toInt()}%"
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.inProgress,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

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
                    top = Spacing.sm,
                    bottom = Spacing.sm
                )
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 2500
                    )
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
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

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xxl)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
fun ReadStatusBadge(
    isRead: Boolean,
    isNextUp: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SigurvitaTheme.colors
    val container = when {
        isRead -> MaterialTheme.colorScheme.surfaceContainerHigh
        isNextUp -> colors.nextUp.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        isRead -> colors.completed
        isNextUp -> colors.nextUp
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
        ) {
            Icon(
                imageVector = if (isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = if (isRead) "Read" else if (isNextUp) "Next up" else "Unread",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = content
            )
        }
    }
}
