package com.zemmy.sigurvita.ui.screens.downloads

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.ui.components.CoverImage
import androidx.compose.ui.res.stringResource
import com.zemmy.sigurvita.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    onBackClick: () -> Unit,
    viewModel: DownloadQueueViewModel = viewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(downloads, key = { it.chapterId }) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverImage(
                                url = task.coverUrl,
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(85.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (task.seriesName.isNotBlank()) {
                                    Text(
                                        text = "${task.libraryName} • ${task.seriesName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
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
                                    IconButton(onClick = { viewModel.pauseDownload(task.chapterId) }) {
                                        Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.pause_task))
                                    }
                                } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                                    IconButton(onClick = { viewModel.resumeDownload(task.chapterId) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.resume_task))
                                    }
                                }
                                IconButton(onClick = { viewModel.cancelDownload(task.chapterId) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_delete_task))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
