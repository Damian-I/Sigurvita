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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.util.extractEraInfo
import com.zemmy.sigurvita.ui.components.ReadStatusBadge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.MassDownloadDialog

import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun formatReadingListItemTitle(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isBlank()) return "Issue"
    if (trimmed.all { it.isDigit() || it == '.' }) return "#$trimmed"
    if (trimmed.startsWith("#") || trimmed.startsWith("Issue", ignoreCase = true) || trimmed.startsWith("Chapter", ignoreCase = true)) {
        return trimmed
    }
    val firstToken = trimmed.substringBefore(" ")
    if (firstToken.all { it.isDigit() || it == '.' }) {
        val rest = trimmed.substringAfter(" ").trimStart('-', ':', '•', ' ').trim()
        return if (rest.isNotBlank()) "#$firstToken - $rest" else "#$firstToken"
    }
    return trimmed
}

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

    LaunchedEffect(initialReadingListId, initialEraName) {
        if (initialReadingListId > 0) {
            viewModel.selectReadingListById(initialReadingListId)
        } else if (initialEraName.isNotBlank()) {
            viewModel.selectEra(initialEraName)
        } else {
            viewModel.loadReadingLists()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentListId = state.selectedListId
                val currentTitle = state.selectedListTitle
                if (currentListId != null && currentListId > 0 && currentTitle != null) {
                    viewModel.loadListDetail(currentListId, currentTitle)
                } else {
                    viewModel.loadReadingLists()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                        val downloadTask = item.chapterId?.let { state.downloadTasks[it] }
                        val isDownloaded = (downloadTask?.status == DownloadStatus.COMPLETED)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.chapterId != null && item.chapterId > 0) {
                                        onChapterClick(item.chapterId, item.seriesId, item.volumeId ?: 0, item.readingListId)
                                    } else {
                                        onSeriesClick(item.seriesId)
                                    }
                                },
                            border = if (isNextUp) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary)) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isNextUp) 4.dp else 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverImage(
                                    url = item.coverUrl,
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(90.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formatReadingListItemTitle(item.title.ifBlank { item.seriesName }),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.seriesName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onSeriesClick(item.seriesId) }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { viewModel.toggleItemRead(item) }
                                    ) {
                                        val showProgress = item.pages > 0 && item.pagesRead > 0 && item.pagesRead < item.pages && !item.isRead
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
                                            onClick = { viewModel.toggleItemRead(item) }
                                        )
                                    }
                                }

                                if (item.chapterId != null && item.chapterId > 0) {
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
                                                IconButton(onClick = { viewModel.cancelDownload(item.chapterId) }) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Cancel Download",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            downloadTask != null && downloadTask.status == DownloadStatus.FAILED -> {
                                                IconButton(onClick = { viewModel.downloadChapter(item) }) {
                                                    Icon(
                                                        Icons.Default.ErrorOutline,
                                                        contentDescription = "Download Failed - Tap to Retry",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                            isDownloaded -> {
                                                IconButton(onClick = { viewModel.deleteLocalChapter(item.chapterId) }) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Downloaded",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            else -> {
                                                IconButton(onClick = { viewModel.downloadChapter(item) }) {
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
                        val displayTitle = cleanReadingListTitle(list.title)
                        val isPinned = state.chosenReadingListIds.contains(list.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.loadListDetail(list.id, list.title) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
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
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${eraInfo.name} • ${list.itemCount} items",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.togglePin(list.id) }) {
                                    Icon(
                                        imageVector = if (isPinned) Icons.Default.Bookmark else Icons.Outlined.Bookmark,
                                        contentDescription = if (isPinned) "Unpin from sidebar" else "Pin to sidebar",
                                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
