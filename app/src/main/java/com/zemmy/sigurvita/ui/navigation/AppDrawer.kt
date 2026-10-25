package com.zemmy.sigurvita.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.util.cleanReadingListTitle

/**
 * The sliding navigation panel. Pure UI: all data comes from [DrawerUiState]; navigation and persistence are
 * reported through callbacks so [SigurvitaNavGraph] stays in charge of the NavController and the drawer state.
 */
@Composable
fun AppDrawer(
    state: DrawerUiState,
    currentRoute: String?,
    currentLibraryId: Int?,
    onNavigateHome: () -> Unit,
    onNavigate: (route: String) -> Unit,
    onSaveLibraryOrder: (List<Int>) -> Unit,
    onSetReadingListChosen: (listId: Int, chosen: Boolean) -> Unit
) {
    var showAddReadingListDialog by remember { mutableStateOf(false) }

    val dragState = rememberLibraryDragState(state.libraries)
    val itemHeightPx = with(LocalDensity.current) { 48.dp.toPx() }

    var expandedEras by remember(state.hierarchy) {
        mutableStateOf(state.hierarchy.map { it.eraName }.toSet())
    }
    var expandedParts by remember(state.hierarchy) {
        mutableStateOf(state.hierarchy.flatMap { era -> era.parts.map { "${era.eraName}_${it.partName}" } }.toSet())
    }

    if (showAddReadingListDialog) {
        AddReadingListsDialog(
            readingLists = state.readingLists,
            chosenIds = state.chosenReadingListIds,
            onSetChosen = onSetReadingListChosen,
            onDismiss = { showAddReadingListDialog = false }
        )
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        DrawerHeader(serverUrl = state.serverUrl)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item { DrawerSectionLabel("Browse") }

            item {
                DrawerNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = currentRoute == Screen.Home.route,
                    onClick = onNavigateHome
                )
            }

            item {
                DrawerNavItem(
                    icon = Icons.Default.Folder,
                    label = "Libraries (All)",
                    selected = currentRoute?.startsWith("library") == true,
                    onClick = { onNavigate(Screen.Library.createRoute(0)) }
                )
            }

            drawerLibraries(
                dragState = dragState,
                currentLibraryId = currentLibraryId,
                itemHeightPx = itemHeightPx,
                onOpenLibrary = { id -> onNavigate(Screen.Library.createRoute(id)) },
                onOrderChanged = onSaveLibraryOrder
            )

            item { DrawerSectionLabel("Reading lists") }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Screen.ReadingLists.createRoute()) }
                        .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "All reading lists",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showAddReadingListDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Reading List")
                    }
                }
            }

            drawerReadingListTree(
                hierarchy = state.hierarchy,
                expandedEras = expandedEras,
                expandedParts = expandedParts,
                onToggleEra = { era ->
                    expandedEras = if (expandedEras.contains(era)) expandedEras - era else expandedEras + era
                },
                onTogglePart = { part ->
                    expandedParts = if (expandedParts.contains(part)) expandedParts - part else expandedParts + part
                },
                onOpenReadingList = { id -> onNavigate(Screen.ReadingListDetail.createRoute(id)) }
            )

            item { DrawerSectionLabel("More") }

            item {
                DrawerNavItem(
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    selected = currentRoute == Screen.Downloads.route,
                    onClick = { onNavigate(Screen.Downloads.route) }
                )
            }

            item {
                DrawerNavItem(
                    icon = Icons.Default.BookmarkBorder,
                    label = "Page Review",
                    selected = currentRoute == Screen.MarkedPages.route,
                    onClick = { onNavigate(Screen.MarkedPages.route) }
                )
            }

            item {
                DrawerNavItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = currentRoute == Screen.Settings.route,
                    onClick = { onNavigate(Screen.Settings.route) }
                )
            }
        }
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun AddReadingListsDialog(
    readingLists: List<ReadingList>,
    chosenIds: Set<Int>,
    onSetChosen: (listId: Int, chosen: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reading Lists to Panel") },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(readingLists) { rl ->
                    val isChosen = chosenIds.contains(rl.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetChosen(rl.id, !isChosen) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChosen,
                            onCheckedChange = { checked -> onSetChosen(rl.id, checked) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = cleanReadingListTitle(rl.title), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
