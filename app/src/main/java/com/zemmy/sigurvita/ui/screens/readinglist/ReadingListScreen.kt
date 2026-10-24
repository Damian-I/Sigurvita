package com.zemmy.sigurvita.ui.screens.readinglist

import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
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
import com.zemmy.sigurvita.core.util.parseReadingListHierarchy
import com.zemmy.sigurvita.ui.components.IssueDetailBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.zemmy.sigurvita.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(
    initialReadingListId: Int = 0,
    initialEraName: String = "",
    onBackClick: () -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChapterClick: (chapterId: Int, seriesId: Int, volumeId: Int, readingListId: Int) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
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
                title = {
                    Text(
                        text = if (state.selectedListTitle != null) cleanReadingListTitle(state.selectedListTitle!!) else "Reading Lists",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    val atRoot = state.selectedListTitle == null
                    IconButton(
                        onClick = {
                            if (atRoot && onOpenDrawer != null) {
                                onOpenDrawer()
                            } else if (!atRoot) {
                                viewModel.loadReadingLists()
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (atRoot && onOpenDrawer != null) Icons.Default.Menu
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (atRoot && onOpenDrawer != null) "Open navigation" else "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleIncognitoMode() }) {
                        Icon(
                            imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.toggle_incognito),
                            tint = if (state.isIncognitoMode) com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.incognito else MaterialTheme.colorScheme.onSurface
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
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    colors = CardDefaults.cardColors(
                        containerColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.infoContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onInfoContainer
                        )
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Text(
                            text = stringResource(R.string.incognito_active_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onInfoContainer
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
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )

            // Era/Year Pills (when viewing reading lists overview)
            if (state.selectedListTitle == null && state.eraCategories.size > 1) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.eraCategories, key = { it.name }) { category ->
                            val selected = state.selectedEraName == category.name
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectEra(category.name) },
                                shape = MaterialTheme.shapes.small,
                                label = {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primaryContainer
                                )
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
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(Spacing.sm))
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
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${state.issuesRead} / ${state.totalIssues} issues read",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "(${state.pagesRead} / ${state.totalPages} pages)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.totalPages > 0 || state.totalIssues > 0) {
                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    com.zemmy.sigurvita.ui.components.SeriesReadIndicator(
                                        pagesRead = state.pagesRead,
                                        totalPages = state.totalPages,
                                        issuesRead = state.issuesRead,
                                        totalIssues = state.totalIssues
                                    )
                                }

                                val nextItem = if (nextUnreadIndex in itemsList.indices) itemsList[nextUnreadIndex] else itemsList.firstOrNull()
                                if (nextItem?.chapterId != null && nextItem.chapterId > 0) {
                                    Spacer(modifier = Modifier.height(Spacing.md))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(Spacing.md))

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
                                        Spacer(modifier = Modifier.width(Spacing.md))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "▶ Next to Resume",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.nextUp
                                            )
                                            Spacer(modifier = Modifier.height(Spacing.xxs))
                                            Text(
                                                text = nextItem.seriesName.ifBlank { nextItem.title },
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = nextItem.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${nextItem.pages} pages" + if (nextItem.pagesRead > 0) " (${nextItem.pagesRead} read)" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Button(
                                            onClick = {
                                                onChapterClick(nextItem.chapterId, nextItem.seriesId, nextItem.volumeId ?: 0, nextItem.readingListId)
                                            },
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                            colors = if (state.isIncognitoMode) {
                                                ButtonDefaults.buttonColors(
                                                    containerColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.incognito,
                                                    contentColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onIncognito
                                                )
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

                val hierarchy = remember(lists) { parseReadingListHierarchy(lists) }
                var expandedEras by remember(hierarchy) { mutableStateOf(hierarchy.map { it.eraName }.toSet()) }
                var expandedParts by remember(hierarchy) { mutableStateOf(hierarchy.flatMap { era -> era.parts.map { "${era.eraName}_${it.partName}" } }.toSet()) }

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
                    hierarchy.forEach { eraGroup ->
                        val isEraExpanded = expandedEras.contains(eraGroup.eraName)
                        item(key = "era_${eraGroup.eraName}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedEras = if (isEraExpanded) expandedEras - eraGroup.eraName else expandedEras + eraGroup.eraName
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isEraExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${eraGroup.eraName} (${eraGroup.parts.sumOf { it.subparts.size }})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (isEraExpanded) {
                            eraGroup.parts.forEach { partGroup ->
                                val partKey = "${eraGroup.eraName}_${partGroup.partName}"
                                val isPartExpanded = expandedParts.contains(partKey)
                                item(key = "part_$partKey") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedParts = if (isPartExpanded) expandedParts - partKey else expandedParts + partKey
                                            }
                                            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isPartExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${partGroup.partDisplayName} (${partGroup.subparts.size})",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (isPartExpanded) {
                                    items(partGroup.subparts, key = { sub -> sub.list.id }) { sub ->
                                        val list = sub.list
                                        val displayTitle = sub.subpartDisplayTitle
                                        val isPinned = state.chosenReadingListIds.contains(list.id)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 32.dp)
                                        ) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.loadListDetail(list.id, list.title) },
                                                shape = MaterialTheme.shapes.medium,
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                                )
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier.padding(Spacing.md),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        CoverImage(
                                                            url = list.coverUrl,
                                                            modifier = Modifier
                                                                .width(60.dp)
                                                                .height(90.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(Spacing.md))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = displayTitle,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier
                                                                    .padding(end = 28.dp)
                                                                    .basicMarquee(
                                                                        iterations = Int.MAX_VALUE,
                                                                        repeatDelayMillis = 2000
                                                                    )
                                                            )
                                                            Spacer(modifier = Modifier.height(Spacing.xxs))
                                                            Text(
                                                                text = eraGroup.eraName,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            if (list.totalPages > 0 || list.itemCount > 0) {
                                                                Spacer(modifier = Modifier.height(6.dp))
                                                                com.zemmy.sigurvita.ui.components.SeriesReadIndicator(
                                                                    pagesRead = list.pagesRead,
                                                                    totalPages = list.totalPages,
                                                                    issuesRead = list.issuesRead,
                                                                    totalIssues = if (list.itemCount > 0) list.itemCount else 0
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
                                                            color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.successContainer.copy(alpha = 0.95f),
                                                            contentColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onSuccessContainer,
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
                                                                    tint = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.completed,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = "READ",
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        fontSize = 9.sp
                                                                    ),
                                                                    color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onSuccessContainer
                                                                )
                                                            }
                                                        }
                                                    } else if (list.pagesRead > 0 && list.totalPages > 0) {
                                                        Surface(
                                                            color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.infoContainer.copy(alpha = 0.95f),
                                                            contentColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onInfoContainer,
                                                            modifier = Modifier.align(Alignment.TopEnd)
                                                        ) {
                                                            Text(
                                                                text = "${(list.pagesRead * 100 / list.totalPages)}%",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 9.sp
                                                                ),
                                                                color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onInfoContainer,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
