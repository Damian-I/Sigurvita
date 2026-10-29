package com.zemmy.sigurvita.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Library : Screen("library/{libraryId}") {
        fun createRoute(libraryId: Int) = "library/$libraryId"
    }
    object ReadingLists : Screen("reading_lists?eraName={eraName}") {
        fun createRoute(eraName: String = "") = if (eraName.isNotBlank()) "reading_lists?eraName=$eraName" else "reading_lists"
    }
    object ReadingListDetail : Screen("reading_list_detail/{listId}") {
        fun createRoute(listId: Int) = "reading_list_detail/$listId"
    }
    object SeriesDetail : Screen("series_detail/{seriesId}") {
        fun createRoute(seriesId: Int) = "series_detail/$seriesId"
    }
    object Reader : Screen("reader/{chapterId}/{totalPages}?seriesId={seriesId}&volumeId={volumeId}&readingListId={readingListId}&incognito={incognito}") {
        fun createRoute(chapterId: Int, totalPages: Int, seriesId: Int = 0, volumeId: Int = 0, readingListId: Int = 0, incognito: Boolean = false) =
            "reader/$chapterId/$totalPages?seriesId=$seriesId&volumeId=$volumeId&readingListId=$readingListId&incognito=${if (incognito) 1 else 0}"
    }
    object Downloads : Screen("downloads")
    object MarkedPages : Screen("marked_pages")
    object Settings : Screen("settings")
}
