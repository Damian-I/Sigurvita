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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.model.MagnifiedBubble
import com.zemmy.sigurvita.core.model.PageBubbles

@Composable
fun BubbleMagnifierOverlay(
    magnifiedBubble: MagnifiedBubble,
    speechBubbleBoxesEnabled: Boolean,
    onDismiss: () -> Unit,
    bubbles: PageBubbles? = null,
    contentScale: ContentScale = ContentScale.Fit,
    onBubbleHover: ((Int, Int?, Offset?, Size?) -> Unit)? = null,
    // When true (and the page is paged, not webtoon) the page-transition zones step bubbles instead of dismissing.
    edgeTapNavigationEnabled: Boolean = false,
    tapRegions: List<TapRegion> = defaultTapRegions,
    // Called with `isNext` for a tap in a page-transition zone; the regions already account for reading direction.
    onEdgeTap: (isNext: Boolean) -> Unit = {}
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnEdgeTap by rememberUpdatedState(onEdgeTap)

    val density = LocalDensity.current

    val animScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "MagnifierScale"
    )

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    val hoverModifier = if (bubbles != null && onBubbleHover != null) {
        Modifier.pointerInput(magnifiedBubble.pageIndex, bubbles, contentScale) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull()
                    if (change != null && change.type == PointerType.Stylus && !change.pressed) {
                        val offset = change.position
                        val norm = viewportToPageNorm(
                            tap = offset,
                            box = size,
                            pageW = bubbles.pageWidth,
                            pageH = bubbles.pageHeight,
                            contentScale = contentScale,
                            zoom = 1f,
                            pan = Offset.Zero
                        )
                        val hitId = if (norm != null) hitBubble(bubbles, norm) else null
                        if (hitId != null && hitId != magnifiedBubble.bubbleId) {
                            val hitBubble = bubbles.bubbles.find { it.id == hitId }
                            if (hitBubble != null) {
                                val boxW = size.width.toFloat()
                                val boxH = size.height.toFloat()
                                val pageW = bubbles.pageWidth.toFloat()
                                val pageH = bubbles.pageHeight.toFloat()
                                val s = when (contentScale) {
                                    ContentScale.FillWidth -> boxW / pageW
                                    ContentScale.FillHeight -> boxH / pageH
                                    else -> minOf(boxW / pageW, boxH / pageH)
                                }
                                val dispW = pageW * s
                                val dispH = pageH * s
                                val originX = (boxW - dispW) / 2f
                                val originY = (boxH - dispH) / 2f

                                val centerX = originX + (hitBubble.box.left + hitBubble.box.right) / 2f * dispW
                                val centerY = originY + (hitBubble.box.top + hitBubble.box.bottom) / 2f * dispH
                                val bw = (hitBubble.box.right - hitBubble.box.left) * dispW
                                val bh = (hitBubble.box.bottom - hitBubble.box.top) * dispH

                                onBubbleHover(magnifiedBubble.pageIndex, hitId, Offset(centerX, centerY), Size(bw, bh))
                            }
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(edgeTapNavigationEnabled, tapRegions) {
                detectTapGestures(onTap = { tap ->
                    // Same zones as the page underneath: the edges step bubbles, the centre (or any tap when
                    // edge navigation is off) dismisses the magnifier and leaves bubble navigation mode.
                    if (edgeTapNavigationEnabled) {
                        when (classifyTap(tap.x, tap.y, size.width.toFloat(), size.height.toFloat(), tapRegions)) {
                            TapZone.NEXT -> latestOnEdgeTap(true)
                            TapZone.PREVIOUS -> latestOnEdgeTap(false)
                            TapZone.CENTER -> latestOnDismiss()
                        }
                    } else {
                        latestOnDismiss()
                    }
                })
            }
            .then(hoverModifier)
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
