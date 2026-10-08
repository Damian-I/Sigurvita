package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.DownloadTask

@Composable
fun DownloadStatusButton(
    task: DownloadTask?,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val buttonSize = if (compact) 28.dp else 48.dp
    val iconSize = if (compact) 16.dp else 24.dp
    val ringSize = if (compact) 26.dp else 38.dp
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            task != null && (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) -> {
                val progress = if (task.totalPages > 0) task.downloadedPages.toFloat() / task.totalPages else 0f
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(ringSize),
                    strokeWidth = if (compact) 2.dp else 3.dp
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        tint = primary,
                        modifier = Modifier.size(if (compact) 12.dp else 18.dp)
                    )
                }
            }
            task?.status == DownloadStatus.FAILED -> {
                IconButton(onClick = onDownload, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Download failed - tap to retry",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            isDownloaded -> {
                IconButton(onClick = onDelete, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Downloaded - tap to delete",
                        tint = primary,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            else -> {
                IconButton(onClick = onDownload, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else primary,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
