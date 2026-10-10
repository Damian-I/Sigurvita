package com.zemmy.sigurvita.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialLibraryId: Int = 0,
    onBackClick: () -> Unit,
    onSeriesClick: (Int) -> Unit,
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
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                text = stringResource(R.string.incognito_active_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Library Filter Chips using modularized FilterChipGroup
                FilterChipGroup(
                    items = filterLibraries,
                    selectedItem = currentSelectedLib,
                    onItemSelected = { viewModel.selectLibrary(it.id) },
                    labelMapper = { if (it.id == 0) stringResource(R.string.all_libraries) else it.name }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.filteredSeries.isNotEmpty()) {
                    val rows = state.filteredSeries.chunked(3)
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
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
