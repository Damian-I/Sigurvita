package com.zemmy.sigurvita.ui.screens.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
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

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .onSizeChanged { screenSize = it }
    ) {
        if (screenSize.width > 0 && screenSize.height > 0) {
            val screenW = screenSize.width.toFloat()
            val screenH = screenSize.height.toFloat()

            val magFactor = 2.0f
            val cropW = if (magnifiedBubble.cropSizeOnScreen.width > 0f) magnifiedBubble.cropSizeOnScreen.width else 150f
            val cropH = if (magnifiedBubble.cropSizeOnScreen.height > 0f) magnifiedBubble.cropSizeOnScreen.height else 100f

            val magW = cropW * magFactor
            val magH = cropH * magFactor

            val anchorX = magnifiedBubble.anchorScreenPx.x
            val anchorY = magnifiedBubble.anchorScreenPx.y

            val relTipX = magnifiedBubble.tipRelOffset.x.coerceIn(0f, 1f)
            val relTipY = magnifiedBubble.tipRelOffset.y.coerceIn(0f, 1f)

            // Pin the magnified bubble's tail tip (or box corner) directly on top of the original bubble's tip,
            // so it grows outwards from the tip and completely covers the original bubble.
            val rawLeft = anchorX - relTipX * magW
            val rawTop = anchorY - relTipY * magH

            val padding = with(density) { 8.dp.toPx() }
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
                        scaleY = animScale,
                        transformOrigin = TransformOrigin(relTipX, relTipY)
                    ),
                color = Color.Transparent
            ) {
                Image(
                    bitmap = magnifiedBubble.bitmap.asImageBitmap(),
                    contentDescription = "Magnified Speech Bubble",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
