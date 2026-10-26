package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.MagnifiedBubble
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReadingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Speech-bubble navigation: the magnifier, long-press / S-Pen hover, and the step-forward / step-back routine
 * shared by the volume keys and the edge taps (bubbles first, then the page).
 *
 * Moved out of [ReaderViewModel]. One small dedupe: a long-press now finds its bubble and calls the same
 * magnify routine the step navigation uses (the two copies were identical). The mode itself lives in
 * [ReaderUiState.bubbleNavActive] so the UI always shows the real state.
 */
class ReaderBubbleNavigator(
    private val app: SigurvitaApp,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ReaderUiState>,
    private val detection: ReaderDetectionScheduler,
    private val nextPage: () -> Unit,
    private val previousPage: () -> Unit
) {
    /**
     * Speech-bubble navigation mode: edge taps and volume keys step through bubbles. Session-only, never persisted.
     * This is independent of the persisted panel-aware reading order, which only decides the order bubbles are visited in.
     */
    private var isBubbleNavActive: Boolean
        get() = state.value.bubbleNavActive
        set(value) {
            state.update { if (it.bubbleNavActive == value) it else it.copy(bubbleNavActive = value) }
        }
    private var completedForwardBubblePageIndex: Int = -1
    private var completedBackwardBubblePageIndex: Int = -1

    /** +1 = current page was reached going forward, -1 = going backward, 0 = unknown. */
    var pageArrivalDirection: Int = 0

    private var screenWidthPx: Int = 1080
    private var screenHeightPx: Int = 2400

    fun updateScreenSize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            screenWidthPx = widthPx
            screenHeightPx = heightPx
        }
    }

    /** A new chapter starts: forget which pages were already stepped through. */
    fun onNewChapter() {
        completedForwardBubblePageIndex = -1
        completedBackwardBubblePageIndex = -1
    }

    /** Remember which way the reader just moved so "continue" can be told from "reverse". Single-page steps only. */
    fun onPageChanged(previousIndex: Int, newIndex: Int) {
        if (newIndex == previousIndex + 1) {
            pageArrivalDirection = 1
        } else if (newIndex == previousIndex - 1) {
            pageArrivalDirection = -1
        }

        if (newIndex != completedForwardBubblePageIndex) {
            completedForwardBubblePageIndex = -1
        }
        if (newIndex != completedBackwardBubblePageIndex) {
            completedBackwardBubblePageIndex = -1
        }
    }

    private data class PageLayout(
        val originX: Float,
        val originY: Float,
        val dispW: Float,
        val dispH: Float
    )

    private fun calculatePageScreenLayout(
        pageWidth: Int,
        pageHeight: Int,
        screenWidthPx: Float,
        screenHeightPx: Float,
        fitMode: FitMode,
        readingMode: ReadingMode
    ): PageLayout {
        val boxW = if (screenWidthPx > 0f) screenWidthPx else 1080f
        val boxH = if (screenHeightPx > 0f) screenHeightPx else 2400f
        val pageW = if (pageWidth > 0) pageWidth.toFloat() else boxW
        val pageH = if (pageHeight > 0) pageHeight.toFloat() else boxH

        val contentScale = when {
            readingMode == ReadingMode.WEBTOON -> ContentScale.FillWidth
            fitMode == FitMode.FIT_WIDTH -> ContentScale.FillWidth
            fitMode == FitMode.FIT_HEIGHT -> ContentScale.FillHeight
            else -> ContentScale.Fit
        }

        val s = when (contentScale) {
            ContentScale.FillWidth -> boxW / pageW
            ContentScale.FillHeight -> boxH / pageH
            else -> minOf(boxW / pageW, boxH / pageH)
        }

        val dispW = pageW * s
        val dispH = pageH * s
        val originX = (boxW - dispW) / 2f
        val originY = (boxH - dispH) / 2f

        return PageLayout(originX, originY, dispW, dispH)
    }

    private fun magnifyBubbleObject(
        pageIndex: Int,
        bubble: DetectedBubble,
        pageBubbles: PageBubbles
    ) {
        isBubbleNavActive = true
        val current = state.value
        val page = current.pages.getOrNull(pageIndex) ?: return

        val layout = calculatePageScreenLayout(
            pageWidth = pageBubbles.pageWidth,
            pageHeight = pageBubbles.pageHeight,
            screenWidthPx = screenWidthPx.toFloat(),
            screenHeightPx = screenHeightPx.toFloat(),
            fitMode = current.settings.fitMode,
            readingMode = current.settings.readingMode
        )

        scope.launch {
            val cropResult = app.bubbleRepository.cropBubble(app, page, bubble, pageBubbles.letterbox, current.settings.speechBubbleBoxesEnabled, current.chapterId)
            if (cropResult != null) {
                val anchorX = layout.originX + cropResult.anchorNormX * layout.dispW
                val anchorY = layout.originY + cropResult.anchorNormY * layout.dispH
                val cropWOnScreen = (cropResult.cropRightNorm - cropResult.cropLeftNorm) * layout.dispW
                val cropHOnScreen = (cropResult.cropBottomNorm - cropResult.cropTopNorm) * layout.dispH

                state.update {
                    it.copy(
                        magnifiedBubble = MagnifiedBubble(
                            pageIndex = pageIndex,
                            bubbleId = bubble.id,
                            bitmap = cropResult.bitmap,
                            anchorScreenPx = Offset(anchorX, anchorY),
                            cropSizeOnScreen = Size(cropWOnScreen, cropHOnScreen),
                            tipRelOffset = Offset(cropResult.relTipX, cropResult.relTipY),
                            isLiteralBox = cropResult.isLiteralBox
                        )
                    )
                }
            }
        }
    }

    fun onBubbleLongPress(pageIndex: Int, bubbleId: Int) {
        isBubbleNavActive = true
        val pageBubbles = state.value.bubblesByPage[pageIndex] ?: return
        val bubble = pageBubbles.bubbles.find { it.id == bubbleId } ?: return
        magnifyBubbleObject(pageIndex, bubble, pageBubbles)
    }

    fun resetBubbleNavMode() {
        completedForwardBubblePageIndex = -1
        completedBackwardBubblePageIndex = -1
        // One atomic update, so the magnifier never lingers for a frame after the mode reads as off.
        state.update {
            if (it.bubbleNavActive || it.magnifiedBubble != null) {
                it.copy(bubbleNavActive = false, magnifiedBubble = null)
            } else {
                it
            }
        }
    }

    /** Bubble navigation needs detection on and a paged (non-webtoon) reading mode. */
    private fun canUseBubbleNav(current: ReaderUiState): Boolean =
        current.settings.speechBubbleDetectionEnabled &&
            current.settings.readingMode != ReadingMode.WEBTOON &&
            current.pages.isNotEmpty()

    /**
     * Reader options switch. Turning it on only arms the mode (the next edge tap or volume key magnifies the first
     * bubble); turning it off is the same full reset as tapping the centre of the screen.
     */
    fun updateBubbleNavActive(enabled: Boolean) {
        if (!enabled) {
            resetBubbleNavMode()
            return
        }
        if (canUseBubbleNav(state.value)) isBubbleNavActive = true
    }

    /**
     * Hold-both-volume-keys toggle. Off -> on behaves like a long-press on a bubble: it arms the mode and magnifies a
     * bubble (the first one in reading order, since no bubble was pressed). On -> off is a full reset.
     * Returns whether the mode changed, so the caller only gives feedback for a real toggle.
     */
    fun toggleBubbleNavMode(): Boolean {
        if (isBubbleNavActive) {
            resetBubbleNavMode()
            return true
        }
        val current = state.value
        if (!canUseBubbleNav(current)) return false
        isBubbleNavActive = true
        val pageIdx = current.currentPageIndex
        val pageBubbles = current.bubblesByPage[pageIdx]
        val first = pageBubbles?.bubbles?.firstOrNull()
        if (pageBubbles != null && first != null) {
            magnifyBubbleObject(pageIdx, first, pageBubbles)
        }
        return true
    }

    fun onBubbleHover(pageIndex: Int, bubbleId: Int?) {
        if (bubbleId == null) {
            resetBubbleNavMode()
            return
        }

        if (state.value.magnifiedBubble?.bubbleId == bubbleId && state.value.magnifiedBubble?.pageIndex == pageIndex) {
            return
        }

        onBubbleLongPress(pageIndex, bubbleId)
    }

    fun handleVolumeKey(isVolumeUp: Boolean): Boolean {
        val settings = state.value.settings
        if (!settings.volumeKeyNavigationEnabled) return false
        val isNext = if (settings.volumeKeyInvert) isVolumeUp else !isVolumeUp
        return navigateStep(isNext)
    }

    /**
     * A tap in one of the page-transition zones. [isNext] already accounts for the reading direction.
     * Steps through bubbles while bubble navigation mode is active, otherwise turns the page, exactly like a volume key.
     */
    fun onEdgeTapNavigate(isNext: Boolean) {
        navigateStep(isNext)
    }

    /** The one step-forward / step-back routine shared by the volume keys and the edge taps. Always returns true. */
    private fun navigateStep(isNext: Boolean): Boolean {
        val current = state.value
        val currentMag = current.magnifiedBubble
        if (currentMag != null) {
            val pageIdx = current.currentPageIndex
            val pageBubbles = current.bubblesByPage[pageIdx]
            val bubbles = pageBubbles?.bubbles ?: emptyList()
            val currentIdx = bubbles.indexOfFirst { it.id == currentMag.bubbleId }

            if (isNext) {
                if (currentIdx != -1 && currentIdx < bubbles.size - 1 && pageBubbles != null) {
                    val nextBubble = bubbles[currentIdx + 1]
                    magnifyBubbleObject(pageIdx, nextBubble, pageBubbles)
                    return true
                } else {
                    completedForwardBubblePageIndex = pageIdx
                    state.update { it.copy(magnifiedBubble = null) }
                    return true
                }
            } else {
                if (currentIdx > 0 && pageBubbles != null) {
                    val prevBubble = bubbles[currentIdx - 1]
                    magnifyBubbleObject(pageIdx, prevBubble, pageBubbles)
                    return true
                } else {
                    completedBackwardBubblePageIndex = pageIdx
                    state.update { it.copy(magnifiedBubble = null) }
                    return true
                }
            }
        }

        if (isBubbleNavActive && current.settings.speechBubbleDetectionEnabled && current.settings.readingMode != ReadingMode.WEBTOON) {
            val pageIdx = current.currentPageIndex
            if (isNext) {
                if (completedForwardBubblePageIndex == pageIdx) {
                    nextPage()
                    return true
                }
                // Reversing right after arriving on this page: just turn the page.
                if (pageArrivalDirection < 0) {
                    nextPage()
                    return true
                }
                val pageBubbles = current.bubblesByPage[pageIdx]
                if (pageBubbles != null) {
                    val bubbles = pageBubbles.bubbles
                    if (bubbles.isNotEmpty()) {
                        magnifyBubbleObject(pageIdx, bubbles.first(), pageBubbles)
                    } else {
                        completedForwardBubblePageIndex = pageIdx
                        nextPage()
                    }
                } else if (detection.hasJob(pageIdx)) {
                    scope.launch {
                        detection.jobFor(pageIdx)?.join()
                        if (state.value.currentPageIndex != pageIdx) return@launch
                        val pb = state.value.bubblesByPage[pageIdx]
                        val bList = pb?.bubbles ?: emptyList()
                        if (bList.isNotEmpty() && completedForwardBubblePageIndex != pageIdx) {
                            magnifyBubbleObject(pageIdx, bList.first(), pb!!)
                        } else {
                            completedForwardBubblePageIndex = pageIdx
                            nextPage()
                        }
                    }
                } else {
                    nextPage()
                }
            } else {
                if (completedBackwardBubblePageIndex == pageIdx) {
                    previousPage()
                    return true
                }
                // Reversing right after arriving on this page: just turn the page.
                if (pageArrivalDirection > 0) {
                    previousPage()
                    return true
                }
                val pageBubbles = current.bubblesByPage[pageIdx]
                if (pageBubbles != null) {
                    val bubbles = pageBubbles.bubbles
                    if (bubbles.isNotEmpty()) {
                        magnifyBubbleObject(pageIdx, bubbles.last(), pageBubbles)
                    } else {
                        completedBackwardBubblePageIndex = pageIdx
                        previousPage()
                    }
                } else if (detection.hasJob(pageIdx)) {
                    scope.launch {
                        detection.jobFor(pageIdx)?.join()
                        if (state.value.currentPageIndex != pageIdx) return@launch
                        val pb = state.value.bubblesByPage[pageIdx]
                        val bList = pb?.bubbles ?: emptyList()
                        if (bList.isNotEmpty() && completedBackwardBubblePageIndex != pageIdx) {
                            magnifyBubbleObject(pageIdx, bList.last(), pb!!)
                        } else {
                            completedBackwardBubblePageIndex = pageIdx
                            previousPage()
                        }
                    }
                } else {
                    previousPage()
                }
            }
        } else {
            if (isNext) {
                nextPage()
            } else {
                previousPage()
            }
        }
        return true
    }
}
