package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.data.repository.ColorScapeKind
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.util.primaryExternalLink
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.IssueDetailBottomSheet
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.MassDownloadDialog
import com.zemmy.sigurvita.ui.theme.accent
import com.zemmy.sigurvita.ui.theme.rememberColorScape

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
            val allChapters = state.volumes.flatMap { it.chapters }
            val nextToReadChapterId = state.nextToReadId

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                // The list/grid takes whatever height is left under the header and toolbar
                val layoutModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)

                SeriesHeader(
                    series = series,
                    state = state,
                    seriesColors = seriesColors,
                    seriesAccent = seriesAccent,
                    externalLink = externalLink,
                    isSeriesCompleted = isSeriesCompleted,
                    onBackClick = onBackClick,
                    onShowInfo = { showSeriesInfoSheet = true },
                    onLibraryClick = onLibraryClick,
                    onResume = {
                        val nextChapter = allChapters.firstOrNull { !it.isRead } ?: allChapters.firstOrNull()
                        if (nextChapter != null) {
                            onChapterClick(nextChapter.id, nextChapter.pages, nextChapter.volumeId)
                        }
                    },
                    onMarkReadClick = { showMarkReadConfirmDialog = true },
                    onToggleIncognito = { viewModel.toggleIncognitoMode() },
                    onBulkDownloadClick = { showMassDownloadDialog = true }
                )

                if (state.isOffline) {
                    OfflineBanner()
                }

                SeriesToolbar(
                    state = state,
                    focusRequester = focusRequester,
                    onSelectViewFilter = { viewModel.selectViewFilter(it) },
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onToggleSearch = { viewModel.toggleSearchActive() },
                    onToggleGridView = { viewModel.toggleGridView() }
                )

                if (state.isMangaLibrary) {
                    // Manga: volumes and chapters in one continuous list
                    if (state.displayMangaItems.isEmpty()) {
                        EmptyResults(layoutModifier)
                    } else if (state.isGridView) {
                        MangaItemsGrid(
                            seriesItems = state.displayMangaItems,
                            seriesCoverUrl = series.coverUrl,
                            nextToReadChapterId = nextToReadChapterId,
                            chapterReadingLists = state.chapterReadingLists,
                            onChapterClick = onChapterClick,
                            onDownloadChapter = { viewModel.downloadChapter(it) },
                            onEraClick = onEraClick,
                            onReadingListClick = onReadingListClick,
                            modifier = layoutModifier
                        )
                    } else {
                        MangaItemsList(
                            seriesItems = state.displayMangaItems,
                            seriesCoverUrl = series.coverUrl,
                            nextToReadChapterId = nextToReadChapterId,
                            chapterReadingLists = state.chapterReadingLists,
                            onChapterClick = onChapterClick,
                            onShowDetail = { selectedChapterForDetail = it },
                            onToggleChapterRead = { viewModel.toggleChapterRead(it) },
                            onDownloadChapter = { viewModel.downloadChapter(it) },
                            onEraClick = onEraClick,
                            onReadingListClick = onReadingListClick,
                            modifier = layoutModifier
                        )
                    }
                } else {
                    // Comics: volumes -> issues
                    if (state.filteredVolumes.isEmpty()) {
                        EmptyResults(layoutModifier)
                    } else if (state.isGridView) {
                        ComicIssuesGrid(
                            volumes = state.filteredVolumes,
                            seriesCoverUrl = series.coverUrl,
                            nextToReadChapterId = nextToReadChapterId,
                            downloadTasks = state.downloadTasks,
                            chapterReadingLists = state.chapterReadingLists,
                            onChapterClick = onChapterClick,
                            onDownloadChapter = { viewModel.downloadChapter(it) },
                            onCancelDownload = { viewModel.cancelDownload(it) },
                            onDeleteLocal = { viewModel.deleteLocalChapter(it) },
                            onEraClick = onEraClick,
                            onReadingListClick = onReadingListClick,
                            modifier = layoutModifier
                        )
                    } else {
                        ComicIssuesList(
                            volumes = state.filteredVolumes,
                            seriesCoverUrl = series.coverUrl,
                            nextToReadChapterId = nextToReadChapterId,
                            downloadTasks = state.downloadTasks,
                            chapterReadingLists = state.chapterReadingLists,
                            onChapterClick = onChapterClick,
                            onShowDetail = { selectedChapterForDetail = it },
                            onToggleChapterRead = { viewModel.toggleChapterRead(it) },
                            onDownloadChapter = { viewModel.downloadChapter(it) },
                            onCancelDownload = { viewModel.cancelDownload(it) },
                            onDeleteLocal = { viewModel.deleteLocalChapter(it) },
                            onEraClick = onEraClick,
                            onReadingListClick = onReadingListClick,
                            modifier = layoutModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
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
