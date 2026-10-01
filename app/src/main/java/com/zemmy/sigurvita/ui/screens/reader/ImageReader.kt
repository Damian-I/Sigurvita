package com.zemmy.sigurvita.ui.screens.reader

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zemmy.sigurvita.R
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zemmy.sigurvita.core.model.FitMode
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
    touchEdgeNavigationEnabled: Boolean = true
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
                itemsIndexed(pages, key = { _, page -> page.pageNumber }) { _, page ->
                    ZoomableImage(
                        page = page,
                        fitMode = fitMode,
                        touchEdgeNavigationEnabled = touchEdgeNavigationEnabled,
                        readingMode = readingMode,
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
    }
}
