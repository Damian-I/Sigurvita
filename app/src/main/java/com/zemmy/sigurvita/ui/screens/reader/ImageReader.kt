package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import coil.request.ImageRequest
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReadingMode

@Composable
fun ImageReader(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    readingMode: ReadingMode,
    fitMode: FitMode = FitMode.FIT_WIDTH,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) return

    when (readingMode) {
        ReadingMode.WEBTOON -> {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)

            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                    onPageChanged(index)
                }
            }

            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .clickable { onTap() }
            ) {
                itemsIndexed(pages) { _, page ->
                    ZoomableImage(
                        page = page,
                        fitMode = fitMode,
                        onTap = onTap,
                        modifier = Modifier.fillMaxSize()
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
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val contentScale = when (fitMode) {
        FitMode.FIT_WIDTH -> ContentScale.FillWidth
        FitMode.FIT_HEIGHT -> ContentScale.FillHeight
        FitMode.FIT_SCREEN -> ContentScale.Fit
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
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
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(page.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.page_desc, page.pageNumber),
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
