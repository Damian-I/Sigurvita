package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.LoadingState
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

    LaunchedEffect(seriesId) {
        viewModel.loadSeriesDetail(seriesId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.series?.name ?: "Series Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        CoverImage(
                            url = series.coverUrl,
                            modifier = Modifier
                                .width(120.dp)
                                .height(175.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = series.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${state.chaptersRead} / ${state.totalChapters} issues read",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "(${state.pagesRead} / ${state.totalPages} pages)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.toggleMarkRead() }) {
                                Text(if (series.isCompleted) "Mark Unread" else "Mark Read")
                            }
                            if (series.summary?.isNotBlank() == true) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = series.summary!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                items(state.volumes, key = { it.id }) { volume ->
                    Text(
                        text = formatVolumeHeader(volume.name, volume.number),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    volume.chapters.forEach { chapter ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onChapterClick(chapter.id, chapter.pages, volume.id)
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                        Text(
                                            text = "${chapter.pages} pages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (chapter.isRead) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "• Read",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
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
