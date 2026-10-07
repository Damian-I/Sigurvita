package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.R
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zemmy.sigurvita.core.data.repository.extractBubbleNormalizedPoints
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReadingMode

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
    bubblesByPage: Map<Int, PageBubbles> = emptyMap(),
    speechBubblesEnabled: Boolean = true,
    onBubbleLongPress: (pageIndex: Int, bubbleId: Int, centerPx: Offset, bubbleSizePx: Size) -> Unit = { _, _, _, _ -> }
) {
    if (pages.isEmpty()) return

    val context = LocalContext.current

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
                        readingMode = readingMode,
                        bubbles = bubblesByPage[idx],
                        speechBubblesEnabled = speechBubblesEnabled,
                        onBubbleLongPress = { bubbleId, centerPx, bubbleSizePx -> onBubbleLongPress(idx, bubbleId, centerPx, bubbleSizePx) },
                        onNextPage = onNextPage,
                        onPreviousPage = onPreviousPage,
                        onTap = onTap,
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
                    pagerState.animateScrollToPage(currentPageIndex)
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
                    readingMode = readingMode,
                    bubbles = bubblesByPage[pageIdx],
                    speechBubblesEnabled = speechBubblesEnabled,
                    onBubbleLongPress = { bubbleId, centerPx, bubbleSizePx -> onBubbleLongPress(pageIdx, bubbleId, centerPx, bubbleSizePx) },
                    onNextPage = onNextPage,
                    onPreviousPage = onPreviousPage,
                    onTap = onTap,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    page: ReaderPage,
    fitMode: FitMode,
    touchEdgeNavigationEnabled: Boolean,
    readingMode: ReadingMode,
    bubbles: PageBubbles? = null,
    speechBubblesEnabled: Boolean = true,
    onBubbleLongPress: (bubbleId: Int, centerPx: Offset, bubbleSizePx: Size) -> Unit = { _, _, _ -> },
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember(page.pageNumber) { mutableFloatStateOf(1f) }
    var offsetX by remember(page.pageNumber) { mutableFloatStateOf(0f) }
    var offsetY by remember(page.pageNumber) { mutableFloatStateOf(0f) }

    val contentScale = when {
        readingMode == ReadingMode.WEBTOON -> ContentScale.FillWidth
        fitMode == FitMode.FIT_WIDTH -> ContentScale.FillWidth
        fitMode == FitMode.FIT_HEIGHT -> ContentScale.FillHeight
        else -> ContentScale.Fit
    }

    val boxModifier = if (readingMode == ReadingMode.WEBTOON) {
        modifier.fillMaxWidth().wrapContentHeight()
    } else {
        modifier.fillMaxSize()
    }

    val transformModifier = if (readingMode == ReadingMode.WEBTOON && scale <= 1.01f) {
        Modifier
    } else {
        Modifier.pointerInput(page.pageNumber) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 4f)
                if (scale > 1f) {
                    offsetX += pan.x
                    offsetY += pan.y
                } else {
                    offsetX = 0f
                    offsetY = 0f
                }
            }
        }
    }

    Box(
        modifier = boxModifier
            .then(transformModifier)
            .pointerInput(touchEdgeNavigationEnabled, readingMode, scale) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        if (readingMode != ReadingMode.WEBTOON && touchEdgeNavigationEnabled && scale <= 1.05f && width > 0f) {
                            val leftThreshold = width * 0.3f
                            val rightThreshold = width * 0.7f
                            when {
                                offset.x < leftThreshold -> {
                                    if (readingMode == ReadingMode.RTL) {
                                        onNextPage()
                                    } else {
                                        onPreviousPage()
                                    }
                                }
                                offset.x > rightThreshold -> {
                                    if (readingMode == ReadingMode.RTL) {
                                        onPreviousPage()
                                    } else {
                                        onNextPage()
                                    }
                                }
                                else -> {
                                    onTap()
                                }
                            }
                        } else {
                            onTap()
                        }
                    },
                    onLongPress = { offset ->
                        if (speechBubblesEnabled && bubbles != null && bubbles.bubbles.isNotEmpty()) {
                            val norm = viewportToPageNorm(
                                tap = offset,
                                box = size,
                                pageW = bubbles.pageWidth,
                                pageH = bubbles.pageHeight,
                                contentScale = contentScale,
                                zoom = scale,
                                pan = Offset(offsetX, offsetY)
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

                                        onBubbleLongPress(hitId, Offset(centerX, centerY), Size(bw, bh))
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
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        } else {
            Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(page.imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = stringResource(R.string.page_desc, page.pageNumber),
            contentScale = contentScale,
            modifier = imageModifier
        )

        if (speechBubblesEnabled && bubbles != null && bubbles.bubbles.isNotEmpty()) {
            Canvas(modifier = imageModifier) {
                val boxW = size.width
                val boxH = size.height
                val pageW = bubbles.pageWidth.toFloat()
                val pageH = bubbles.pageHeight.toFloat()

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

                    val vectorPaths = buildBubbleVectorPaths(bubbles, dispW, dispH, originX, originY)

                    vectorPaths.forEach { path ->
                        // 1. Smooth vector translucent yellow fill
                        drawPath(
                            path = path,
                            color = Color(0x38FFE500),
                            style = Fill
                        )
                        // 2. Smooth vector black outer stroke
                        drawPath(
                            path = path,
                            color = Color.Black,
                            style = Stroke(
                                width = 1.0.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        // 3. Smooth vector neon yellow inner stroke
                        drawPath(
                            path = path,
                            color = Color(0xFFFFE500),
                            style = Stroke(
                                width = 0.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
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
