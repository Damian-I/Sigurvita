package com.zemmy.sigurvita.ui.screens.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.ui.components.CoverImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    onBackClick: () -> Unit,
    onChapterClick: ((chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int) -> Unit)? = null,
    viewModel: DownloadQueueViewModel = viewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

    val groupedDownloads = remember(downloads) {
        downloads.groupBy { it.seriesName.ifBlank { "Downloads" } }
    }

    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(groupedDownloads.keys) {
        expandedGroups = expandedGroups + groupedDownloads.keys
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (downloads.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.pause_all))
                        }
                    } else if (downloads.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }) {
                        IconButton(onClick = { viewModel.resumeAll() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.resume_all))
                        }
                    }
                    if (downloads.any { it.status == DownloadStatus.COMPLETED }) {
                        IconButton(onClick = { viewModel.clearCompleted() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_completed))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.no_download_tasks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                groupedDownloads.forEach { (seriesName, tasks) ->
                    val isExpanded = expandedGroups.contains(seriesName)
                    val completedCount = tasks.count { it.status == DownloadStatus.COMPLETED }
                    val failedCount = tasks.count { it.status == DownloadStatus.FAILED }
                    val libraryName = tasks.firstOrNull()?.libraryName ?: "Comics"

                    item(key = "group_$seriesName") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedGroups = if (isExpanded) {
                                                expandedGroups - seriesName
                                            } else {
                                                expandedGroups + seriesName
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = seriesName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$libraryName • $completedCount/${tasks.size} downloaded" +
                                                    if (failedCount > 0) " • $failedCount failed" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (failedCount > 0) {
                                        IconButton(
                                            onClick = {
                                                tasks.filter { it.status == DownloadStatus.FAILED }.forEach {
                                                    viewModel.resumeDownload(it.chapterId)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Retry Failed",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        tasks.forEach { task ->
                                            DownloadTaskItem(
                                                task = task,
                                                onPause = { viewModel.pauseDownload(task.chapterId) },
                                                onResume = { viewModel.resumeDownload(task.chapterId) },
                                                onCancel = { viewModel.cancelDownload(task.chapterId) },
                                                onChapterClick = onChapterClick
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onChapterClick: ((chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int) -> Unit)?
) {
    val isCompleted = task.status == DownloadStatus.COMPLETED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCompleted && onChapterClick != null) {
                    Modifier.clickable {
                        onChapterClick(task.chapterId, task.totalPages, task.seriesId, task.volumeId)
                    }
                } else Modifier
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(
                url = task.coverUrl,
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.status_format, task.status.name, task.downloadedPages, task.totalPages),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (task.status) {
                            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (isCompleted && onChapterClick != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Read",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Tap to read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!task.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.error_format, task.errorMessage ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        if (task.totalPages > 0) task.downloadedPages.toFloat() / task.totalPages else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.pause_task))
                    }
                } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                    IconButton(onClick = onResume) {
                        Icon(
                            imageVector = if (task.status == DownloadStatus.FAILED) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = if (task.status == DownloadStatus.FAILED) "Retry Task" else stringResource(R.string.resume_task),
                            tint = if (task.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_delete_task))
                }
            }
        }
    }
}
