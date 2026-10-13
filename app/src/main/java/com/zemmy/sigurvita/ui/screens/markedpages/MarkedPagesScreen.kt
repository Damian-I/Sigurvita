package com.zemmy.sigurvita.ui.screens.markedpages

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.screens.reader.BubbleMagnifierOverlay
import com.zemmy.sigurvita.ui.screens.reader.ImageReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkedPagesScreen(
    onBackClick: () -> Unit,
    onOpenReader: (chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int) -> Unit,
    viewModel: MarkedPagesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Page Marks") },
            text = { Text("Are you sure you want to remove all marked pages for review?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsSheet) {
        PageReviewSettingsSheet(
            settings = state.readerSettings,
            onUpdateSpeechBubbleDetection = { viewModel.updateSpeechBubbleDetectionEnabled(it) },
            onUpdateSpeechBubbleBoxes = { viewModel.updateSpeechBubbleBoxesEnabled(it) },
            onUpdateSpeechBubbleConfidence = { viewModel.updateSpeechBubbleConfidence(it) },
            onUpdateFitMode = { viewModel.updateFitMode(it) },
            onUpdatePanelAwareOrder = { viewModel.updatePanelAwareOrder(it) },
            onUpdatePanelDebugOverlay = { viewModel.updatePanelDebugOverlay(it) },
            onDismissRequest = { showSettingsSheet = false }
        )
    }

    // Fullscreen Image & ML Testing Preview Dialog (Never cut off!)
    val previewPage = state.previewingPage
    if (previewPage != null) {
        Dialog(
            onDismissRequest = { viewModel.setPreviewPage(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Header Overlay Bar (Matching ReaderScreen.kt)
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tag Chip (DEBUG / REMOVE)
                            Surface(
                                color = if (previewPage.reviewTag == "REMOVE") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable {
                                    viewModel.toggleReviewTag(previewPage.id, previewPage.reviewTag)
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (previewPage.reviewTag == "REMOVE") Icons.Outlined.Delete else Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = if (previewPage.reviewTag == "REMOVE") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (previewPage.reviewTag == "REMOVE") "TO REMOVE" else "DEBUG",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                                        color = if (previewPage.reviewTag == "REMOVE") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }

                            // Title Info (Series Name + Chapter Title & Page N of M)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = previewPage.seriesName.ifBlank { previewPage.chapterTitle },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = "${previewPage.chapterTitle} • Page ${previewPage.pageIndex + 1}" + if (previewPage.totalPages > 0) " of ${previewPage.totalPages}" else "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                            }

                            // Actions
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showSettingsSheet = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                                }
                                IconButton(onClick = { viewModel.setPreviewPage(null) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }
                        }
                    }

                    // Image Reader Area (Full comic page reader: pinch zoom, speech bubble magnifier, panel map)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.readerPages.isNotEmpty()) {
                            ImageReader(
                                pages = state.readerPages,
                                currentPageIndex = 0,
                                readingMode = ReadingMode.LTR,
                                onPageChanged = {},
                                onNextPage = {},
                                onPreviousPage = {},
                                onTap = {},
                                fitMode = state.readerSettings.fitMode,
                                touchEdgeNavigationEnabled = false,
                                bubblesByPage = if (state.testedPageBubbles != null) mapOf(0 to state.testedPageBubbles!!) else emptyMap(),
                                panelMapsByPage = if (state.testedPanelMap != null) mapOf(0 to state.testedPanelMap!!) else emptyMap(),
                                speechBubbleDetectionEnabled = state.readerSettings.speechBubbleDetectionEnabled,
                                speechBubbleBoxesEnabled = state.readerSettings.speechBubbleBoxesEnabled,
                                panelDebugOverlayEnabled = state.readerSettings.panelDebugOverlayEnabled,
                                onBubbleLongPress = { pageIdx, bubbleId, centerPx, bubbleSizePx ->
                                    viewModel.onBubbleLongPress(pageIdx, bubbleId, centerPx, bubbleSizePx)
                                }
                            )

                            state.magnifiedBubble?.let { magBubble ->
                                BubbleMagnifierOverlay(
                                    magnifiedBubble = magBubble,
                                    speechBubbleBoxesEnabled = state.readerSettings.speechBubbleBoxesEnabled,
                                    onDismiss = { viewModel.dismissBubble() }
                                )
                            }
                        }
                    }

                    // Bottom ML Inspection Panel
                    Surface(
                        color = Color.Black.copy(alpha = 0.90f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (state.isTestingMl) {
                                Text(
                                    text = "Analyzing speech bubbles & panels...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                val bubblesCount = state.testedPageBubbles?.bubbles?.size
                                val panelsCount = state.testedPanelMap?.panels?.size
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${bubblesCount ?: 0} bubbles detected",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${panelsCount ?: 0} panels detected",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page Review (${state.markedPages.size})") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Reader & ML Settings")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            MarkedPageSorting.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.displayName,
                                            fontWeight = if (state.selectedSorting == option) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSorting(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    if (state.markedPages.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
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
            if (state.markedPages.isNotEmpty()) {
                // Search Bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search marked pages...") },
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
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Tag Filter Chips (All, Debug, To Remove)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ReviewTagFilter.entries) { filter ->
                        FilterChip(
                            selected = (state.selectedTagFilter == filter),
                            onClick = { viewModel.setTagFilter(filter) },
                            label = { Text(filter.displayName) }
                        )
                    }
                }

                // Grouping Filter Chips (Flat, By Series, By Tag)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MarkedPageGrouping.entries) { grouping ->
                        FilterChip(
                            selected = (state.selectedGrouping == grouping),
                            onClick = { viewModel.setGrouping(grouping) },
                            label = { Text("Group: ${grouping.displayName}") }
                        )
                    }
                }
            }

            val groups = state.groupedPages
            if (groups.isNotEmpty() && groups.any { it.pages.isNotEmpty() }) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    groups.forEach { group ->
                        if (group.pages.isNotEmpty()) {
                            if (state.selectedGrouping != MarkedPageGrouping.NONE) {
                                item(key = "header_${group.title}") {
                                    Text(
                                        text = group.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            items(group.pages, key = { it.id }) { page ->
                                MarkedPageCard(
                                    page = page,
                                    onPreview = { viewModel.setPreviewPage(page) },
                                    onToggleTag = { viewModel.toggleReviewTag(page.id, page.reviewTag) },
                                    onOpenReader = {
                                        onOpenReader(page.chapterId, page.totalPages, page.seriesId, page.volumeId)
                                    },
                                    onDelete = { viewModel.deleteMarkedPage(page.id) }
                                )
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
                        text = if (state.searchQuery.isNotBlank()) "No matching marked pages" else "No pages marked for review",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkedPageCard(
    page: MarkedPageEntity,
    onPreview: () -> Unit,
    onToggleTag: () -> Unit,
    onOpenReader: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPreview() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Thumbnail with Tag Badge Overlay
            Box(
                modifier = Modifier
                    .width(75.dp)
                    .height(110.dp)
                    .clickable { onPreview() }
            ) {
                CoverImage(
                    url = page.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                )

                // Tag Pill Badge Overlay (Debug vs Remove)
                Surface(
                    color = if (page.reviewTag == "REMOVE") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                            else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clickable { onToggleTag() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (page.reviewTag == "REMOVE") Icons.Outlined.Delete else Icons.Default.BugReport,
                            contentDescription = null,
                            tint = if (page.reviewTag == "REMOVE") MaterialTheme.colorScheme.onErrorContainer
                                   else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (page.reviewTag == "REMOVE") "REMOVE" else "DEBUG",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            ),
                            color = if (page.reviewTag == "REMOVE") MaterialTheme.colorScheme.onErrorContainer
                                   else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = page.seriesName.ifBlank { page.chapterTitle },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = page.chapterTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (page.totalPages > 0) "Page ${page.pageIndex + 1} of ${page.totalPages}" else "Page ${page.pageIndex + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleTag,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = if (page.reviewTag == "REMOVE") Icons.Outlined.Delete else Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (page.reviewTag == "REMOVE") "To Remove" else "Debug",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    IconButton(
                        onClick = onOpenReader,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Open in Reader",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove Mark",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageReviewSettingsSheet(
    settings: com.zemmy.sigurvita.core.model.ReaderSettings,
    onUpdateSpeechBubbleDetection: (Boolean) -> Unit,
    onUpdateSpeechBubbleBoxes: (Boolean) -> Unit,
    onUpdateSpeechBubbleConfidence: (Float) -> Unit,
    onUpdateFitMode: (FitMode) -> Unit,
    onUpdatePanelAwareOrder: (Boolean) -> Unit,
    onUpdatePanelDebugOverlay: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Reader & ML Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fit Mode Selector
            Text("Fit Mode", style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                FitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = (settings.fitMode == mode),
                        onClick = { onUpdateFitMode(mode) },
                        label = { Text(stringResource(mode.stringRes)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 1. Feature: Speech Bubble AI Detection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Speech Bubble AI Detection", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "On-device TFLite speech bubble detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.speechBubbleDetectionEnabled,
                    onCheckedChange = onUpdateSpeechBubbleDetection
                )
            }

            if (settings.speechBubbleDetectionEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // 2. Feature: Speech Bubble Highlight Boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speech Bubble Highlight Boxes", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Render detection bounding boxes on comic pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.speechBubbleBoxesEnabled,
                        onCheckedChange = onUpdateSpeechBubbleBoxes
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Feature Tuning: Detection Confidence Threshold
                var sliderVal by remember(settings.speechBubbleConfidence) { mutableFloatStateOf(settings.speechBubbleConfidence) }
                Text(
                    text = "Detection Confidence Threshold: ${(sliderVal * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = { onUpdateSpeechBubbleConfidence(sliderVal) },
                    valueRange = 0.02f..0.50f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Feature Option: Panel-Aware Reading Order
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Panel-Aware Reading Order", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Order speech bubbles and panels logically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.panelAwareOrderEnabled,
                        onCheckedChange = onUpdatePanelAwareOrder
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 5. Debug After: Panel Map Detection / Debug Overlay
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Panel Map Detection", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Highlight detected panel outlines on comic pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.panelDebugOverlayEnabled,
                    onCheckedChange = onUpdatePanelDebugOverlay
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
