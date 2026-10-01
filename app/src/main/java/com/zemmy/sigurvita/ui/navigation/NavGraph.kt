package com.zemmy.sigurvita.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zemmy.sigurvita.ui.screens.auth.AuthScreen
import com.zemmy.sigurvita.ui.screens.downloads.DownloadQueueScreen
import com.zemmy.sigurvita.ui.screens.home.HomeScreen
import com.zemmy.sigurvita.ui.screens.reader.ReaderScreen
import com.zemmy.sigurvita.ui.screens.readinglist.ReadingListScreen
import com.zemmy.sigurvita.ui.screens.seriesdetail.SeriesDetailScreen
import com.zemmy.sigurvita.ui.screens.settings.SettingsScreen

@Composable
fun SigurvitaNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Auth.route
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
                onReadingListsClick = {
                    navController.navigate(Screen.ReadingLists.route)
                },
                onDownloadsClick = {
                    navController.navigate(Screen.Downloads.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.ReadingLists.route) {
            ReadingListScreen(
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
            route = "reading_list_detail/{listId}",
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
                onLibraryClick = { _ ->
                    navController.navigate(Screen.Home.route)
                },
                onReadingListClick = { listId ->
                    navController.navigate("reading_list_detail/$listId")
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
