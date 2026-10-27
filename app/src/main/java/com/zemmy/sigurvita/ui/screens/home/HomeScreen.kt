package com.zemmy.sigurvita.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.datastore.SeriesReadContext
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.SeriesCard
import com.zemmy.sigurvita.ui.components.coverImageRequest
import com.zemmy.sigurvita.ui.theme.SigurvitaTheme
import com.zemmy.sigurvita.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSeriesClick: (Int) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToReader: (chapterId: Int, totalPages: Int, seriesId: Int, volumeId: Int, readingListId: Int) -> Unit = { _, _, _, _, _ -> },
    onNavigateToReadingListDetail: (readingListId: Int) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.onDeckSeries, state.recentlyUpdatedSeries, state.newlyAddedSeries, state.downloadedSeries) {
        val loader = context.imageLoader
        (state.onDeckSeries + state.recentlyUpdatedSeries + state.newlyAddedSeries + state.downloadedSeries)
            .mapNotNull { it.coverUrl }
            .distinct()
            .take(80)
            .forEach { loader.enqueue(coverImageRequest(context, it)) }
    }
    val focusRequester = remember { FocusRequester() }

    var dialogSeries by remember { mutableStateOf<Series?>(null) }
    var dialogReadingLists by remember { mutableStateOf<List<ReadingList>>(emptyList()) }

    dialogSeries?.let { series ->
        val currentContext = state.seriesReadContexts[series.id]
        OnDeckContextSelectionDialog(
            seriesName = series.name,
            currentContext = currentContext,
            availableReadingLists = dialogReadingLists,
            onSelectLibrary = {
                viewModel.updateSeriesReadContext(series.id, "LIBRARY")
                dialogSeries = null
                onSeriesClick(series.id)
            },
            onSelectReadingList = { listId, listTitle ->
                viewModel.updateSeriesReadContext(series.id, "READING_LIST", listId, listTitle)
                dialogSeries = null
                viewModel.onOnDeckClick(series, onNavigateToReader, onSeriesClick, onNavigateToReadingListDetail)
            },
            onDismissRequest = { dialogSeries = null }
        )
    }

    com.zemmy.sigurvita.ui.util.OnResumeEffect {
        viewModel.loadData()
    }

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearchActive) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search everything...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    } else {
                        Column(modifier = Modifier.clickable { onOpenDrawer() }) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (state.isSearchActive) {
                        IconButton(onClick = { viewModel.toggleSearchActive() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                        }
                    }
                },
                actions = {
                    if (state.isSearchActive) {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleSearchActive() }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = { viewModel.toggleIncognitoMode() }) {
                            Icon(
                                imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.toggle_incognito),
                                tint = if (state.isIncognitoMode) SigurvitaTheme.colors.incognito else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val hasContent = state.onDeckSeries.isNotEmpty() ||
                         state.recentlyUpdatedSeries.isNotEmpty() ||
                         state.newlyAddedSeries.isNotEmpty() ||
                         state.downloadedSeries.isNotEmpty() ||
                         state.allSeries.isNotEmpty()
        if (state.isLoading && !hasContent) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else if (state.errorMessage != null && !hasContent) {
            ErrorState(
                message = state.errorMessage!!,
                onRetry = { viewModel.loadData() },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                if (state.isOffline) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SigurvitaTheme.colors.warningContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = SigurvitaTheme.colors.onWarningContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Offline Mode: Connection to Kavita failed. Showing cached & downloaded content.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SigurvitaTheme.colors.onWarningContainer
                            )
                        }
                    }
                }

                if (state.isIncognitoMode) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SigurvitaTheme.colors.infoContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = SigurvitaTheme.colors.onInfoContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.incognito_active_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = SigurvitaTheme.colors.onInfoContainer
                            )
                        }
                    }
                }

                if (state.searchQuery.isNotBlank()) {
                    // Search Everything across all series
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.searchResults.isNotEmpty()) {
                        val rows = remember(state.searchResults) { state.searchResults.chunked(3) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { series ->
                                        SeriesCard(
                                            series = series,
                                            onClick = { onSeriesClick(series.id) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No series found for \"${state.searchQuery}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {

                    if (state.onDeckSeries.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.on_deck_title))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.onDeckSeries, key = { it.id }) { series ->
                                val readContext = state.seriesReadContexts[series.id]
                                SeriesCard(
                                    series = series,
                                    readContext = readContext,
                                    onClick = {
                                        viewModel.onOnDeckClick(
                                            series = series,
                                            onNavigateToReader = onNavigateToReader,
                                            onNavigateToSeriesDetail = onSeriesClick,
                                            onNavigateToReadingListDetail = onNavigateToReadingListDetail
                                        )
                                    },
                                    onLongClick = {
                                        dialogSeries = series
                                        viewModel.getAvailableReadingListsForNextChapter(series.id) { lists ->
                                            dialogReadingLists = lists
                                        }
                                    },
                                    modifier = Modifier.width(124.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }

                    if (state.recentlyUpdatedSeries.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.recently_updated_title))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.recentlyUpdatedSeries, key = { it.id }) { series ->
                                SeriesCard(
                                    series = series,
                                    onClick = { onSeriesClick(series.id) },
                                    modifier = Modifier.width(124.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }

                    if (state.newlyAddedSeries.isNotEmpty()) {
                        SectionHeader(title = "Newly Added")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.newlyAddedSeries, key = { it.id }) { series ->
                                SeriesCard(
                                    series = series,
                                    onClick = { onSeriesClick(series.id) },
                                    modifier = Modifier.width(124.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }

                    if (state.downloadedSeries.isNotEmpty()) {
                        SectionHeader(title = "Downloaded")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.downloadedSeries, key = { "dl_${it.id}" }) { series ->
                                SeriesCard(
                                    series = series,
                                    onClick = { onSeriesClick(series.id) },
                                    modifier = Modifier.width(124.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .width(3.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun OnDeckContextSelectionDialog(
    seriesName: String,
    currentContext: SeriesReadContext?,
    availableReadingLists: List<ReadingList>,
    onSelectLibrary: () -> Unit,
    onSelectReadingList: (readingListId: Int, readingListTitle: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Choose Reading Context") },
        text = {
            Column {
                Text(
                    text = "Select how you want to continue reading \"$seriesName\":",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val isLibrarySelected = currentContext?.originType != "READING_LIST"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLibrary() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLibrarySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Library Order",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Continue sequentially through issues in series order",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (availableReadingLists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reading List for Next Issue:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    availableReadingLists.forEach { list ->
                        val isListSelected = currentContext?.originType == "READING_LIST" && currentContext.readingListId == list.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectReadingList(list.id, list.title) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isListSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cleanReadingListTitle(list.title),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${list.itemCount} items in list",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
