package com.zemmy.sigurvita.core.model

data class MagnifiedBubble(
    val pageIndex: Int,
    val bubbleId: Int,
    val bitmap: android.graphics.Bitmap,
    val anchorScreenPx: androidx.compose.ui.geometry.Offset,
    val cropSizeOnScreen: androidx.compose.ui.geometry.Size,
    val tipRelOffset: androidx.compose.ui.geometry.Offset,
    val isLiteralBox: Boolean = false
)

data class ReaderPage(
    val index: Int,
    val pageNumber: Int,
    val imageUrl: String,
    val isLocal: Boolean = false
)
