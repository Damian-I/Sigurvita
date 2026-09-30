package com.zemmy.sigurvita.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.SeriesCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSeriesClick: (Int) -> Unit,
    onReadingListsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sigurvita", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.toggleIncognitoMode() }) {
                        Icon(
                            imageVector = if (state.isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Incognito Mode",
                            tint = if (state.isIncognitoMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onReadingListsClick) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Reading Lists")
                    }
                    IconButton(onClick = onDownloadsClick) {
                        Icon(Icons.Default.Download, contentDescription = "Downloads")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                                text = "Incognito Mode Active: Reading progress is private and won't sync to Kavita.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                if (state.onDeckSeries.isNotEmpty()) {
                    SectionHeader(title = "On Deck (Continue Reading)")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.onDeckSeries, key = { it.id }) { series ->
                            SeriesCard(
                                series = series,
                                onClick = { onSeriesClick(series.id) },
                                modifier = Modifier.width(110.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (state.recentlyUpdatedSeries.isNotEmpty()) {
                    SectionHeader(title = "Recently Updated")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.recentlyUpdatedSeries, key = { it.id }) { series ->
                            SeriesCard(
                                series = series,
                                onClick = { onSeriesClick(series.id) },
                                modifier = Modifier.width(110.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                SectionHeader(title = "Libraries")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = (state.selectedLibraryId == 0),
                            onClick = { viewModel.selectLibrary(0) },
                            label = { Text("All Libraries") }
                        )
                    }
                    items(state.libraries, key = { it.id }) { library ->
                        FilterChip(
                            selected = (state.selectedLibraryId == library.id),
                            onClick = { viewModel.selectLibrary(library.id) },
                            label = { Text(library.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.selectedLibrarySeries.isNotEmpty()) {
                    val rows = state.selectedLibrarySeries.chunked(3)
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
                                // Fill remaining spaces if last row has fewer than 3 items
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No series found in this library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
