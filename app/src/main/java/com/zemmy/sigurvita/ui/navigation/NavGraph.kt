package com.zemmy.sigurvita.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.zemmy.sigurvita.ui.screens.auth.AuthScreen
import com.zemmy.sigurvita.ui.screens.downloads.DownloadQueueScreen
import com.zemmy.sigurvita.ui.screens.home.HomeScreen
import com.zemmy.sigurvita.ui.screens.library.LibraryScreen
import com.zemmy.sigurvita.ui.screens.markedpages.MarkedPagesScreen
import com.zemmy.sigurvita.ui.screens.reader.ReaderScreen
import com.zemmy.sigurvita.ui.screens.readinglist.ReadingListScreen
import com.zemmy.sigurvita.ui.screens.seriesdetail.SeriesDetailScreen
import com.zemmy.sigurvita.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun SigurvitaNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Auth.route
) {
    val drawerViewModel: DrawerViewModel = viewModel()
    val drawerUiState by drawerViewModel.uiState.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isAuthScreen = currentRoute == Screen.Auth.route
    val showBottomBar = TopDestination.match(currentRoute) != null
    val currentLibraryId = if (currentRoute?.startsWith("library/") == true) {
        navBackStackEntry?.arguments?.getInt("libraryId")
    } else null

    fun navigateFromDrawer(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateHomeFromDrawer() {
        scope.launch { drawerState.close() }
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isAuthScreen && currentRoute?.startsWith("reader/") != true,
        drawerContent = {
            AppDrawer(
                state = drawerUiState,
                currentRoute = currentRoute,
                currentLibraryId = currentLibraryId,
                onNavigateHome = { navigateHomeFromDrawer() },
                onNavigate = { route -> navigateFromDrawer(route) },
                onSaveLibraryOrder = { ids -> drawerViewModel.saveLibraryOrder(ids) },
                onSetReadingListChosen = { id, chosen -> drawerViewModel.setReadingListChosen(id, chosen) }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    SigurvitaBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .then(
                    if (showBottomBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                    else Modifier
                )
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
                    },
                    onNavigateToReader = { chapterId, totalPages, seriesId, volumeId, readingListId ->
                        navController.navigate(Screen.Reader.createRoute(chapterId, totalPages, seriesId, volumeId, readingListId))
                    },
                    onNavigateToReadingListDetail = { listId ->
                        navController.navigate(Screen.ReadingListDetail.createRoute(listId))
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
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
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
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
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
                    onBackClick = { navController.popBackStack() },
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
                    onBackClick = { navController.popBackStack() },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onChapterClick = { chapterId, totalPages, seriesId, volumeId ->
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
    }
}
