package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onSizeChanged
import coil.size.Scale
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.data.repository.extractBubbleNormalizedPoints
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.MagnifiedBubble
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReaderBackground
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.core.util.PanelMap

@Composable
fun ImageReader(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    readingMode: ReadingMode,
    onPageChanged: (Int) -> Unit,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    fitMode: FitMode = FitMode.FIT_WIDTH,
    touchEdgeNavigationEnabled: Boolean = true,
    tapRegions: List<TapRegion> = defaultTapRegions,
    bubblesByPage: Map<Int, PageBubbles> = emptyMap(),
    panelMapsByPage: Map<Int, PanelMap> = emptyMap(),
    speechBubbleDetectionEnabled: Boolean = true,
    speechBubbleBoxesEnabled: Boolean = true,
    panelDebugOverlayEnabled: Boolean = false,
    onBubbleLongPress: (pageIndex: Int, bubbleId: Int, centerPx: Offset, bubbleSizePx: Size) -> Unit = { _, _, _, _ -> },
    onBubbleHover: (pageIndex: Int, bubbleId: Int?, centerPx: Offset?, bubbleSizePx: Size?) -> Unit = { _, _, _, _ -> },
    magnifiedBubble: MagnifiedBubble? = null,
    readerBackground: ReaderBackground = ReaderBackground.BLACK,
    onPageBackground: (pageIndex: Int, color: Color) -> Unit = { _, _ -> },
    viewport: PageViewportController = remember { PageViewportController() }
) {
    if (pages.isEmpty()) return

    val context = LocalContext.current

    val viewRequest = viewport.request
    LaunchedEffect(viewRequest?.id) {
        viewRequest?.let { viewport.run(it) }
    }

    // Prefetch adjacent pages into Coil memory cache
    LaunchedEffect(currentPageIndex) {
        val imageLoader = context.imageLoader
        for (offset in 1..3) {
            val prefetchIdx = currentPageIndex + offset
            if (prefetchIdx in pages.indices) {
                val prefetchRequest = ImageRequest.Builder(context)
                    .data(pages[prefetchIdx].imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(prefetchRequest)
            }
        }
    }

    when (readingMode) {
        ReadingMode.WEBTOON -> {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)

            LaunchedEffect(currentPageIndex) {
                if (listState.firstVisibleItemIndex != currentPageIndex) {
                    listState.scrollToItem(currentPageIndex)
                }
            }

            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                    onPageChanged(index)
                }
            }

            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize()
            ) {
                itemsIndexed(pages, key = { _, page -> page.pageNumber }) { idx, page ->
                    ZoomableImage(
                        page = page,
                        fitMode = fitMode,
                        touchEdgeNavigationEnabled = touchEdgeNavigationEnabled,
                        tapRegions = tapRegions,
                        readingMode = readingMode,
                        bubbles = bubblesByPage[idx],
                        panelMap = panelMapsByPage[idx],
                        speechBubbleDetectionEnabled = speechBubbleDetectionEnabled,
                        speechBubbleBoxesEnabled = speechBubbleBoxesEnabled,
                        panelDebugOverlayEnabled = panelDebugOverlayEnabled,
                        onBubbleLongPress = { bubbleId, centerPx, bubbleSizePx -> onBubbleLongPress(idx, bubbleId, centerPx, bubbleSizePx) },
                        onBubbleHover = { bubbleId, centerPx, bubbleSizePx -> onBubbleHover(idx, bubbleId, centerPx, bubbleSizePx) },
                        magnifiedBubbleId = if (magnifiedBubble?.pageIndex == idx) magnifiedBubble.bubbleId else null,
                        onNextPage = onNextPage,
                        onPreviousPage = onPreviousPage,
                        onTap = onTap,
                        readerBackground = readerBackground,
                        onPageBackground = onPageBackground,
                        viewport = viewport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }
        ReadingMode.RTL, ReadingMode.LTR -> {
            val pagerState = rememberPagerState(
                initialPage = currentPageIndex,
                pageCount = { pages.size }
            )

            LaunchedEffect(currentPageIndex) {
                if (pagerState.currentPage != currentPageIndex) {
                    pagerState.scrollToPage(currentPageIndex)
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    onPageChanged(page)
                }
            }

            HorizontalPager(
                state = pagerState,
                reverseLayout = (readingMode == ReadingMode.RTL),
                modifier = modifier.fillMaxSize()
            ) { pageIdx ->
                ZoomableImage(
                    page = pages[pageIdx],
                    fitMode = fitMode,
                    touchEdgeNavigationEnabled = touchEdgeNavigationEnabled,
                    tapRegions = tapRegions,
                    readingMode = readingMode,
                    bubbles = bubblesByPage[pageIdx],
                    panelMap = panelMapsByPage[pageIdx],
                    speechBubbleDetectionEnabled = speechBubbleDetectionEnabled,
                    speechBubbleBoxesEnabled = speechBubbleBoxesEnabled,
                    panelDebugOverlayEnabled = panelDebugOverlayEnabled,
                    onBubbleLongPress = { bubbleId, centerPx, bubbleSizePx -> onBubbleLongPress(pageIdx, bubbleId, centerPx, bubbleSizePx) },
                    onBubbleHover = { bubbleId, centerPx, bubbleSizePx -> onBubbleHover(pageIdx, bubbleId, centerPx, bubbleSizePx) },
                    magnifiedBubbleId = if (magnifiedBubble?.pageIndex == pageIdx) magnifiedBubble.bubbleId else null,
                    onNextPage = onNextPage,
                    onPreviousPage = onPreviousPage,
                    onTap = onTap,
                    readerBackground = readerBackground,
                    onPageBackground = onPageBackground,
                    viewport = viewport,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    page: ReaderPage,
    fitMode: FitMode,
    touchEdgeNavigationEnabled: Boolean,
    tapRegions: List<TapRegion>,
    readingMode: ReadingMode,
    viewport: PageViewportController,
    bubbles: PageBubbles? = null,
    panelMap: PanelMap? = null,
    speechBubbleDetectionEnabled: Boolean = true,
    speechBubbleBoxesEnabled: Boolean = true,
    panelDebugOverlayEnabled: Boolean = false,
    onBubbleLongPress: (bubbleId: Int, centerPx: Offset, bubbleSizePx: Size) -> Unit = { _, _, _ -> },
    onBubbleHover: (bubbleId: Int?, centerPx: Offset?, bubbleSizePx: Size?) -> Unit = { _, _, _ -> },
    magnifiedBubbleId: Int? = null,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onTap: () -> Unit,
    readerBackground: ReaderBackground = ReaderBackground.BLACK,
    onPageBackground: (pageIndex: Int, color: Color) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val latestOnBubbleHover by rememberUpdatedState(onBubbleHover)
    val latestOnBubbleLongPress by rememberUpdatedState(onBubbleLongPress)
    val latestOnNextPage by rememberUpdatedState(onNextPage)
    val latestOnPreviousPage by rememberUpdatedState(onPreviousPage)
    val latestOnTap by rememberUpdatedState(onTap)

    val transform = viewport.transformFor(page.index)
    DisposableEffect(page.index) {
        onDispose { viewport.release(page.index) }
    }
    // Large pages are laid out whole (Fit) so panel zoom / scrolling can reach every part of the page.
    val largePage = readingMode != ReadingMode.WEBTOON && viewport.isLarge(page.index)

    val contentScale = when {
        readingMode == ReadingMode.WEBTOON -> ContentScale.FillWidth
        largePage -> ContentScale.Fit
        fitMode == FitMode.FIT_WIDTH -> ContentScale.FillWidth
        fitMode == FitMode.FIT_HEIGHT -> ContentScale.FillHeight
        else -> ContentScale.Fit
    }

    val boxModifier = if (readingMode == ReadingMode.WEBTOON) {
        modifier.fillMaxWidth().wrapContentHeight()
    } else {
        modifier.fillMaxSize()
    }

    val transformModifier = Modifier.pointerInput(page.pageNumber, readingMode) {
        if (readingMode == ReadingMode.WEBTOON) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val multiTouch = event.changes.count { it.pressed } >= 2
                    if (multiTouch || transform.scale > 1.01f) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || pan != Offset.Zero) {
                            transform.applyGesture(zoom, pan)
                            viewport.onManualTransform(page.index)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        } else {
            detectTransformGestures { _, pan, zoom, _ ->
                transform.applyGesture(zoom, pan)
                viewport.onManualTransform(page.index)
            }
        }
    }

    var lastHoveredBubbleId by remember(page.pageNumber) { mutableStateOf<Int?>(null) }

    // S-Pen Hover Magnification Modifier:
    // Magnifies when hovered over a speech bubble. Returns to normal ONLY when PRESSING on screen with the S-Pen, NOT when unhovering.
    val stylusHoverMagnifyModifier = Modifier.pointerInput(page.pageNumber, speechBubbleDetectionEnabled, bubbles, magnifiedBubbleId, contentScale) {
        awaitPointerEventScope {
            while (true) {
                val pointerEvent = awaitPointerEvent()
                val pointerChange = pointerEvent.changes.firstOrNull()
                // Only process Stylus or Mouse events. Ignore Touch entirely so finger taps/long-presses work!
                if (pointerChange != null && (pointerChange.type == PointerType.Stylus || pointerChange.type == PointerType.Mouse)) {
                    if (!pointerChange.pressed && pointerEvent.type != PointerEventType.Exit) {
                        val currentHoverOffset = pointerChange.position
                        // Magnifies when hovered over a speech bubble.
                        // If no bubble is magnified yet, or if hovering over a NEW bubble while one is already magnified, switch to and magnify the new bubble.
                        if (speechBubbleDetectionEnabled && bubbles != null && bubbles.bubbles.isNotEmpty()) {
                            val normPoint = viewportToPageNorm(
                                tap = currentHoverOffset,
                                box = size,
                                pageW = bubbles.pageWidth,
                                pageH = bubbles.pageHeight,
                                contentScale = contentScale,
                                zoom = transform.scale,
                                pan = Offset(transform.offsetX, transform.offsetY)
                            )
                            val hitBubbleId = if (normPoint != null) hitBubble(bubbles, normPoint) else null
                            if (hitBubbleId != null && hitBubbleId != magnifiedBubbleId && hitBubbleId != lastHoveredBubbleId) {
                                lastHoveredBubbleId = hitBubbleId
                                triggerMagnify(hitBubbleId, bubbles, contentScale, size, latestOnBubbleHover)                            }
                        }
                    } else {
                        // Returns to normal ONLY when PRESSING on screen with the S-Pen (pointerChange.pressed == true), NOT when unhovering
                        if (magnifiedBubbleId != null && pointerChange.pressed) {
                            lastHoveredBubbleId = null
                            latestOnBubbleHover(null, null, null)
                        }
                    }
                }
                // Unhovering or exiting does NOT return to normal; magnification persists until pen-down screen press.
            }
        }
    }
    var pageLoaded by remember(page.pageNumber) { mutableStateOf(false) }
    var autoBackground by remember(page.pageNumber) { mutableStateOf<Color?>(null) }
    val latestOnPageBackground by rememberUpdatedState(onPageBackground)
    LaunchedEffect(page.pageNumber, readerBackground, pageLoaded) {
        if (readerBackground == ReaderBackground.AUTO && pageLoaded && autoBackground == null) {
            val color = estimatePageEdgeColor(context, page.imageUrl)
            if (color != null) {
                autoBackground = color
                latestOnPageBackground(page.index, color)
            }
        }
    }
    val pageBackground = readerBackground.fixedColor() ?: autoBackground ?: Color.Black
    Box(
        modifier = boxModifier
            .background(pageBackground)
            .onSizeChanged {
                if (readingMode != ReadingMode.WEBTOON) viewport.reportBoxSize(it.width, it.height)
            }
            .then(transformModifier)
            .then(stylusHoverMagnifyModifier)
            .pointerInput(touchEdgeNavigationEnabled, readingMode, tapRegions, largePage, contentScale, bubbles) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        if (readingMode != ReadingMode.WEBTOON && touchEdgeNavigationEnabled && (transform.scale <= 1.05f || largePage) && width > 0f) {
                            // The tap zones navigate (pages, or bubbles while bubble navigation mode is on);
                            // a tap outside every zone toggles the controls and leaves bubble navigation mode.
                            when (classifyTap(offset.x, offset.y, width, size.height.toFloat(), tapRegions)) {
                                TapZone.NEXT -> latestOnNextPage()
                                TapZone.PREVIOUS -> latestOnPreviousPage()
                                TapZone.CENTER -> latestOnTap()
                            }
                        } else {
                            latestOnTap()
                        }
                    },
                    onLongPress = { offset ->
                        if (speechBubbleDetectionEnabled && bubbles != null && bubbles.bubbles.isNotEmpty()) {
                            val norm = viewportToPageNorm(
                                tap = offset,
                                box = size,
                                pageW = bubbles.pageWidth,
                                pageH = bubbles.pageHeight,
                                contentScale = contentScale,
                                zoom = transform.scale,
                                pan = Offset(transform.offsetX, transform.offsetY)
                            )
                            if (norm != null) {
                                val hitId = hitBubble(bubbles, norm)
                                if (hitId != null) {
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

                                        latestOnBubbleLongPress(hitId, Offset(centerX, centerY), Size(bw, bh))
                                    }
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val imageModifier = if (readingMode == ReadingMode.WEBTOON) {
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offsetX
                    translationY = transform.offsetY
                }
        } else {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offsetX
                    translationY = transform.offsetY
                }
        }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(page.imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .apply {
                    // Zoomed panels need more pixels than the screen shows at 1x.
                    if (largePage && viewport.boxWidth > 0f) {
                        size((viewport.boxWidth * 3f).toInt(), (viewport.boxHeight * 3f).toInt())
                        scale(Scale.FIT)
                    }
                }
                .build(),
            contentDescription = stringResource(R.string.page_desc, page.pageNumber),
            contentScale = contentScale,
            onSuccess = { success ->
                pageLoaded = true
                val d = success.result.drawable
                viewport.reportPageSize(page.index, d.intrinsicWidth, d.intrinsicHeight)
            },
            modifier = imageModifier
        )

        if ((speechBubbleBoxesEnabled && bubbles != null && bubbles.bubbles.isNotEmpty()) || (panelDebugOverlayEnabled && (panelMap != null || bubbles != null))) {
            Canvas(modifier = imageModifier) {
                val boxW = size.width
                val boxH = size.height
                val pageW = (bubbles?.pageWidth ?: panelMap?.letterbox?.originalWidth ?: 2000).toFloat()
                val pageH = (bubbles?.pageHeight ?: panelMap?.letterbox?.originalHeight ?: 3000).toFloat()

                if (boxW > 0f && boxH > 0f && pageW > 0f && pageH > 0f) {
                    val s = when (contentScale) {
                        ContentScale.FillWidth -> boxW / pageW
                        ContentScale.FillHeight -> boxH / pageH
                        else -> minOf(boxW / pageW, boxH / pageH)
                    }
                    val dispW = pageW * s
                    val dispH = pageH * s
                    val originX = (boxW - dispW) / 2f
                    val originY = (boxH - dispH) / 2f

                    if (speechBubbleBoxesEnabled && bubbles != null && bubbles.bubbles.isNotEmpty()) {
                        val vectorPaths = buildBubbleVectorPaths(bubbles, dispW, dispH, originX, originY)
                        vectorPaths.forEach { path ->
                            drawPath(path = path, color = Color(0x38FFE500), style = Fill)
                            drawPath(path = path, color = Color.Black, style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path = path, color = Color(0xFFFFE500), style = Stroke(width = 0.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }

                    if (panelDebugOverlayEnabled) {
                        panelMap?.panels?.forEach { panel ->
                            val pLeft = originX + panel.box.left * dispW
                            val pTop = originY + panel.box.top * dispH
                            val pW = (panel.box.right - panel.box.left) * dispW
                            val pH = (panel.box.bottom - panel.box.top) * dispH

                            drawRect(
                                color = Color(0xFF00E5FF),
                                topLeft = Offset(pLeft, pTop),
                                size = Size(pW, pH),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        if (bubbles != null && bubbles.bubbles.isNotEmpty()) {
                            val radius = 12.dp.toPx()
                            val bgPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(220, 20, 20, 20)
                                style = android.graphics.Paint.Style.FILL
                                isAntiAlias = true
                            }
                            val borderPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.rgb(0, 229, 255)
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 1.5f.dp.toPx()
                                isAntiAlias = true
                            }
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 12.dp.toPx()
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            val textYOffset = (textPaint.descent() + textPaint.ascent()) / 2f

                            bubbles.bubbles.forEachIndexed { index, bubble ->
                                val cx = originX + ((bubble.box.left + bubble.box.right) / 2f) * dispW
                                val cy = originY + ((bubble.box.top + bubble.box.bottom) / 2f) * dispH

                                drawContext.canvas.nativeCanvas.apply {
                                    drawCircle(cx, cy, radius, bgPaint)
                                    drawCircle(cx, cy, radius, borderPaint)
                                    drawText("${index + 1}", cx, cy - textYOffset, textPaint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildBubbleVectorPaths(
    bubbles: PageBubbles,
    dispW: Float,
    dispH: Float,
    originX: Float,
    originY: Float
): List<Path> {
    if (bubbles.bubbles.isEmpty()) return emptyList()

    val paths = mutableListOf<Path>()
    val lb = bubbles.letterbox

    for (bubble in bubbles.bubbles) {
        val normPoints = extractBubbleNormalizedPoints(bubble, lb)
        if (normPoints.size < 3) continue

        val screenPoints = normPoints.map { (normX, normY) ->
            Offset(originX + normX * dispW, originY + normY * dispH)
        }

        val path = Path()
        path.moveTo(screenPoints[0].x, screenPoints[0].y)

        if (screenPoints.size == 4) {
            path.lineTo(screenPoints[1].x, screenPoints[1].y)
            path.lineTo(screenPoints[2].x, screenPoints[2].y)
            path.lineTo(screenPoints[3].x, screenPoints[3].y)
        } else {
            for (i in screenPoints.indices) {
                val p0 = screenPoints[i]
                val p1 = screenPoints[(i + 1) % screenPoints.size]
                val midX = (p0.x + p1.x) / 2f
                val midY = (p0.y + p1.y) / 2f
                path.quadraticTo(p0.x, p0.y, midX, midY)
            }
        }
        path.close()
        paths.add(path)
    }

    return paths
}

private fun triggerMagnify(
    hitId: Int,
    bubbles: PageBubbles,
    contentScale: ContentScale,
    boxSize: IntSize,
    onBubbleHover: (Int?, Offset?, Size?) -> Unit
) {
    val hitBubble = bubbles.bubbles.find { it.id == hitId } ?: return
    val boxW = boxSize.width.toFloat()
    val boxH = boxSize.height.toFloat()
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

    onBubbleHover(hitId, Offset(centerX, centerY), Size(bw, bh))
}
