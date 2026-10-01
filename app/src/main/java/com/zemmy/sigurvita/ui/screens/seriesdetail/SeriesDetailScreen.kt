package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.MassDownloadDialog
import kotlin.math.abs

private fun formatVolumeHeader(name: String, number: Float): String {
    if (name.contains("100000") || abs(number) >= 10000f) {
        return "Issues"
    }
    return if (name.isNotBlank()) "Volume $name" else "Volume $number"
}

private fun formatChapterTitle(title: String, chapterNumber: String): String {
    val isSentinelNumber = chapterNumber.contains("100000") ||
            chapterNumber.toIntOrNull()?.let { abs(it) >= 10000 } == true

    val cleanNumber = if (!isSentinelNumber && chapterNumber.isNotBlank()) "Issue #$chapterNumber" else ""
    val cleanTitle = if (title.trim() == chapterNumber.trim()) "" else title

    return when {
        cleanNumber.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanNumber - $cleanTitle"
        cleanTitle.isNotBlank() -> cleanTitle
        cleanNumber.isNotBlank() -> cleanNumber
        else -> "Issue"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    onBackClick: () -> Unit,
    onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    viewModel: SeriesDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showMassDownloadDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, seriesId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadSeriesDetail(seriesId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showMassDownloadDialog) {
        val unreadCount = state.totalChapters - state.chaptersRead
        MassDownloadDialog(
            totalAvailableIssues = state.totalChapters,
            unreadIssuesCount = if (unreadCount > 0) unreadCount else state.totalChapters,
            onDismiss = { showMassDownloadDialog = false },
            onConfirmDownload = { count, unreadOnly ->
                viewModel.downloadBulk(count, unreadOnly)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.series?.name ?: "Series Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSearchActive() }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = { viewModel.toggleGridView() }) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List View"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleIncognitoMode() }) {
                        Icon(
                            imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Incognito Mode",
                            tint = if (state.isIncognitoMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showMassDownloadDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Mass Download")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else if (state.errorMessage != null) {
            ErrorState(
                message = state.errorMessage!!,
                onRetry = { viewModel.loadSeriesDetail(seriesId) },
                modifier = Modifier.padding(innerPadding)
            )
        } else if (state.series != null) {
            val series = state.series!!
            val isSeriesCompleted = series.isCompleted || (state.chaptersRead >= state.totalChapters && state.totalChapters > 0)
            val displayVolumes = state.filteredVolumes
            val allChapters = displayVolumes.flatMap { it.chapters }
            val nextToReadChapterId = allChapters.firstOrNull { !it.isRead }?.id

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (state.isIncognitoMode) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Incognito Mode Active: Reading progress is private and won't sync to Kavita.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        CoverImage(
                            url = series.coverUrl,
                            modifier = Modifier
                                .width(100.dp)
                                .height(145.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = series.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.chaptersRead} / ${state.totalChapters} issues read",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "(${state.pagesRead} / ${state.totalPages} pages)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val nextChapter = allChapters.firstOrNull { !it.isRead } ?: allChapters.firstOrNull()
                                        if (nextChapter != null) {
                                            onChapterClick(nextChapter.id, nextChapter.pages, nextChapter.volumeId)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume")
                                }
                                OutlinedButton(onClick = { viewModel.toggleMarkRead() }) {
                                    Text(if (isSeriesCompleted) "Mark Unread" else "Mark Read")
                                }
                            }
                            if (series.summary?.isNotBlank() == true) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = series.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (state.isSearchActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search issue or title...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (state.isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        displayVolumes.forEach { volume ->
                            item(span = { GridItemSpan(3) }) {
                                Text(
                                    text = formatVolumeHeader(volume.name, volume.number),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            items(volume.chapters, key = { it.id }) { chapter ->
                                val downloadTask = state.downloadTasks[chapter.id]
                                val isDownloaded = chapter.isDownloaded || (downloadTask?.status == DownloadStatus.COMPLETED)
                                val isNextUp = (chapter.id == nextToReadChapterId)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onChapterClick(chapter.id, chapter.pages, volume.id)
                                        },
                                    border = if (isNextUp) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary)) else null,
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isNextUp) 4.dp else 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CoverImage(
                                            url = chapter.coverUrl ?: series.coverUrl,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatChapterTitle(chapter.title, chapter.chapterNumber),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (chapter.isRead) "• Read" else if (isNextUp) "▶ Next" else "Unread",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (chapter.isRead) MaterialTheme.colorScheme.primary else if (isNextUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            IconButton(
                                                onClick = { viewModel.downloadChapter(chapter) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(displayVolumes, key = { it.id }) { volume ->
                            Text(
                                text = formatVolumeHeader(volume.name, volume.number),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                            volume.chapters.forEach { chapter ->
                                val downloadTask = state.downloadTasks[chapter.id]
                                val isDownloaded = chapter.isDownloaded || (downloadTask?.status == DownloadStatus.COMPLETED)
                                val isNextUp = (chapter.id == nextToReadChapterId)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            onChapterClick(chapter.id, chapter.pages, volume.id)
                                        },
                                    border = if (isNextUp) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary)) else null,
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isNextUp) 4.dp else 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CoverImage(
                                            url = chapter.coverUrl ?: series.coverUrl,
                                            modifier = Modifier
                                                .width(60.dp)
                                                .height(85.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = formatChapterTitle(chapter.title, chapter.chapterNumber),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val showProgress = chapter.pagesRead > 0 && chapter.pages > 0 && chapter.pagesRead < chapter.pages && !chapter.isRead
                                                Text(
                                                    text = if (showProgress) "${chapter.pagesRead} / ${chapter.pages} pages" else "${chapter.pages} pages",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (chapter.isRead) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "• Read",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.clickable { viewModel.toggleChapterRead(chapter) }
                                                    )
                                                } else if (isNextUp) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "▶ Next to Read",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.clickable { viewModel.toggleChapterRead(chapter) }
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "• Unread",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.clickable { viewModel.toggleChapterRead(chapter) }
                                                    )
                                                }
                                            }
                                        }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            when {
                                                downloadTask != null && (downloadTask.status == DownloadStatus.DOWNLOADING || downloadTask.status == DownloadStatus.QUEUED) -> {
                                                    val progress = if (downloadTask.totalPages > 0) downloadTask.downloadedPages.toFloat() / downloadTask.totalPages else 0f
                                                    CircularProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier.size(38.dp),
                                                        strokeWidth = 3.dp
                                                    )
                                                    IconButton(onClick = { viewModel.cancelDownload(chapter.id) }) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Cancel Download",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                downloadTask != null && downloadTask.status == DownloadStatus.FAILED -> {
                                                    IconButton(onClick = { viewModel.downloadChapter(chapter) }) {
                                                        Icon(
                                                            Icons.Default.ErrorOutline,
                                                            contentDescription = "Download Failed - Tap to Retry",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                                isDownloaded -> {
                                                    IconButton(onClick = { viewModel.deleteLocalChapter(chapter.id) }) {
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = "Downloaded",
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    IconButton(onClick = { viewModel.downloadChapter(chapter) }) {
                                                        Icon(
                                                            Icons.Default.Download,
                                                            contentDescription = "Download Chapter",
                                                            tint = MaterialTheme.colorScheme.primary
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
        }
    }
}
