package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.core.util.extractEraInfo
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

    val cleanNumber = if (!isSentinelNumber && chapterNumber.isNotBlank()) "#$chapterNumber" else ""

    var cleanTitle = title.trim()
    if (chapterNumber.isNotBlank()) {
        cleanTitle = cleanTitle
            .removePrefix("Issue #$chapterNumber")
            .removePrefix("Issue $chapterNumber")
            .removePrefix("#$chapterNumber")
            .removePrefix(chapterNumber)
            .trimStart(' ', '-', ':', '•')
            .trim()
    }

    if (cleanTitle.equals(chapterNumber.trim(), ignoreCase = true)) {
        cleanTitle = ""
    }

    return when {
        cleanNumber.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanNumber - $cleanTitle"
        cleanTitle.isNotBlank() -> cleanTitle
        cleanNumber.isNotBlank() -> cleanNumber
        else -> "Issue"
    }
}

@Composable
private fun IntegratedReadingListBadge(
    eraName: String,
    partName: String,
    listId: Int,
    onEraClick: (String) -> Unit,
    onReadingListClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val showEra = eraName.isNotBlank() && eraName != "General"
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showEra) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.clickable { onEraClick(eraName) }
                ) {
                    Text(
                        text = eraName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.clickable { onReadingListClick(listId) }
            ) {
                Text(
                    text = partName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    onLibraryClick: (libraryId: Int) -> Unit = {},
    onReadingListClick: (readingListId: Int) -> Unit = {},
    onEraClick: (eraName: String) -> Unit = {},
    viewModel: SeriesDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showMassDownloadDialog by remember { mutableStateOf(false) }
    var showMarkReadConfirmDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            focusRequester.requestFocus()
        }
    }

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

    if (showMarkReadConfirmDialog) {
        val series = state.series
        val isSeriesCompleted = series != null && (series.isCompleted || (state.chaptersRead >= state.totalChapters && state.totalChapters > 0))
        AlertDialog(
            onDismissRequest = { showMarkReadConfirmDialog = false },
            title = { Text(if (isSeriesCompleted) "Mark Series Unread" else "Mark Series Read") },
            text = { Text(if (isSeriesCompleted) "Are you sure you want to mark all chapters in this series as unread?" else "Are you sure you want to mark all chapters in this series as read?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMarkReadConfirmDialog = false
                        viewModel.toggleMarkRead()
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkReadConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold { innerPadding ->
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
            val displayMangaItems = state.displayMangaItems
            val allChapters = state.volumes.flatMap { it.chapters }
            val nextToReadChapterId = state.nextToReadId

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // FIXED PINNED TOP SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
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
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = series.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (series.summary?.isNotBlank() == true) {
                                Spacer(modifier = Modifier.height(4.dp))
                                var summaryExpanded by remember(series.id) { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { summaryExpanded = !summaryExpanded }
                                ) {
                                    Text(
                                        text = series.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (summaryExpanded) "Read less" else "Read more...",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.isMangaLibrary) "${state.chaptersRead} / ${state.totalChapters} chapters read" else "${state.chaptersRead} / ${state.totalChapters} issues read",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "(${state.pagesRead} / ${state.totalPages} pages)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.libraryName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.clickable { onLibraryClick(state.libraryId) }
                                ) {
                                    Text(
                                        text = "Library: ${state.libraryName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val nextChapter = allChapters.firstOrNull { !it.isRead } ?: allChapters.firstOrNull()
                                        if (nextChapter != null) {
                                            onChapterClick(nextChapter.id, nextChapter.pages, nextChapter.volumeId)
                                        }
                                    },
                                    colors = if (state.isIncognitoMode) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    }
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
                                    IconButton(
                                        onClick = { showMarkReadConfirmDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = if (isSeriesCompleted) "Mark Unread" else "Mark Read",
                                            tint = if (isSeriesCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.toggleIncognitoMode() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Incognito Mode",
                                            tint = if (state.isIncognitoMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TOOLBAR / SEARCH SECTION
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isMangaLibrary && !state.isSearchActive) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(SeriesViewFilter.entries.toTypedArray()) { filter ->
                                FilterChip(
                                    selected = (state.viewFilter == filter),
                                    onClick = { viewModel.selectViewFilter(filter) },
                                    label = { Text(filter.displayName) }
                                )
                            }
                        }
                    } else if (!state.isMangaLibrary && !state.isSearchActive) {
                        val volumeHeaderStr = displayVolumes.firstOrNull()?.let { formatVolumeHeader(it.name, it.number) } ?: "Chapters"
                        Text(
                            text = volumeHeaderStr,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (state.isSearchActive) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search...", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.toggleSearchActive() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Close Search",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .focusRequester(focusRequester)
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.toggleSearchActive() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleGridView() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List View",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (state.isMangaLibrary) {
                    // MANGA CONTINUOUS UNIFIED LAYOUT
                    if (displayMangaItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else if (state.isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(displayMangaItems, key = { item -> "${item.javaClass.simpleName}_${item.id}" }) { item ->
                                val isNextUp = when (item) {
                                    is SeriesItem.ChapterItem -> item.chapter.id == nextToReadChapterId
                                    is SeriesItem.VolumeItem -> item.firstChapterId == nextToReadChapterId
                                }
                                val readingLists = if (item is SeriesItem.ChapterItem) state.chapterReadingLists[item.chapter.id] else null

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (item) {
                                                is SeriesItem.VolumeItem -> {
                                                    if (item.firstChapterId > 0) {
                                                        onChapterClick(item.firstChapterId, item.pages, item.volumeId)
                                                    }
                                                }
                                                is SeriesItem.ChapterItem -> {
                                                    onChapterClick(item.chapter.id, item.chapter.pages, item.volumeId)
                                                }
                                            }
                                        },
                                    border = if (isNextUp) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary)) else null,
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isNextUp) 4.dp else 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CoverImage(
                                            url = item.coverUrl ?: series.coverUrl,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.7f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!readingLists.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                readingLists.forEach { (listId, listTitle) ->
                                                    val eraInfo = extractEraInfo(listTitle)
                                                    val eraName = eraInfo.name
                                                    val cleanTitle = cleanReadingListTitle(listTitle)
                                                    val partName = cleanTitle.removePrefix(eraName).trimStart('-', ':', '•', ' ').ifBlank { cleanTitle }

                                                    IntegratedReadingListBadge(
                                                        eraName = eraName,
                                                        partName = partName,
                                                        listId = listId,
                                                        onEraClick = onEraClick,
                                                        onReadingListClick = onReadingListClick
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (item.isRead) "• Read" else if (isNextUp) "▶ Next" else "Unread",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant else if (isNextUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(
                                                onClick = {
                                                    when (item) {
                                                        is SeriesItem.ChapterItem -> viewModel.downloadChapter(item.chapter)
                                                        is SeriesItem.VolumeItem -> {
                                                            item.volume.chapters.firstOrNull()?.let { viewModel.downloadChapter(it) }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = if (item.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(displayMangaItems, key = { item -> "${item.javaClass.simpleName}_${item.id}" }) { item ->
                                val isNextUp = when (item) {
                                    is SeriesItem.ChapterItem -> item.chapter.id == nextToReadChapterId
                                    is SeriesItem.VolumeItem -> item.firstChapterId == nextToReadChapterId
                                }
                                val readingLists = if (item is SeriesItem.ChapterItem) state.chapterReadingLists[item.chapter.id] else null

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (item) {
                                                is SeriesItem.VolumeItem -> {
                                                    if (item.firstChapterId > 0) {
                                                        onChapterClick(item.firstChapterId, item.pages, item.volumeId)
                                                    }
                                                }
                                                is SeriesItem.ChapterItem -> {
                                                    onChapterClick(item.chapter.id, item.chapter.pages, item.volumeId)
                                                }
                                            }
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
                                            url = item.coverUrl ?: series.coverUrl,
                                            modifier = Modifier
                                                .width(60.dp)
                                                .height(85.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!item.subtitle.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.subtitle!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!readingLists.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    readingLists.forEach { (listId, listTitle) ->
                                                        val eraInfo = extractEraInfo(listTitle)
                                                        val eraName = eraInfo.name
                                                        val cleanTitle = cleanReadingListTitle(listTitle)
                                                        val partName = cleanTitle.removePrefix(eraName).trimStart('-', ':', '•', ' ').ifBlank { cleanTitle }

                                                        IntegratedReadingListBadge(
                                                            eraName = eraName,
                                                            partName = partName,
                                                            listId = listId,
                                                            onEraClick = onEraClick,
                                                            onReadingListClick = onReadingListClick,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val showProgress = item.pagesRead > 0 && item.pages > 0 && item.pagesRead < item.pages && !item.isRead
                                                Text(
                                                    text = if (showProgress) "${item.pagesRead} / ${item.pages} pages" else "${item.pages} pages",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (item.isRead) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "• Read",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else if (isNextUp) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "▶ Next to Read",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "• Unread",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                when (item) {
                                                    is SeriesItem.ChapterItem -> viewModel.downloadChapter(item.chapter)
                                                    is SeriesItem.VolumeItem -> {
                                                        item.volume.chapters.firstOrNull()?.let { viewModel.downloadChapter(it) }
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (item.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                contentDescription = "Download",
                                                tint = if (item.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- STANDARD NON-MANGA COMIC LAYOUT (100% original untouched) ---
                    if (displayVolumes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else if (state.isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            displayVolumes.forEach { volume ->
                                if (displayVolumes.size > 1) {
                                    item(span = { GridItemSpan(3) }) {
                                        Text(
                                            text = formatVolumeHeader(volume.name, volume.number),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                                        )
                                    }
                                }
                                items(volume.chapters, key = { it.id }) { chapter ->
                                    val downloadTask = state.downloadTasks[chapter.id]
                                    val isDownloaded = chapter.isDownloaded || (downloadTask?.status == DownloadStatus.COMPLETED)
                                    val isNextUp = (chapter.id == nextToReadChapterId)
                                    val readingLists = state.chapterReadingLists[chapter.id]

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
                                                    .aspectRatio(0.7f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatChapterTitle(chapter.title, chapter.chapterNumber),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!readingLists.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    readingLists.forEach { (listId, listTitle) ->
                                                        val eraInfo = extractEraInfo(listTitle)
                                                        val eraName = eraInfo.name
                                                        val cleanTitle = cleanReadingListTitle(listTitle)
                                                        val partName = cleanTitle.removePrefix(eraName).trimStart('-', ':', '•', ' ').ifBlank { cleanTitle }

                                                        IntegratedReadingListBadge(
                                                            eraName = eraName,
                                                            partName = partName,
                                                            listId = listId,
                                                            onEraClick = onEraClick,
                                                            onReadingListClick = onReadingListClick
                                                        )
                                                    }
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (chapter.isRead) "• Read" else if (isNextUp) "▶ Next" else "Unread",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (chapter.isRead) MaterialTheme.colorScheme.onSurfaceVariant else if (isNextUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
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
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            displayVolumes.forEach { volume ->
                                if (displayVolumes.size > 1) {
                                    item {
                                        Text(
                                            text = formatVolumeHeader(volume.name, volume.number),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                                        )
                                    }
                                }
                                items(volume.chapters, key = { it.id }) { chapter ->
                                    val downloadTask = state.downloadTasks[chapter.id]
                                    val isDownloaded = chapter.isDownloaded || (downloadTask?.status == DownloadStatus.COMPLETED)
                                    val isNextUp = (chapter.id == nextToReadChapterId)
                                    val readingLists = state.chapterReadingLists[chapter.id]

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
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
                                                if (!readingLists.isNullOrEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        readingLists.forEach { (listId, listTitle) ->
                                                            val eraInfo = extractEraInfo(listTitle)
                                                            val eraName = eraInfo.name
                                                            val cleanTitle = cleanReadingListTitle(listTitle)
                                                            val partName = cleanTitle.removePrefix(eraName).trimStart('-', ':', '•', ' ').ifBlank { cleanTitle }

                                                            IntegratedReadingListBadge(
                                                                eraName = eraName,
                                                                partName = partName,
                                                                listId = listId,
                                                                onEraClick = onEraClick,
                                                                onReadingListClick = onReadingListClick,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                        }
                                                    }
                                                }
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
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary,
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
}
