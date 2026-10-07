package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.zemmy.sigurvita.core.model.PageBubbles
import kotlin.math.min

fun viewportToPageNorm(
    tap: Offset,
    box: IntSize,
    pageW: Int,
    pageH: Int,
    contentScale: ContentScale,
    zoom: Float,
    pan: Offset
): Offset? {
    val boxW = box.width.toFloat()
    val boxH = box.height.toFloat()
    if (boxW <= 0f || boxH <= 0f || pageW <= 0f || pageH <= 0f) return null

    val c = Offset(boxW / 2f, boxH / 2f)
    // Undo zoom and pan transformation
    val unscaled = (tap - c - pan) / zoom + c

    val s = when (contentScale) {
        ContentScale.FillWidth -> boxW / pageW
        ContentScale.FillHeight -> boxH / pageH
        else -> min(boxW / pageW, boxH / pageH)
    }

    val dispW = pageW * s
    val dispH = pageH * s
    val originX = (boxW - dispW) / 2f
    val originY = (boxH - dispH) / 2f

    val pageX = (unscaled.x - originX) / dispW
    val pageY = (unscaled.y - originY) / dispH

    if (pageX !in 0f..1f || pageY !in 0f..1f) return null
    return Offset(pageX, pageY)
}

fun hitBubble(pageBubbles: PageBubbles, normPoint: Offset): Int? {
    for (bubble in pageBubbles.bubbles) {
        if (normPoint.x in bubble.box.left..bubble.box.right &&
            normPoint.y in bubble.box.top..bubble.box.bottom) {
            return bubble.id
        }
    }
    return null
}
