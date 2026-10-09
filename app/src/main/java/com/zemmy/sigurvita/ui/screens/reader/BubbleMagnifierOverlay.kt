package com.zemmy.sigurvita.ui.screens.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.model.MagnifiedBubble

@Composable
fun BubbleMagnifierOverlay(
    magnifiedBubble: MagnifiedBubble,
    speechBubbleBoxesEnabled: Boolean,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    val density = LocalDensity.current

    val animScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "MagnifierScale"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }

        val magFactor = 2.0f
        val origW = if (magnifiedBubble.bubbleSizePx.width > 0f) magnifiedBubble.bubbleSizePx.width else 150f
        val origH = if (magnifiedBubble.bubbleSizePx.height > 0f) magnifiedBubble.bubbleSizePx.height else 100f

        val magW = origW * magFactor
        val magH = origH * magFactor

        val center = magnifiedBubble.centerOffsetPx
        val rawLeft = center.x - magW / 2f
        val rawTop = center.y - magH / 2f

        val padding = with(density) { 16.dp.toPx() }
        val leftPx = rawLeft.coerceIn(padding, maxOf(padding, screenW - magW - padding))
        val topPx = rawTop.coerceIn(padding, maxOf(padding, screenH - magH - padding))

        val leftDp = with(density) { leftPx.toDp() }
        val topDp = with(density) { topPx.toDp() }
        val magWDp = with(density) { magW.toDp() }
        val magHDp = with(density) { magH.toDp() }

        Surface(
            modifier = Modifier
                .offset(x = leftDp, y = topDp)
                .size(width = magWDp, height = magHDp)
                .graphicsLayer(
                    scaleX = animScale,
                    scaleY = animScale
                )
                .shadow(elevation = if (speechBubbleBoxesEnabled) 16.dp else 8.dp, shape = RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = if (speechBubbleBoxesEnabled) BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)) else null
        ) {
            Image(
                bitmap = magnifiedBubble.bitmap.asImageBitmap(),
                contentDescription = "Magnified Speech Bubble",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            )
        }
    }
}
