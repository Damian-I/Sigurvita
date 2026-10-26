package com.zemmy.sigurvita.core.model

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
