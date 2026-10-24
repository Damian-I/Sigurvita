package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.IssueDetailBottomSheet
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.MassDownloadDialog
import com.zemmy.sigurvita.ui.components.ReadStatusBadge
import kotlin.math.abs
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.util.ExternalLinkProvider
import com.zemmy.sigurvita.core.util.primaryExternalLink
import androidx.compose.foundation.background
import com.zemmy.sigurvita.core.data.repository.ColorScapeKind
import com.zemmy.sigurvita.ui.theme.ColorScapeColors
import com.zemmy.sigurvita.ui.theme.accent
import com.zemmy.sigurvita.ui.theme.colorScapeBorder
import com.zemmy.sigurvita.ui.theme.headerBrush
import com.zemmy.sigurvita.ui.theme.isSyntheticPlaceholder
import com.zemmy.sigurvita.ui.theme.onAccent
import com.zemmy.sigurvita.ui.theme.rememberColorScape

private fun formatVolumeHeader(name: String, number: Float): String {
    if (name.contains("100000") || abs(number) >= 10000f) {
        return "Issues"
    }
    return if (name.isNotBlank()) "Volume $name" else "Volume $number"
}
@Composable
private fun rememberItemColorScape(item: SeriesItem): ColorScapeColors? = when (item) {
    is SeriesItem.VolumeItem -> rememberColorScape(ColorScapeKind.VOLUME, item.volumeId)
    is SeriesItem.ChapterItem -> rememberColorScape(
        ColorScapeKind.CHAPTER,
        if (item.chapter.isSyntheticPlaceholder()) 0 else item.chapter.id
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    onBackClick: () -> Unit = {},
    onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    onLibraryClick: (libraryId: Int) -> Unit = {},
    onReadingListClick: (readingListId: Int) -> Unit = {},
    onEraClick: (eraName: String) -> Unit = {},
    viewModel: SeriesDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showMassDownloadDialog by remember { mutableStateOf(false) }
    var showMarkReadConfirmDialog by remember { mutableStateOf(false) }
    var showSeriesInfoSheet by remember { mutableStateOf(false) }
    var selectedChapterForDetail by remember { mutableStateOf<Chapter?>(null) }
    val focusRequester = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current
    val externalLink = remember(state.metadata, state.isMangaLibrary) {
        state.metadata?.primaryExternalLink(state.isMangaLibrary)
    }
    val seriesColors = rememberColorScape(ColorScapeKind.SERIES, seriesId)
    val seriesAccent = seriesColors?.accent()

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    com.zemmy.sigurvita.ui.util.OnResumeEffect(seriesId) {
        viewModel.loadSeriesDetail(seriesId)
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

    if (showSeriesInfoSheet) {
        com.zemmy.sigurvita.ui.components.SeriesInfoBottomSheet(
            series = state.series,
            metadata = state.metadata,
            volumes = state.volumes,
            libraryName = state.libraryName,
            onDismissRequest = { showSeriesInfoSheet = false }
        )
    }

    selectedChapterForDetail?.let { chapter ->
        IssueDetailBottomSheet(
            chapter = chapter,
            seriesName = state.series?.name ?: "",
            seriesCoverUrl = state.series?.coverUrl,
            metadata = state.metadata,
            onReadClick = { chapterId, totalPages, volumeId ->
                selectedChapterForDetail = null
                onChapterClick(chapterId, totalPages, volumeId)
            },
            onToggleRead = { targetChapter ->
                viewModel.toggleChapterRead(targetChapter)
            },
            onDownloadClick = { targetChapter ->
                viewModel.downloadChapter(targetChapter)
            },
            onDeleteDownloadClick = { chapterId ->
                viewModel.deleteLocalChapter(chapterId)
            },
            onDismissRequest = { selectedChapterForDetail = null }
        )
    }

    Scaffold(
        topBar = {
            // The loaded layout hosts the app bar inside the series gradient header.
            // Only show the plain bar while there is no series to draw behind it.
            if (state.series == null) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Series Detail",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        if (state.isLoading && state.series == null) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else if (state.errorMessage != null && state.series == null) {
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
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                // FIXED PINNED TOP SECTION
                // The app bar lives inside this gradient container so the ColorScape wash
                // starts at the very top of the screen instead of behind a flat bar.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (seriesColors != null) Modifier.background(seriesColors.headerBrush()) else Modifier)
                        .statusBarsPadding()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = state.series?.name ?: "Series Detail",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CoverImage(
                            url = series.coverUrl,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .width(104.dp)
                                .height(150.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 150.dp)
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSeriesInfoSheet = true }
                                ) {
                                    if (series.summary?.isNotBlank() == true) {
                                        Text(
                                            text = series.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Read more...",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = seriesAccent ?: MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (state.isMangaLibrary) "${state.chaptersRead} / ${state.totalChapters} chapters read" else "${state.chaptersRead} / ${state.totalChapters} issues read",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = seriesAccent ?: MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "(${state.pagesRead} / ${state.totalPages} pages)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (state.totalPages > 0) {
                                    Spacer(modifier = Modifier.height(5.dp))
                                    LinearProgressIndicator(
                                        progress = {
                                            (state.pagesRead.toFloat() / state.totalPages).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = seriesAccent ?: MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        gapSize = 0.dp,
                                        drawStopIndicator = {}
                                    )
                                }
                                if (state.libraryName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.clickable { onLibraryClick(state.libraryId) }
                                    ) {
                                        Text(
                                            text = "Library: ${state.libraryName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
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
                                            containerColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.incognito,
                                            contentColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onIncognito
                                        )
                                    } else {
                                        seriesAccent?.let { ButtonDefaults.buttonColors(containerColor = it, contentColor = it.onAccent()) }
                                            ?: ButtonDefaults.buttonColors()
                                    },
                                    shape = RoundedCornerShape(50)
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
                                    externalLink?.let { link ->
                                        val linkLabel = stringResource(
                                            when (link.provider) {
                                                ExternalLinkProvider.MANGABAKA -> R.string.open_mangabaka
                                                ExternalLinkProvider.COMIC_VINE -> R.string.open_comic_vine
                                            }
                                        )
                                        IconButton(
                                            onClick = { runCatching { uriHandler.openUri(link.url) } },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            // Official brand artwork (raster); keep its own colours and corners.
                                            Image(
                                                painter = painterResource(
                                                    when (link.provider) {
                                                        ExternalLinkProvider.MANGABAKA -> R.drawable.mangabaka_logo
                                                        ExternalLinkProvider.COMIC_VINE -> R.drawable.comic_vine_logo
                                                    }
                                                ),
                                                contentDescription = linkLabel,
                                                modifier = Modifier
                                                    .size(
                                                        when (link.provider) {
                                                            ExternalLinkProvider.MANGABAKA -> 22.dp
                                                            ExternalLinkProvider.COMIC_VINE -> 24.dp
                                                        }
                                                    )
                                                    .clip(
                                                        when (link.provider) {
                                                            ExternalLinkProvider.MANGABAKA -> CircleShape
                                                            ExternalLinkProvider.COMIC_VINE -> RoundedCornerShape(4.dp)
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showMarkReadConfirmDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = if (isSeriesCompleted) "Mark Unread" else "Mark Read",
                                            tint = if (isSeriesCompleted) com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.completed
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            tint = if (state.isIncognitoMode) com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.incognito
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showMassDownloadDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Bulk Download",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.isOffline) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.warningContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onWarningContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Offline Mode: Showing cached & downloaded content.",
                                style = MaterialTheme.typography.bodySmall,
                                color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onWarningContainer
                            )
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
                                val selected = state.viewFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectViewFilter(filter) },
                                    shape = MaterialTheme.shapes.small,
                                    label = {
                                        Text(
                                            text = filter.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        selectedBorderColor = MaterialTheme.colorScheme.primaryContainer
                                    )
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
                                val itemColors = rememberItemColorScape(item)

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
                                    border = colorScapeBorder(isNextUp, itemColors?.accent()),
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
                                            com.zemmy.sigurvita.ui.components.ReadingListBadgeRow(
                                                badges = readingLists.map { (listId, listTitle) -> com.zemmy.sigurvita.ui.components.toListBadge(listId, listTitle) },
                                                onEraClick = onEraClick,
                                                onReadingListClick = onReadingListClick
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (item.isRead) "• Read" else if (isNextUp) "▶ Next" else "Unread",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant else if (isNextUp) com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.nextUp else MaterialTheme.colorScheme.primary
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
                                val itemColors = rememberItemColorScape(item)

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
                                    border = colorScapeBorder(isNextUp, itemColors?.accent()),
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
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.clickable {
                                                    when (item) {
                                                        is SeriesItem.ChapterItem -> selectedChapterForDetail = item.chapter
                                                        is SeriesItem.VolumeItem -> {
                                                            item.volume.chapters.firstOrNull()?.let { selectedChapterForDetail = it }
                                                        }
                                                    }
                                                }
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
                                                com.zemmy.sigurvita.ui.components.ReadingListBadgeRow(
                                                    badges = readingLists.map { (listId, listTitle) -> com.zemmy.sigurvita.ui.components.toListBadge(listId, listTitle) },
                                                    onEraClick = onEraClick,
                                                    onReadingListClick = onReadingListClick
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val showProgress = item.pagesRead > 0 && item.pages > 0 && item.pagesRead < item.pages && !item.isRead
                                                if (showProgress) {
                                                    Text(
                                                        text = "${item.pagesRead} / ${item.pages} pages",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                ReadStatusBadge(
                                                    isRead = item.isRead,
                                                    isNextUp = isNextUp,
                                                    onClick = {
                                                        when (item) {
                                                            is SeriesItem.ChapterItem -> viewModel.toggleChapterRead(item.chapter)
                                                            is SeriesItem.VolumeItem -> {
                                                                item.volume.chapters.firstOrNull()?.let { viewModel.toggleChapterRead(it) }
                                                            }
                                                        }
                                                    }
                                                )
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
                                    val isNextUp = (chapter.id == nextToReadChapterId)
                                    val readingLists = state.chapterReadingLists[chapter.id]
                                    val issue = remember(chapter, downloadTask, isNextUp, readingLists, series.coverUrl) {
                                        com.zemmy.sigurvita.ui.components.IssueUi(
                                            id = chapter.id,
                                            title = com.zemmy.sigurvita.core.util.formatChapterDisplayTitle(chapter.title, chapter.chapterNumber),
                                            coverUrl = chapter.coverUrl ?: series.coverUrl,
                                            pages = chapter.pages,
                                            pagesRead = chapter.pagesRead,
                                            isRead = chapter.isRead,
                                            isNextUp = isNextUp,
                                            badges = readingLists?.map { (id, title) -> com.zemmy.sigurvita.ui.components.toListBadge(id, title) }.orEmpty(),
                                            download = downloadTask,
                                            isDownloaded = chapter.isDownloaded || downloadTask?.status == DownloadStatus.COMPLETED
                                        )
                                    }
                                    val accent = rememberColorScape(ColorScapeKind.CHAPTER, chapter.id)?.accent()
                                    com.zemmy.sigurvita.ui.components.IssueGridCard(
                                        issue = issue,
                                        accent = accent,
                                        onOpen = { onChapterClick(chapter.id, chapter.pages, volume.id) },
                                        onDownload = { viewModel.downloadChapter(chapter) },
                                        onCancelDownload = { viewModel.cancelDownload(chapter.id) },
                                        onDeleteLocal = { viewModel.deleteLocalChapter(chapter.id) },
                                        onEraClick = onEraClick,
                                        onReadingListClick = onReadingListClick
                                    )
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
                                    val isNextUp = (chapter.id == nextToReadChapterId)
                                    val readingLists = state.chapterReadingLists[chapter.id]
                                    val issue = remember(chapter, downloadTask, isNextUp, readingLists, series.coverUrl) {
                                        com.zemmy.sigurvita.ui.components.IssueUi(
                                            id = chapter.id,
                                            title = com.zemmy.sigurvita.core.util.formatChapterDisplayTitle(chapter.title, chapter.chapterNumber),
                                            coverUrl = chapter.coverUrl ?: series.coverUrl,
                                            pages = chapter.pages,
                                            pagesRead = chapter.pagesRead,
                                            isRead = chapter.isRead,
                                            isNextUp = isNextUp,
                                            badges = readingLists?.map { (id, title) -> com.zemmy.sigurvita.ui.components.toListBadge(id, title) }.orEmpty(),
                                            download = downloadTask,
                                            isDownloaded = chapter.isDownloaded || downloadTask?.status == DownloadStatus.COMPLETED
                                        )
                                    }
                                    val accent = rememberColorScape(ColorScapeKind.CHAPTER, chapter.id)?.accent()
                                    com.zemmy.sigurvita.ui.components.IssueRow(
                                        issue = issue,
                                        accent = accent,
                                        onOpen = { onChapterClick(chapter.id, chapter.pages, volume.id) },
                                        onTitleClick = { selectedChapterForDetail = chapter },
                                        onToggleRead = { viewModel.toggleChapterRead(chapter) },
                                        onDownload = { viewModel.downloadChapter(chapter) },
                                        onCancelDownload = { viewModel.cancelDownload(chapter.id) },
                                        onDeleteLocal = { viewModel.deleteLocalChapter(chapter.id) },
                                        onEraClick = onEraClick,
                                        onReadingListClick = onReadingListClick
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
