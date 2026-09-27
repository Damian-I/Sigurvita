package com.zemmy.sigurvita.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object ReadingLists : Screen("reading_lists")
    object ReadingListDetail : Screen("reading_list_detail/{listId}") {
        fun createRoute(listId: Int) = "reading_list_detail/$listId"
    }
    object SeriesDetail : Screen("series_detail/{seriesId}") {
        fun createRoute(seriesId: Int) = "series_detail/$seriesId"
    }
    object Reader : Screen("reader/{chapterId}/{totalPages}?seriesId={seriesId}&volumeId={volumeId}") {
        fun createRoute(chapterId: Int, totalPages: Int, seriesId: Int = 0, volumeId: Int = 0) =
            "reader/$chapterId/$totalPages?seriesId=$seriesId&volumeId=$volumeId"
    }
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
}
