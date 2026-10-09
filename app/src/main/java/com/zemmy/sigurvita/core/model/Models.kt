package com.zemmy.sigurvita.core.model

import androidx.annotation.StringRes
import com.zemmy.sigurvita.R

enum class ReadingMode(@param:StringRes val stringRes: Int) {
    RTL(R.string.reading_mode_rtl),
    LTR(R.string.reading_mode_ltr),
    WEBTOON(R.string.reading_mode_webtoon)
}

enum class FitMode(@param:StringRes val stringRes: Int) {
    FIT_WIDTH(R.string.fit_mode_width),
    FIT_HEIGHT(R.string.fit_mode_height),
    FIT_SCREEN(R.string.fit_mode_screen)
}

enum class AutoDeleteOption(@param:StringRes val stringRes: Int) {
    NEVER(R.string.auto_delete_never),
    IMMEDIATELY(R.string.auto_delete_immediately),
    AFTER_1_ISSUE(R.string.auto_delete_after_1),
    AFTER_2_ISSUES(R.string.auto_delete_after_2),
    AFTER_5_ISSUES(R.string.auto_delete_after_5)
}

data class ServerConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val jwtToken: String = "",
    val userId: Int = 0,
    val isLoggedIn: Boolean = false
)

data class ReaderSettings(
    val readingMode: ReadingMode = ReadingMode.RTL,
    val fitMode: FitMode = FitMode.FIT_WIDTH,
    val volumeKeyNavigationEnabled: Boolean = true,
    val volumeKeyInvert: Boolean = false,
    val touchEdgeNavigationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoDeleteOption: AutoDeleteOption = AutoDeleteOption.NEVER,
    val isIncognitoMode: Boolean = false,
    val speechBubbleDetectionEnabled: Boolean = true,
    val speechBubbleBoxesEnabled: Boolean = true,
    val speechBubbleConfidence: Float = 0.10f,
    val panelAwareOrderEnabled: Boolean = true,
    val panelDebugOverlayEnabled: Boolean = false
)

enum class BubbleCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

data class MagnifiedBubble(
    val pageIndex: Int,
    val bubbleId: Int,
    val bitmap: android.graphics.Bitmap,
    val anchorScreenPx: androidx.compose.ui.geometry.Offset,
    val cropSizeOnScreen: androidx.compose.ui.geometry.Size,
    val tipRelOffset: androidx.compose.ui.geometry.Offset,
    val isLiteralBox: Boolean = false
)

data class Library(
    val id: Int,
    val name: String,
    val type: Int, // 0 = Manga, 1 = Comic, 2 = Book
    val coverUrl: String? = null
) {
    val isManga: Boolean get() = type == 0 || name.contains("manga", ignoreCase = true)
}

data class Series(
    val id: Int,
    val libraryId: Int,
    val name: String,
    val localizedName: String? = null,
    val coverUrl: String? = null,
    val summary: String? = null,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val isCompleted: Boolean = false,
    val userRating: Float = 0f
)

data class Volume(
    val id: Int,
    val seriesId: Int,
    val name: String,
    val number: Float,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val chapters: List<Chapter> = emptyList()
)

data class MangaFile(
    val id: Int = 0,
    val filePath: String? = null,
    val pages: Int = 0,
    val bytes: Long = 0L,
    val format: Int = 0,
    val extension: String? = null
)

data class Chapter(
    val id: Int,
    val volumeId: Int,
    val seriesId: Int,
    val chapterNumber: String,
    val title: String,
    val summary: String? = null,
    val pages: Int,
    val pagesRead: Int = 0,
    val isRead: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val coverUrl: String? = null,
    val files: List<MangaFile> = emptyList()
)

data class ReadingList(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val itemCount: Int = 0,
    val coverUrl: String? = null,
    val promoted: Boolean = false,
    val items: List<ReadingListItem> = emptyList(),
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val issuesRead: Int = 0
)

data class ReadingListItem(
    val id: Int,
    val readingListId: Int,
    val seriesId: Int,
    val seriesName: String,
    val libraryName: String? = null,
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val title: String,
    val coverUrl: String? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isRead: Boolean = false
)

data class ReaderPage(
    val index: Int,
    val pageNumber: Int,
    val imageUrl: String,
    val isLocal: Boolean = false
)

data class DownloadTask(
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryName: String = "Downloads",
    val seriesName: String = "",
    val chapterNumber: String = "",
    val title: String,
    val coverUrl: String?,
    val totalPages: Int,
    val downloadedPages: Int,
    val status: DownloadStatus,
    val errorMessage: String? = null,
    val localPath: String? = null
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class BubbleMask(
    val size: Int,
    val probs: ByteArray
)

data class DetectedBubble(
    val id: Int,
    val box: NormRect,
    val score: Float,
    val classId: Int,
    val mask: BubbleMask
)

data class LetterboxParams(
    val inputSize: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val gain: Float,
    val padX: Float,
    val padY: Float
) {
    fun letterboxToNorm(x: Float, y: Float): Pair<Float, Float> {
        val normX = (x - padX) / (originalWidth * gain)
        val normY = (y - padY) / (originalHeight * gain)
        return Pair(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
    }
}

data class PageBubbles(
    val pageIndex: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val letterbox: LetterboxParams,
    val bubbles: List<DetectedBubble>
)
