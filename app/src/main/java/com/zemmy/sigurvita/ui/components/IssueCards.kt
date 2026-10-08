package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.model.DownloadTask

@Immutable
data class IssueUi(
    val id: Int,
    val title: String,
    val subtitle: String? = null,
    val coverUrl: String?,
    val pages: Int,
    val pagesRead: Int,
    val isRead: Boolean,
    val isNextUp: Boolean,
    val badges: List<ListBadge> = emptyList(),
    val download: DownloadTask? = null,
    val isDownloaded: Boolean = false,
    val canDownload: Boolean = true
) {
    val showProgress: Boolean
        get() = pagesRead > 0 && pages > 0 && pagesRead < pages && !isRead
}

@Composable
private fun nextUpBorder(isNextUp: Boolean) =
    if (isNextUp) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary)) else null

@Composable
fun IssueRow(
    issue: IssueUi,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteLocal: () -> Unit,
    onEraClick: (String) -> Unit,
    onReadingListClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    coverSize: DpSize = DpSize(60.dp, 85.dp),
    contentPadding: Dp = 8.dp
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        border = nextUpBorder(issue.isNextUp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (issue.isNextUp) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(url = issue.coverUrl, modifier = Modifier.size(coverSize))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier
                )
                issue.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (onSubtitleClick != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier
                    )
                }
                if (issue.badges.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    ReadingListBadgeRow(issue.badges, onEraClick, onReadingListClick)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (issue.showProgress) {
                        Text(
                            text = "${issue.pagesRead} / ${issue.pages} pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    ReadStatusBadge(isRead = issue.isRead, isNextUp = issue.isNextUp, onClick = onToggleRead)
                }
            }
            if (issue.canDownload) {
                DownloadStatusButton(
                    task = issue.download,
                    isDownloaded = issue.isDownloaded,
                    onDownload = onDownload,
                    onCancel = onCancelDownload,
                    onDelete = onDeleteLocal,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun IssueGridCard(
    issue: IssueUi,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteLocal: () -> Unit,
    onEraClick: (String) -> Unit,
    onReadingListClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        border = nextUpBorder(issue.isNextUp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (issue.isNextUp) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CoverImage(url = issue.coverUrl, modifier = Modifier.fillMaxWidth().aspectRatio(0.7f))
            Spacer(Modifier.height(4.dp))
            Text(
                text = issue.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (issue.badges.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                ReadingListBadgeRow(issue.badges, onEraClick, onReadingListClick)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (issue.isRead) "• Read" else if (issue.isNextUp) "▶ Next" else "Unread",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        issue.isRead -> MaterialTheme.colorScheme.onSurfaceVariant
                        issue.isNextUp -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                if (issue.canDownload) {
                    DownloadStatusButton(
                        task = issue.download,
                        isDownloaded = issue.isDownloaded,
                        onDownload = onDownload,
                        onCancel = onCancelDownload,
                        onDelete = onDeleteLocal,
                        compact = true
                    )
                }
            }
        }
    }
}
