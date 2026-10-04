package com.zemmy.sigurvita.ui.screens.reader

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.ReadingMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.ui.components.LoadingState

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
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

    val handleBack: () -> Unit = {
        scope.launch {
            viewModel.flushProgressSync()
            onBackClick()
        }
    }

    BackHandler {
        handleBack()
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

    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushProgress()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                fitMode = state.settings.fitMode,
                touchEdgeNavigationEnabled = state.settings.touchEdgeNavigationEnabled,
                onPageChanged = { viewModel.onPageChanged(it) },
                onNextPage = { viewModel.nextPage() },
                onPreviousPage = { viewModel.previousPage() },
                onTap = { viewModel.toggleControls() }
            )

            // Top Control Overlay
            AnimatedVisibility(
                visible = state.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.page_indicator, state.currentPageIndex + 1, state.pages.size))
                            if (state.settings.isIncognitoMode) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VisibilityOff,
                                            contentDescription = "Incognito",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onTertiary
                                        )
                                        Text(
                                            text = "Incognito",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiary
                                        )
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
                        IconButton(onClick = { viewModel.toggleMarkCurrentPage() }) {
                            Icon(
                                imageVector = if (isCurrentPageMarked) Icons.Default.Bookmark else Icons.Outlined.Bookmark,
                                contentDescription = if (isCurrentPageMarked) "Remove Page Mark" else "Mark Page for Review",
                                tint = if (isCurrentPageMarked) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(onClick = { showOptionsBottomSheet = true }) {
                            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.reader_options_cd), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.8f),
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
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${state.currentPageIndex + 1}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = state.currentPageIndex.toFloat(),
                                onValueChange = { viewModel.onPageChanged(it.toInt()) },
                                valueRange = 0f..(if (state.pages.isNotEmpty()) (state.pages.size - 1).toFloat() else 0f),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${state.pages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (showOptionsBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showOptionsBottomSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reader_options_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(stringResource(R.string.reading_mode), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReadingMode.entries.forEach { mode ->
                                val isLibraryDefault = (mode == state.libraryDefaultMode)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    FilterChip(
                                        selected = (state.settings.readingMode == mode),
                                        onClick = { viewModel.updateReadingMode(mode) },
                                        label = { Text(stringResource(mode.stringRes)) }
                                    )
                                    if (isLibraryDefault) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.library_default_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(stringResource(R.string.fit_mode), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FitMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = (state.settings.fitMode == mode),
                                    onClick = { viewModel.updateFitMode(mode) },
                                    label = { Text(stringResource(mode.stringRes)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.touch_edge_nav), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                Text(stringResource(R.string.touch_edge_nav_subtitle), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                            }
                            Switch(
                                checked = state.settings.touchEdgeNavigationEnabled,
                                onCheckedChange = { viewModel.updateTouchEdgeNav(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                Text(stringResource(R.string.keep_screen_on_subtitle), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                            }
                            Switch(
                                checked = state.settings.keepScreenOn,
                                onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
