package com.zemmy.sigurvita.ui.screens.reader

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.ui.components.LoadingState

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

    val state by viewModel.uiState.collectAsState()

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
            AlertDialog(
                onDismissRequest = { viewModel.dismissResumePrompt(startFromBeginning = true) },
                title = { Text("Resume Position") },
                text = {
                    Text("You previously finished this issue (Page ${state.promptPageNum} of ${state.pages.size}). Would you like to start from the beginning or resume from the last page?")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissResumePrompt(startFromBeginning = false) }) {
                        Text("Resume Last Page")
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
                    title = { Text(stringResource(R.string.page_indicator, state.currentPageIndex + 1, state.pages.size)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.8f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
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
        }
    }
}
