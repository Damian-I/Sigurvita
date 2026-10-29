package com.zemmy.sigurvita.ui.screens.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderOrientation
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.ui.components.IssueDetailBottomSheet
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.components.settings.ReaderSettingsActions
import com.zemmy.sigurvita.ui.components.settings.ReaderSettingsContent
import com.zemmy.sigurvita.ui.theme.readableOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapterId: Int,
    totalPages: Int,
    seriesId: Int = 0,
    volumeId: Int = 0,
    readingListId: Int = 0,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val viewModel: ReaderViewModel = if (activity != null) viewModel(activity) else viewModel()
    val scope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsState()
    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showIssueDetailSheet by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        scope.launch {
            viewModel.flushProgressSync()
            onBackClick()
        }
    }

    BackHandler {
        handleBack()
    }

    val displayMetrics = context.resources.displayMetrics
    LaunchedEffect(displayMetrics.widthPixels, displayMetrics.heightPixels) {
        viewModel.updateScreenSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    LaunchedEffect(chapterId) {
        viewModel.loadChapter(chapterId, totalPages, seriesId, volumeId, readingListId)
    }

    DisposableEffect(state.settings.keepScreenOn) {
        if (state.settings.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(state.settings.orientation) {
        activity?.requestedOrientation = state.settings.orientation.toRequestedOrientation()
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushProgress()
            viewModel.resetBubbleNavMode()
            viewModel.onReaderClosed()
        }
    }

    val pageBackgrounds = remember(state.chapterId) { mutableStateMapOf<Int, Color>() }
    val rootBackground = state.settings.readerBackground.fixedColor()
        ?: pageBackgrounds[state.currentPageIndex]
        ?: Color.Black
    val scapeAccent = com.zemmy.sigurvita.ui.theme.rememberColorScapeAccent(
        com.zemmy.sigurvita.core.data.repository.ColorScapeKind.SERIES,
        seriesId
    ) ?: com.zemmy.sigurvita.ui.theme.rememberColorScapeAccent(
        com.zemmy.sigurvita.core.data.repository.ColorScapeKind.CHAPTER,
        chapterId
    )
    val chromeSurface = scapeAccent?.tint ?: Color.Black
    val chromeAccent = (scapeAccent?.source?.primary ?: MaterialTheme.colorScheme.primary)
        .readableOn(chromeSurface)
    val isRtlMode = state.settings.readingMode == ReadingMode.RTL
    val tapRegions = remember(
        state.settings.tapZoneLayout,
        state.settings.customTapZones,
        state.settings.customZonesMirrorInRtl,
        isRtlMode
    ) {
        tapRegionsFor(
            state.settings.tapZoneLayout,
            state.settings.customTapZones,
            state.settings.customZonesMirrorInRtl,
            isRtlMode
        )
    }
    val zonesActive = state.settings.touchEdgeNavigationEnabled &&
            state.settings.readingMode != ReadingMode.WEBTOON
    var zoneFlash by remember { mutableStateOf(false) }
    // Flashes once the chapter has loaded, and again when the layout, zones, direction or the toggle change.
    // It waits for the load (the ViewModel is activity-scoped, so stale state exists on reopen) and always resets.
    LaunchedEffect(tapRegions, zonesActive, state.settings.showTapZones) {
        if (zonesActive && state.settings.showTapZones) {
            try {
                snapshotFlow { !state.isLoading && state.chapterId == chapterId }.first { it }
                zoneFlash = true
                delay(3000)
            } finally {
                zoneFlash = false
            }
        }
    }
    SideEffect {
        viewModel.viewport.sync(
            aspectThreshold = state.settings.largePageAspectThreshold,
            paged = state.settings.readingMode != ReadingMode.WEBTOON
        )
    }
    // Toggling bubble navigation returns large pages to their default mode (guided / scroll).
    LaunchedEffect(state.bubbleNavActive) {
        viewModel.viewport.clearModeOverride()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBackground)
    ) {
        if (state.showResumePrompt) {
            val isFinished = state.promptPageNum >= state.pages.size
            val promptText = if (isFinished) {
                "You previously finished this issue (Page ${state.promptPageNum} of ${state.pages.size}). Would you like to start from the beginning or resume from the last page?"
            } else {
                "You previously read up to Page ${state.promptPageNum} of ${state.pages.size}. Would you like to start from the beginning or resume from Page ${state.promptPageNum}?"
            }
            val confirmBtnText = if (isFinished) "Resume Last Page" else "Resume Page ${state.promptPageNum}"

            AlertDialog(
                onDismissRequest = { viewModel.dismissResumePrompt(startFromBeginning = true) },
                title = { Text("Resume Position") },
                text = { Text(promptText) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissResumePrompt(startFromBeginning = false) }) {
                        Text(confirmBtnText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissResumePrompt(startFromBeginning = true) }) {
                        Text("Start from Beginning")
                    }
                }
            )
        }

        if (state.isLoading) {
            LoadingState()
        } else {
            ImageReader(
                pages = state.pages,
                currentPageIndex = state.currentPageIndex,
                readingMode = state.settings.readingMode,
                onPageChanged = { viewModel.onPageChanged(it) },
                // Edge taps: step bubbles while bubble navigation mode is on, otherwise turn the page.
                onNextPage = { viewModel.onEdgeTapNavigate(isNext = true) },
                onPreviousPage = { viewModel.onEdgeTapNavigate(isNext = false) },
                onTap = {
                    viewModel.resetBubbleNavMode()
                    viewModel.toggleControls()
                },
                fitMode = state.settings.fitMode,
                touchEdgeNavigationEnabled = state.settings.touchEdgeNavigationEnabled,
                tapRegions = tapRegions,
                bubblesByPage = state.bubblesByPage,
                panelMapsByPage = state.panelMapsByPage,
                speechBubbleDetectionEnabled = state.settings.speechBubbleDetectionEnabled,
                speechBubbleBoxesEnabled = state.settings.speechBubbleBoxesEnabled,
                panelDebugOverlayEnabled = state.settings.panelDebugOverlayEnabled,
                onBubbleLongPress = { pageIdx, bubbleId, centerPx, bubbleSizePx -> viewModel.onBubbleLongPress(pageIdx, bubbleId, centerPx, bubbleSizePx) },
                onBubbleHover = { pageIdx, bubbleId, centerPx, bubbleSizePx -> viewModel.onBubbleHover(pageIdx, bubbleId, centerPx, bubbleSizePx) },
                magnifiedBubble = state.magnifiedBubble,
                readerBackground = state.settings.readerBackground,
                onPageBackground = { index, color -> pageBackgrounds[index] = color },
                viewport = viewModel.viewport
            )
            TapZoneOverlay(
                regions = tapRegions,
                visible = zoneFlash && zonesActive && state.settings.showTapZones,
                modifier = Modifier.fillMaxSize()
            )
            state.magnifiedBubble?.let { magBubble ->
                BubbleMagnifierOverlay(
                    magnifiedBubble = magBubble,
                    speechBubbleBoxesEnabled = state.settings.speechBubbleBoxesEnabled,
                    onDismiss = { viewModel.dismissBubble() },
                    bubbles = state.bubblesByPage[magBubble.pageIndex],
                    contentScale = when {
                        state.settings.readingMode == ReadingMode.WEBTOON -> ContentScale.FillWidth
                        viewModel.viewport.isLarge(magBubble.pageIndex) -> ContentScale.Fit
                        state.settings.fitMode == FitMode.FIT_WIDTH -> ContentScale.FillWidth
                        state.settings.fitMode == FitMode.FIT_HEIGHT -> ContentScale.FillHeight
                        else -> ContentScale.Fit
                    },
                    transform = viewModel.viewport.transformFor(magBubble.pageIndex),
                    onBubbleHover = { pageIdx, bubbleId, centerPx, bubbleSizePx -> viewModel.onBubbleHover(pageIdx, bubbleId, centerPx, bubbleSizePx) },
                    edgeTapNavigationEnabled = state.settings.touchEdgeNavigationEnabled &&
                        state.settings.readingMode != ReadingMode.WEBTOON,
                    tapRegions = tapRegions,
                    onEdgeTap = { isNext -> viewModel.onEdgeTapNavigate(isNext) }
                )
            }

            if (state.settings.showPageNumber && !state.controlsVisible && state.pages.isNotEmpty()) {
                Text(
                    text = "${state.currentPageIndex + 1} / ${state.pages.size}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 4.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // Large-page chip: shows the current mode, tap to switch Panels <-> Scroll (hidden with the controls)
            val onLargePage = state.settings.readingMode != ReadingMode.WEBTOON &&
                    viewModel.viewport.isLarge(state.currentPageIndex)
            AnimatedVisibility(
                visible = onLargePage && !state.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(12.dp)
            ) {
                val guided = viewModel.viewport.effectiveMode(state.bubbleNavActive) == LargePageMode.GUIDED
                Surface(
                    color = chromeSurface.copy(alpha = 0.85f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.toggleLargePageMode() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (guided) Icons.Default.ZoomIn else Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = chromeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(
                                if (guided) R.string.large_page_mode_panels else R.string.large_page_mode_scroll
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Top Control Overlay
            AnimatedVisibility(
                visible = state.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.cancelAutoHideControls()
                                    showIssueDetailSheet = true
                                },
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.height(22.dp)
                            ) {
                                val headerTitle = state.seriesName.ifBlank { state.chapterTitle.ifBlank { "Issue Details" } }
                                Text(
                                    text = headerTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Issue Details",
                                    tint = chromeAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(18.dp)
                            ) {
                                if (state.seriesName.isNotBlank() && state.chapterTitle.isNotBlank()) {
                                    Text(
                                        text = state.chapterTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.page_indicator, state.currentPageIndex + 1, state.pages.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                                if (state.settings.isIncognitoMode) {
                                    Surface(
                                        color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.incognito,
                                        contentColor = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onIncognito,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VisibilityOff,
                                                contentDescription = "Incognito",
                                                modifier = Modifier.size(12.dp),
                                                tint = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onIncognito
                                            )
                                            Text(
                                                text = "Incognito",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.onIncognito
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        val isCurrentPageMarked = state.currentPageIndex in state.markedPageIndices
                        IconButton(onClick = {
                            viewModel.scheduleAutoHideControls()
                            viewModel.toggleMarkCurrentPage()
                        }) {
                            Icon(
                                imageVector = if (isCurrentPageMarked) Icons.Default.Bookmark else Icons.Outlined.Bookmark,
                                contentDescription = if (isCurrentPageMarked) "Remove Page Mark" else "Mark Page for Review",
                                tint = if (isCurrentPageMarked) chromeAccent else Color.White
                            )
                        }
                        IconButton(onClick = {
                            viewModel.cancelAutoHideControls()
                            showOptionsBottomSheet = true
                        }) {
                            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.reader_options_cd), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = chromeSurface.copy(alpha = 0.88f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }

            // Bottom Control Overlay
            AnimatedVisibility(
                visible = state.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val isRtl = state.settings.readingMode == ReadingMode.RTL
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        chromeSurface.copy(alpha = 0.90f),
                                        chromeSurface.copy(alpha = 0.97f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(bottom = 8.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            val currentBubbles = state.bubblesByPage[state.currentPageIndex]
                            if (state.settings.speechBubbleDetectionEnabled && currentBubbles != null) {
                                Surface(
                                    color = chromeSurface.copy(alpha = 0.9f),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubble,
                                            contentDescription = null,
                                            tint = chromeAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${currentBubbles.bubbles.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Multi-Thumbnail Timeline Preview Strip
                        if (state.pages.isNotEmpty()) {
                            val layoutDirection = LocalLayoutDirection.current
                            val density = LocalDensity.current
                            val stepPx = remember(density) { with(density) { 32.dp.toPx() } }
                            var dragStartIdx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                            var accumulatedDragPx by remember { mutableFloatStateOf(0f) }

                            val draggableState = rememberDraggableState { delta ->
                                accumulatedDragPx += delta
                                val directionMultiplier = if (layoutDirection == LayoutDirection.Rtl) 1f else -1f
                                val pageOffset = (accumulatedDragPx * directionMultiplier / stepPx).toInt()
                                val targetIdx = (dragStartIdx + pageOffset).coerceIn(0, state.pages.size - 1)
                                if (targetIdx != state.currentPageIndex) {
                                    viewModel.resetBubbleNavMode()
                                    viewModel.onPageChanged(targetIdx)
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .draggable(
                                        state = draggableState,
                                        orientation = Orientation.Horizontal,
                                        onDragStarted = {
                                            viewModel.cancelAutoHideControls()
                                            dragStartIdx = state.currentPageIndex
                                            accumulatedDragPx = 0f
                                        },
                                        onDragStopped = {
                                            viewModel.scheduleAutoHideControls()
                                        }
                                    )
                                    .padding(bottom = 8.dp)
                            ) {
                                for (offset in -2..2) {
                                    val targetIdx = state.currentPageIndex + offset
                                    val page = state.pages.getOrNull(targetIdx)
                                    val absOffset = kotlin.math.abs(offset)

                                    val (itemWidth, itemHeight, alpha) = when (absOffset) {
                                        0 -> Triple(80.dp, 112.dp, 1.0f)
                                        1 -> Triple(60.dp, 84.dp, 0.75f)
                                        else -> Triple(45.dp, 63.dp, 0.45f)
                                    }

                                    if (page != null) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = chromeSurface.copy(alpha = 0.92f),
                                            shadowElevation = if (absOffset == 0) 6.dp else 2.dp,
                                            border = if (absOffset == 0) BorderStroke(1.5.dp, chromeAccent)
                                            else BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                            modifier = Modifier
                                                .graphicsLayer { this.alpha = alpha }
                                                .clickable {
                                                    viewModel.resetBubbleNavMode()
                                                    viewModel.onPageChanged(targetIdx)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(page.imageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Page ${page.pageNumber}",
                                                modifier = Modifier
                                                    .width(itemWidth)
                                                    .height(itemHeight),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(itemWidth))
                                    }
                                }
                            }

                            Text(
                                text = "Page ${state.currentPageIndex + 1} of ${state.pages.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Live Scrolling Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { viewModel.onPreviousChapterClick() },
                                enabled = state.prevChapter != null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous chapter",
                                    tint = if (state.prevChapter != null) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f }
                                )
                            }
                            Text(
                                text = "${state.currentPageIndex + 1}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = state.currentPageIndex.toFloat(),
                                onValueChange = {
                                    viewModel.onPageChanged(it.toInt())
                                },
                                valueRange = 0f..(if (state.pages.isNotEmpty()) (state.pages.size - 1).toFloat() else 0f),
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = chromeAccent,
                                    activeTrackColor = chromeAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${state.pages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = { viewModel.onNextChapterClick() },
                                enabled = state.nextChapter != null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next chapter",
                                    tint = if (state.nextChapter != null) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f }
                                )
                            }
                        }
                    }
                }
            }

            if (showIssueDetailSheet) {
                val chapterForDetail = state.currentChapter ?: Chapter(
                    id = state.chapterId,
                    volumeId = state.volumeId,
                    seriesId = state.seriesId,
                    chapterNumber = "",
                    title = state.chapterTitle,
                    pages = state.pages.size,
                    pagesRead = state.currentPageIndex + 1,
                    isRead = state.currentPageIndex + 1 >= state.pages.size
                )
                IssueDetailBottomSheet(
                    chapter = chapterForDetail,
                    seriesName = state.seriesName.ifBlank { "Series" },
                    seriesCoverUrl = state.seriesCoverUrl,
                    metadata = state.seriesMetadata,
                    onReadClick = { _, _, _ ->
                        showIssueDetailSheet = false
                        viewModel.scheduleAutoHideControls()
                    },
                    onToggleRead = { targetChapter ->
                        viewModel.toggleChapterRead(targetChapter)
                    },
                    onDownloadClick = { targetChapter ->
                        viewModel.downloadChapter(targetChapter)
                    },
                    onDeleteDownloadClick = { chapterId ->
                        viewModel.deleteLocalChapter(chapterId)
                    },
                    onDismissRequest = {
                        showIssueDetailSheet = false
                        viewModel.scheduleAutoHideControls()
                    }
                )
            }

            if (showOptionsBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showOptionsBottomSheet = false
                        viewModel.scheduleAutoHideControls()
                    },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reader_options_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ReaderSettingsContent(
                            settings = state.settings,
                            actions = remember(viewModel) {
                                ReaderSettingsActions(
                                    onFitMode = viewModel::updateFitMode,
                                    onReaderBackground = viewModel::updateReaderBackground,
                                    onOrientation = viewModel::updateOrientation,
                                    onVolumeKeyNavigation = viewModel::updateVolumeKeyNavigation,
                                    onVolumeKeyInvert = viewModel::updateVolumeKeyInvert,
                                    onTouchEdgeNavigation = viewModel::updateTouchEdgeNav,
                                    onTapZoneLayout = viewModel::updateTapZoneLayout,
                                    onShowTapZones = viewModel::updateShowTapZones,
                                    onCustomTapZones = viewModel::updateCustomTapZones,
                                    onCustomZonesMirrorInRtl = viewModel::updateCustomZonesMirrorInRtl,
                                    onShowPageNumber = viewModel::updateShowPageNumber,
                                    onKeepScreenOn = viewModel::updateKeepScreenOn,
                                    onSpeechBubbleDetection = viewModel::updateSpeechBubbleDetectionEnabled,
                                    onSpeechBubbleBoxes = viewModel::updateSpeechBubbleBoxesEnabled,
                                    onSpeechBubbleConfidence = viewModel::updateSpeechBubbleConfidence,
                                    onPanelAwareOrder = viewModel::updatePanelAwareOrder,
                                    onPanelDebugOverlay = viewModel::updatePanelDebugOverlay,
                                    onReadingMode = viewModel::updateReadingMode,
                                    onBubbleNavActive = viewModel::updateBubbleNavActive,
                                    onLargePageAspect = viewModel::updateLargePageAspect
                                )
                            },
                            showReadingMode = true,
                            libraryDefaultMode = state.libraryDefaultMode,
                            bubbleNavActive = state.bubbleNavActive
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
private fun ReaderOrientation.toRequestedOrientation(): Int = when (this) {
    ReaderOrientation.FREE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    ReaderOrientation.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    ReaderOrientation.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ReaderOrientation.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}