package com.zemmy.sigurvita.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.core.util.extractEraInfo
import com.zemmy.sigurvita.core.util.naturalCompare
import com.zemmy.sigurvita.ui.screens.auth.AuthScreen
import com.zemmy.sigurvita.ui.screens.downloads.DownloadQueueScreen
import com.zemmy.sigurvita.ui.screens.home.HomeScreen
import com.zemmy.sigurvita.ui.screens.library.LibraryScreen
import com.zemmy.sigurvita.ui.screens.markedpages.MarkedPagesScreen
import com.zemmy.sigurvita.ui.screens.reader.ReaderScreen
import com.zemmy.sigurvita.ui.screens.readinglist.ReadingListScreen
import com.zemmy.sigurvita.ui.screens.seriesdetail.SeriesDetailScreen
import com.zemmy.sigurvita.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigurvitaNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Auth.route
) {
    val app = SigurvitaApp.instance
    val prefsRepo = app.userPreferencesRepository
    val libraryRepo = app.libraryRepository
    val readingListRepo = app.readingListRepository

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var libraries by remember { mutableStateOf<List<Library>>(emptyList()) }
    var readingLists by remember { mutableStateOf<List<ReadingList>>(emptyList()) }
    var chosenReadingListIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var inProgressReadingListIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var libraryOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showAddReadingListDialog by remember { mutableStateOf(false) }

    // Live dragging state for libraries
    var draggingLibraryIndex by remember { mutableStateOf<Int?>(null) }
    var libraryDragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 48.dp.toPx() }

    LaunchedEffect(Unit) {
        scope.launch {
            val libRes = libraryRepo.getLibraries()
            if (libRes.isSuccess) libraries = libRes.getOrDefault(emptyList())

            val rlRes = readingListRepo.getReadingLists()
            if (rlRes.isSuccess) {
                val fetchedLists = rlRes.getOrDefault(emptyList())
                readingLists = fetchedLists

                // Concurrently check progress for all reading lists in parallel using fast endpoint
                val deferreds = fetchedLists.map { rl ->
                    async {
                        if (readingListRepo.hasReadingListProgress(rl.id)) {
                            rl.id
                        } else null
                    }
                }
                val inProgressIds = deferreds.awaitAll().filterNotNull()
                inProgressReadingListIds = inProgressIds.toSet()
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            prefsRepo.libraryOrderFlow.collect { libraryOrder = it }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            prefsRepo.chosenReadingListsFlow.collect { chosenReadingListIds = it.toSet() }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isAuthScreen = currentRoute == Screen.Auth.route

    if (showAddReadingListDialog) {
        AlertDialog(
            onDismissRequest = { showAddReadingListDialog = false },
            title = { Text("Add Reading Lists to Panel") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(readingLists) { rl ->
                        val isChosen = chosenReadingListIds.contains(rl.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        if (isChosen) prefsRepo.removeChosenReadingList(rl.id)
                                        else prefsRepo.addChosenReadingList(rl.id)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChosen,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        if (checked) prefsRepo.addChosenReadingList(rl.id)
                                        else prefsRepo.removeChosenReadingList(rl.id)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = cleanReadingListTitle(rl.title), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddReadingListDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    val orderedLibraries = remember(libraries, libraryOrder) {
        if (libraryOrder.isEmpty()) libraries
        else {
            val map = libraries.associateBy { it.id }
            val sorted = libraryOrder.mapNotNull { map[it] }
            val remaining = libraries.filter { !libraryOrder.contains(it.id) }
            sorted + remaining
        }
    }

    // Reading lists sorted by era, then alphanumerically inside each era
    val readingListsByEra = remember(readingLists, chosenReadingListIds, inProgressReadingListIds) {
        val activeLists = readingLists.filter { chosenReadingListIds.contains(it.id) || inProgressReadingListIds.contains(it.id) }
        val grouped = activeLists.groupBy { extractEraInfo(it.title).name }
        grouped.entries
            .sortedBy { it.key }
            .associate { (era, lists) ->
                era to lists.sortedWith { l1, l2 -> naturalCompare(cleanReadingListTitle(l1.title), cleanReadingListTitle(l2.title)) }
            }
    }

    var expandedEras by remember(readingListsByEra) {
        mutableStateOf(readingListsByEra.keys.toSet())
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isAuthScreen,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sigurvita",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Home
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text("Home") },
                            selected = currentRoute == Screen.Home.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    // Libraries Header
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text("Libraries (All)") },
                            selected = currentRoute?.startsWith("library") == true,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.Library.createRoute(0))
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    // Individual Libraries (live drag to reorder with fluid surrounding items displacement)
                    itemsIndexed(orderedLibraries, key = { _, lib -> lib.id }) { index, library ->
                        val isDragging = (draggingLibraryIndex == index)
                        val targetIndex = remember(draggingLibraryIndex, libraryDragOffsetY, orderedLibraries.size) {
                            if (draggingLibraryIndex == null) null
                            else {
                                val delta = (libraryDragOffsetY / itemHeightPx).roundToInt()
                                (draggingLibraryIndex!! + delta).coerceIn(0, orderedLibraries.size - 1)
                            }
                        }

                        val shiftPx = remember(index, draggingLibraryIndex, targetIndex) {
                            if (draggingLibraryIndex == null || targetIndex == null || isDragging) 0f
                            else {
                                val startIndex = draggingLibraryIndex!!
                                if (startIndex < targetIndex && index in (startIndex + 1)..targetIndex) {
                                    -itemHeightPx
                                } else if (startIndex > targetIndex && index in targetIndex..<startIndex) {
                                    itemHeightPx
                                } else {
                                    0f
                                }
                            }
                        }

                        val animatedShiftY by animateFloatAsState(
                            targetValue = shiftPx,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "itemShift"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 10f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) libraryDragOffsetY else animatedShiftY
                                    shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                                }
                                .clickable {
                                    if (draggingLibraryIndex == null) {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.Library.createRoute(library.id))
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${library.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .pointerInput(library.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingLibraryIndex = index
                                                libraryDragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                libraryDragOffsetY += dragAmount.y
                                            },
                                            onDragEnd = {
                                                val start = draggingLibraryIndex
                                                val target = targetIndex
                                                if (start != null && target != null && start != target) {
                                                    val mutable = orderedLibraries.map { it.id }.toMutableList()
                                                    val item = mutable.removeAt(start)
                                                    mutable.add(target, item)
                                                    scope.launch { prefsRepo.updateLibraryOrder(mutable) }
                                                }
                                                draggingLibraryIndex = null
                                                libraryDragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingLibraryIndex = null
                                                libraryDragOffsetY = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Drag to Rearrange",
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Reading Lists Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(Screen.ReadingLists.route)
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Reading Lists", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showAddReadingListDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Reading List")
                            }
                        }
                    }

                    // Era Folders & Reading Lists Tree View (Alphanumerically sorted)
                    readingListsByEra.forEach { (eraName, listsInEra) ->
                        val isExpanded = expandedEras.contains(eraName)
                        item(key = "era_$eraName") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedEras = if (isExpanded) expandedEras - eraName else expandedEras + eraName
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$eraName (${listsInEra.size})",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (isExpanded) {
                            items(listsInEra, key = { rl -> rl.id }) { rl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch { drawerState.close() }
                                            navController.navigate(Screen.ReadingListDetail.createRoute(rl.id))
                                        }
                                        .padding(start = 40.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${cleanReadingListTitle(rl.title)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Downloads
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Download, contentDescription = null) },
                            label = { Text("Downloads") },
                            selected = currentRoute == Screen.Downloads.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.Downloads.route)
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    // Page Review
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                            label = { Text("Page Review") },
                            selected = currentRoute == Screen.MarkedPages.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.MarkedPages.route)
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    // Settings
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings") },
                            selected = currentRoute == Screen.Settings.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.Settings.route)
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onSeriesClick = { seriesId ->
                        navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            composable(
                route = Screen.Library.route,
                arguments = listOf(navArgument("libraryId") { type = NavType.IntType; defaultValue = 0 })
            ) { backStackEntry ->
                val libraryId = backStackEntry.arguments?.getInt("libraryId") ?: 0
                LibraryScreen(
                    initialLibraryId = libraryId,
                    onBackClick = { navController.popBackStack() },
                    onSeriesClick = { seriesId ->
                        navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                    }
                )
            }

            composable(
                route = Screen.ReadingLists.route,
                arguments = listOf(navArgument("eraName") { type = NavType.StringType; defaultValue = "" })
            ) { backStackEntry ->
                val eraName = backStackEntry.arguments?.getString("eraName") ?: ""
                ReadingListScreen(
                    initialEraName = eraName,
                    onBackClick = { navController.popBackStack() },
                    onSeriesClick = { seriesId ->
                        navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                    },
                    onChapterClick = { chapterId, seriesId, volumeId, readingListId ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                chapterId = chapterId,
                                totalPages = 0,
                                seriesId = seriesId,
                                volumeId = volumeId,
                                readingListId = readingListId
                            )
                        )
                    }
                )
            }

            composable(
                route = Screen.ReadingListDetail.route,
                arguments = listOf(navArgument("listId") { type = NavType.IntType })
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getInt("listId") ?: 0
                ReadingListScreen(
                    initialReadingListId = listId,
                    onBackClick = { navController.popBackStack() },
                    onSeriesClick = { seriesId ->
                        navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                    },
                    onChapterClick = { chapterId, seriesId, volumeId, readingListId ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                chapterId = chapterId,
                                totalPages = 0,
                                seriesId = seriesId,
                                volumeId = volumeId,
                                readingListId = readingListId
                            )
                        )
                    }
                )
            }

            composable(
                route = Screen.SeriesDetail.route,
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType })
            ) { backStackEntry ->
                val seriesId = backStackEntry.arguments?.getInt("seriesId") ?: 0
                SeriesDetailScreen(
                    seriesId = seriesId,
                    onChapterClick = { chapterId, totalPages, volumeId ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                chapterId = chapterId,
                                totalPages = totalPages,
                                seriesId = seriesId,
                                volumeId = volumeId
                            )
                        )
                    },
                    onLibraryClick = { libId ->
                        navController.navigate(Screen.Library.createRoute(libId))
                    },
                    onReadingListClick = { listId ->
                        navController.navigate(Screen.ReadingListDetail.createRoute(listId))
                    },
                    onEraClick = { eraName ->
                        navController.navigate(Screen.ReadingLists.createRoute(eraName))
                    }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("chapterId") { type = NavType.IntType },
                    navArgument("totalPages") { type = NavType.IntType },
                    navArgument("seriesId") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("volumeId") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("readingListId") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { backStackEntry ->
                val chapterId = backStackEntry.arguments?.getInt("chapterId") ?: 0
                val totalPages = backStackEntry.arguments?.getInt("totalPages") ?: 0
                val seriesId = backStackEntry.arguments?.getInt("seriesId") ?: 0
                val volumeId = backStackEntry.arguments?.getInt("volumeId") ?: 0
                val readingListId = backStackEntry.arguments?.getInt("readingListId") ?: 0

                ReaderScreen(
                    chapterId = chapterId,
                    totalPages = totalPages,
                    seriesId = seriesId,
                    volumeId = volumeId,
                    readingListId = readingListId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Downloads.route) {
                DownloadQueueScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.MarkedPages.route) {
                MarkedPagesScreen(
                    onBackClick = { navController.popBackStack() },
                    onOpenReader = { chapterId, totalPages, seriesId, volumeId ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                chapterId = chapterId,
                                totalPages = totalPages,
                                seriesId = seriesId,
                                volumeId = volumeId
                            )
                        )
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onLogoutClick = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
