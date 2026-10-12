package com.zemmy.sigurvita.ui.screens.readinglist

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.ReadingListItem
import com.zemmy.sigurvita.core.util.extractEraInfo
import com.zemmy.sigurvita.ui.components.IssueDetailBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.MassDownloadDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(
    initialReadingListId: Int = 0,
    initialEraName: String = "",
    onBackClick: () -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChapterClick: (chapterId: Int, seriesId: Int, volumeId: Int, readingListId: Int) -> Unit,
    viewModel: ReadingListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showMassDownloadDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<ReadingListItem?>(null) }

    LaunchedEffect(initialReadingListId, initialEraName) {
        if (initialReadingListId > 0) {
            viewModel.selectReadingListById(initialReadingListId)
        } else if (initialEraName.isNotBlank()) {
            viewModel.selectEra(initialEraName)
        } else {
            viewModel.loadReadingLists()
        }
    }

    com.zemmy.sigurvita.ui.util.OnResumeEffect {
        val currentListId = state.selectedListId
        val currentTitle = state.selectedListTitle
        if (currentListId != null && currentListId > 0 && currentTitle != null) {
            viewModel.loadListDetail(currentListId, currentTitle)
        } else {
            viewModel.loadReadingLists()
        }
    }

    if (showMassDownloadDialog) {
        val unreadCount = state.totalIssues - state.issuesRead
        MassDownloadDialog(
            totalAvailableIssues = state.totalIssues,
            unreadIssuesCount = if (unreadCount > 0) unreadCount else state.totalIssues,
            onDismiss = { showMassDownloadDialog = false },
            onConfirmDownload = { count, unreadOnly ->
                viewModel.downloadBulk(count, unreadOnly)
            }
        )
    }

    selectedItemForDetail?.let { item ->
        val mockChapter = Chapter(
            id = item.chapterId ?: 0,
            volumeId = item.volumeId ?: 0,
            seriesId = item.seriesId,
            chapterNumber = "1",
            title = item.title,
            pages = item.pages,
            pagesRead = item.pagesRead,
            isRead = item.isRead,
            coverUrl = item.coverUrl
        )
        IssueDetailBottomSheet(
            chapter = mockChapter,
            seriesName = item.seriesName,
            seriesCoverUrl = item.coverUrl,
            onReadClick = { chapterId, _, volumeId ->
                selectedItemForDetail = null
                onChapterClick(chapterId, item.seriesId, volumeId, item.readingListId)
            },
            onToggleRead = {
                viewModel.toggleItemRead(item)
            },
            onDownloadClick = {
                viewModel.downloadChapter(item)
            },
            onDeleteDownloadClick = { chapterId ->
                viewModel.deleteLocalChapter(chapterId)
            },
            onDismissRequest = { selectedItemForDetail = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.selectedListTitle != null) cleanReadingListTitle(state.selectedListTitle!!) else "Reading Lists") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.selectedListTitle != null) {
                                viewModel.loadReadingLists()
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleIncognitoMode() }) {
                        Icon(
                            imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.toggle_incognito),
                            tint = if (state.isIncognitoMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (state.selectedListTitle != null) {
                        IconButton(onClick = { showMassDownloadDialog = true }) {
                            Icon(Icons.Default.Download, contentDescription = "Mass Download")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                ReadingListSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.displayName,
                                                fontWeight = if (state.sortOption == option) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isIncognitoMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            text = stringResource(R.string.incognito_active_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text(if (state.selectedListTitle != null) "Search issue or series..." else "Search reading lists...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Era/Year Pills (when viewing reading lists overview)
            if (state.selectedListTitle == null && state.eraCategories.size > 1) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.eraCategories, key = { it.name }) { category ->
                            FilterChip(
                                selected = (state.selectedEraName == category.name),
                                onClick = { viewModel.selectEra(category.name) },
                                label = { Text(category.name) }
                            )
                        }
                    }

                    // Selected Era Year Subtitle
                    val activeEra = state.selectedEraCategory
                    if (activeEra?.years != null) {
                        Text(
                            text = "Years: ${activeEra.years}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (state.isLoading && state.readingLists.isEmpty() && state.selectedListItems.isEmpty()) {
                LoadingState()
            } else if (state.errorMessage != null && state.readingLists.isEmpty() && state.selectedListItems.isEmpty()) {
                ErrorState(
                    message = state.errorMessage!!,
                    onRetry = { viewModel.loadReadingLists() }
                )
            } else if (state.selectedListTitle != null) {
                val itemsList = state.filteredListItems
                val nextUnreadIndex = state.nextToReadIndex

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${state.issuesRead} / ${state.totalIssues} issues read",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "(${state.pagesRead} / ${state.totalPages} pages)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.totalPages > 0 || state.totalIssues > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    com.zemmy.sigurvita.ui.components.SeriesReadIndicator(
                                        pagesRead = state.pagesRead,
                                        totalPages = state.totalPages,
                                        issuesRead = state.issuesRead,
                                        totalIssues = state.totalIssues
                                    )
                                }

                                val nextItem = if (nextUnreadIndex in itemsList.indices) itemsList[nextUnreadIndex] else itemsList.firstOrNull()
                                if (nextItem?.chapterId != null && nextItem.chapterId > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CoverImage(
                                            url = nextItem.coverUrl,
                                            modifier = Modifier
                                                .width(50.dp)
                                                .height(75.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "▶ Next to Resume",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = nextItem.seriesName.ifBlank { nextItem.title },
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = nextItem.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${nextItem.pages} pages" + if (nextItem.pagesRead > 0) " (${nextItem.pagesRead} read)" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                onChapterClick(nextItem.chapterId, nextItem.seriesId, nextItem.volumeId ?: 0, nextItem.readingListId)
                                            },
                                            colors = if (state.isIncognitoMode) {
                                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                            } else {
                                                ButtonDefaults.buttonColors()
                                            }
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Resume")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(itemsList, key = { _, item -> item.id }) { index, item ->
                        val isNextUp = (index == nextUnreadIndex)
                        val cid = item.chapterId
                        val downloadTask = cid?.let { state.downloadTasks[it] }
                        val issue = remember(item, isNextUp, downloadTask) {
                            com.zemmy.sigurvita.ui.components.IssueUi(
                                id = item.id,
                                title = com.zemmy.sigurvita.core.util.formatChapterDisplayTitle(item.title.ifBlank { item.seriesName }, ""),
                                subtitle = item.seriesName,
                                coverUrl = item.coverUrl,
                                pages = item.pages,
                                pagesRead = item.pagesRead,
                                isRead = item.isRead,
                                isNextUp = isNextUp,
                                download = downloadTask,
                                isDownloaded = downloadTask?.status == DownloadStatus.COMPLETED,
                                canDownload = cid != null && cid > 0
                            )
                        }
                        com.zemmy.sigurvita.ui.components.IssueRow(
                            issue = issue,
                            onOpen = {
                                if (cid != null && cid > 0) {
                                    onChapterClick(cid, item.seriesId, item.volumeId ?: 0, item.readingListId)
                                } else {
                                    onSeriesClick(item.seriesId)
                                }
                            },
                            onTitleClick = { selectedItemForDetail = item },
                            onSubtitleClick = { onSeriesClick(item.seriesId) },
                            onToggleRead = { viewModel.toggleItemRead(item) },
                            onDownload = { viewModel.downloadChapter(item) },
                            onCancelDownload = { cid?.let { viewModel.cancelDownload(it) } },
                            onDeleteLocal = { cid?.let { viewModel.deleteLocalChapter(it) } },
                            onEraClick = {},
                            onReadingListClick = {},
                            coverSize = androidx.compose.ui.unit.DpSize(60.dp, 90.dp),
                            contentPadding = 12.dp
                        )
                    }
                }
            } else if (state.filteredReadingLists.isNotEmpty()) {
                val lists = state.filteredReadingLists
                val overviewListState = rememberLazyListState()

                LaunchedEffect(state.sortOption) {
                    if (overviewListState.firstVisibleItemIndex == 0) {
                        overviewListState.scrollToItem(0, 0)
                    }
                }

                LazyColumn(
                    state = overviewListState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(lists, key = { it.id }) { list ->
                        val eraInfo = extractEraInfo(list.title)
                        val badge = com.zemmy.sigurvita.ui.components.toListBadge(list.id, list.title)
                        val displayTitle = badge.partName.ifBlank { cleanReadingListTitle(list.title) }
                        val isPinned = state.chosenReadingListIds.contains(list.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.loadListDetail(list.id, list.title) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CoverImage(
                                        url = list.coverUrl,
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(90.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(end = 28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = eraInfo.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (list.totalPages > 0 || list.itemCount > 0) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            com.zemmy.sigurvita.ui.components.SeriesReadIndicator(
                                                pagesRead = list.pagesRead,
                                                totalPages = list.totalPages,
                                                issuesRead = list.issuesRead,
                                                totalIssues = if (list.itemCount > 0) list.itemCount else 0,
                                                compact = true
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.togglePin(list.id) }) {
                                        Icon(
                                            imageVector = if (isPinned) Icons.Default.Bookmark else Icons.Outlined.Bookmark,
                                            contentDescription = if (isPinned) "Unpin from sidebar" else "Pin to sidebar",
                                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (list.totalPages > 0 && list.pagesRead >= list.totalPages) {
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
                                } else if (list.pagesRead > 0 && list.totalPages > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                                        shape = RoundedCornerShape(bottomStart = 8.dp),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = "${(list.pagesRead * 100 / list.totalPages)}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Reading Lists found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
