package com.zemmy.sigurvita.ui.screens.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.ui.components.ErrorState
import com.zemmy.sigurvita.ui.components.FilterChipGroup
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.SeriesCard
import com.zemmy.sigurvita.ui.components.SigurvitaTopBar

@Composable
fun LibraryScreen(
    initialLibraryId: Int = 0,
    onBackClick: () -> Unit,
    onSeriesClick: (Int) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: LibraryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialLibraryId) {
        viewModel.loadLibrary(initialLibraryId)
    }

    com.zemmy.sigurvita.ui.util.OnResumeEffect {
        viewModel.loadLibrary(state.selectedLibraryId)
    }

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    val filterLibraries = remember(state.libraries) {
        listOf(Library(id = 0, name = "All Libraries", type = 0)) + state.libraries
    }
    val currentSelectedLib = remember(filterLibraries, state.selectedLibraryId) {
        filterLibraries.find { it.id == state.selectedLibraryId } ?: filterLibraries.first()
    }

    Scaffold(
        topBar = {
            SigurvitaTopBar(
                title = state.libraryName,
                isSearchActive = state.isSearchActive,
                searchQuery = state.searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onToggleSearch = { viewModel.toggleSearchActive() },
                isIncognitoMode = state.isIncognitoMode,
                onToggleIncognito = { viewModel.toggleIncognitoMode() },
                focusRequester = focusRequester,
                navigationIcon = {
                    if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading && state.series.isEmpty()) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else if (state.errorMessage != null && state.series.isEmpty()) {
            ErrorState(
                message = state.errorMessage!!,
                onRetry = { viewModel.loadLibrary(state.selectedLibraryId) },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.isIncognitoMode) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.infoContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onInfoContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.incognito_active_message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onInfoContainer
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterChipGroup(
                        items = filterLibraries,
                        selectedItem = currentSelectedLib,
                        onItemSelected = { viewModel.selectLibrary(it.id) },
                        labelMapper = { if (it.id == 0) stringResource(R.string.all_libraries) else it.name },
                        contentPadding = PaddingValues(vertical = 4.dp)
                    )
                }

                if (state.filteredSeries.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No series in this library yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.filteredSeries, key = { it.id }) { series ->
                        SeriesCard(
                            series = series,
                            onClick = { onSeriesClick(series.id) }
                        )
                    }
                }
            }
        }
    }
}
