package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import com.zemmy.sigurvita.core.datastore.SeriesReadContext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.zemmy.sigurvita.R
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.zemmy.sigurvita.core.model.Series

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    if (!url.isNullOrBlank()) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build()
        )
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
            CoverPlaceholder(title = contentDescription, modifier = modifier)
        } else {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        CoverPlaceholder(title = contentDescription, modifier = modifier)
    }
}

@Composable
fun CoverPlaceholder(
    title: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                if (!title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
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

    Column(modifier = modifier) {
        if (showProgressBar && totalPages > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 3.dp else 4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when {
                    isRead -> MaterialTheme.colorScheme.primary
                    isPartiallyRead -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = if (compact) 2.dp else 6.dp, vertical = 2.dp)
        ) {
            val issuePart = if (totalIssues > 0) {
                if (isRead) "#$totalIssues"
                else if (isPartiallyRead) "#$issuesRead/$totalIssues"
                else "#$totalIssues"
            } else ""

            val pagePart = if (totalPages > 0) {
                if (isRead) ""
                else if (isPartiallyRead) "$pagesRead/${totalPages}p"
                else "${totalPages}p"
            } else ""

            val textStr = when {
                isRead -> if (issuePart.isNotEmpty()) "Completely Read ($issuePart)" else "Completely Read"
                isPartiallyRead -> listOf(issuePart, pagePart).filter { it.isNotEmpty() }.joinToString(" • ")
                totalPages > 0 || totalIssues > 0 -> listOf(issuePart, pagePart).filter { it.isNotEmpty() }.joinToString(" • ")
                else -> "Unread"
            }

            Text(
                text = textStr,
                style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                        else MaterialTheme.typography.labelSmall,
                color = when {
                    isRead -> MaterialTheme.colorScheme.primary
                    isPartiallyRead -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isRead || isPartiallyRead) FontWeight.Bold else FontWeight.Normal
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 2.dp)
    ) {
        if (isRead) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Read",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (totalIssues > 0) "READ (#$totalIssues)" else "READ",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (isPartiallyRead) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            val label = if (totalIssues > 0) "#$issuesRead/$totalIssues" else "${(progress * 100).toInt()}%"
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.tertiary
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
        modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        modifier.clickable(onClick = onClick)
    }

    Card(
        modifier = clickModifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                CoverImage(
                    url = series.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f),
                    contentDescription = series.name
                )

                val isListContext = readContext?.originType == "READING_LIST"
                val contextLabel = when {
                    isListContext -> {
                        val title = readContext.readingListTitle.ifBlank { "List" }
                        if (title.length > 10) "List: ${title.take(8)}…" else "List: $title"
                    }
                    readContext?.originType == "LIBRARY" -> "Library"
                    else -> null
                }

                if (!contextLabel.isNullOrBlank()) {
                    Surface(
                        color = if (isListContext) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f)
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isListContext) Icons.Default.Bookmark else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isListContext) MaterialTheme.colorScheme.onTertiaryContainer
                                       else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = contextLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                ),
                                color = if (isListContext) MaterialTheme.colorScheme.onTertiaryContainer
                                       else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (series.isFullyRead) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completely Read",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "READ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else if (series.pagesRead > 0 && series.totalPages > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = "${(series.pagesRead * 100 / series.totalPages)}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = series.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            SeriesReadIndicator(
                pagesRead = series.pagesRead,
                totalPages = series.totalPages,
                issuesRead = series.issuesRead,
                totalIssues = series.totalIssues,
                isRead = series.isFullyRead,
                modifier = Modifier.padding(bottom = 4.dp)
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
        CircularProgressIndicator()
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            if (onRetry != null) {
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
    Surface(
        color = if (isRead) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = if (isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isRead) "Read" else if (isNextUp) "▶ Next to Read" else "Unread",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
