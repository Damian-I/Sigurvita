package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.util.PanelMap
import com.zemmy.sigurvita.core.util.orderPanels
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

const val MAX_VIEW_ZOOM = 6f
const val DEFAULT_LARGE_PAGE_ASPECT = 1.1f

private const val MIN_USEFUL_ZOOM = 1.08f
private const val MIN_PANEL_AREA = 0.01f
private const val MIN_PANEL_COVERAGE = 0.15
private const val PANEL_PADDING = 0.015f
private const val HALF_OVERLAP = 0.02f
private const val SCROLL_STRIDE = 0.92f
private const val MIN_RECT = 0.02f
private const val END_STEP = Int.MAX_VALUE / 2

enum class LargePageMode { GUIDED, SCROLL }

data class ViewTarget(val scale: Float, val offsetX: Float, val offsetY: Float)

class ViewRequest(val id: Int, val pageIndex: Int, val target: ViewTarget)

/** Zoom/pan of one page. screen = centre + offset + scale * (layoutPoint - centre). */
class PageTransform {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    val isIdentity: Boolean
        get() = scale <= 1.02f && abs(offsetX) < 2f && abs(offsetY) < 2f

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun applyGesture(zoom: Float, pan: Offset) {
        scale = (scale * zoom).coerceIn(1f, MAX_VIEW_ZOOM)
        if (scale > 1f) {
            offsetX += pan.x
            offsetY += pan.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }
}

/**
 * Owns the zoom/pan of every visible page, decides which pages are "large", and steps a large page through
 * panels (guided) or sideways windows (scroll). Main thread only.
 */
class PageViewportController {
    private class Geometry(val originX: Float, val originY: Float, val dispW: Float, val dispH: Float)

    private val transforms = HashMap<Int, PageTransform>()
    private val aspects = mutableStateMapOf<Int, Float>()

    var aspectThreshold by mutableFloatStateOf(DEFAULT_LARGE_PAGE_ASPECT)
        private set
    var paged by mutableStateOf(true)
        private set
    var modeOverride by mutableStateOf<LargePageMode?>(null)
        private set
    var request by mutableStateOf<ViewRequest?>(null)
        private set

    var boxWidth = 0f
        private set
    var boxHeight = 0f
        private set

    private var requestId = 0
    private var stepPage = -1
    private var step = 0
    private var stepMode: LargePageMode? = null
    private var free = false

    // ---- state shared with the UI -------------------------------------------------------------

    fun transformFor(pageIndex: Int): PageTransform = transforms.getOrPut(pageIndex) { PageTransform() }

    fun release(pageIndex: Int) {
        transforms[pageIndex]?.reset()
    }

    fun sync(aspectThreshold: Float, paged: Boolean) {
        if (this.aspectThreshold != aspectThreshold) this.aspectThreshold = aspectThreshold
        if (this.paged != paged) this.paged = paged
    }

    fun reportBoxSize(width: Int, height: Int) {
        boxWidth = width.toFloat()
        boxHeight = height.toFloat()
    }

    fun reportPageSize(pageIndex: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) aspects[pageIndex] = width.toFloat() / height
    }

    fun isLarge(pageIndex: Int): Boolean {
        if (!paged) return false
        val aspect = aspects[pageIndex] ?: return false
        return aspect >= aspectThreshold
    }

    fun effectiveMode(bubbleNavActive: Boolean): LargePageMode =
        modeOverride ?: if (bubbleNavActive) LargePageMode.GUIDED else LargePageMode.SCROLL

    fun clearModeOverride() {
        modeOverride = null
    }

    fun toggleMode(bubbleNavActive: Boolean, pageIndex: Int) {
        modeOverride = if (effectiveMode(bubbleNavActive) == LargePageMode.GUIDED) {
            LargePageMode.SCROLL
        } else {
            LargePageMode.GUIDED
        }
        stepPage = pageIndex
        step = 0
        free = false
        stepMode = null
        animateTo(pageIndex, IDENTITY)
    }

    fun onNewChapter() {
        transforms.clear()
        aspects.clear()
        stepPage = -1
        step = 0
        free = false
        stepMode = null
        request = null
    }

    /** direction: +1 arrived going forward, -1 going back (starts at the end of the page), 0 jump. */
    fun onPageChanged(pageIndex: Int, direction: Int) {
        stepPage = pageIndex
        step = if (direction < 0) END_STEP else 0
        free = false
        stepMode = null
        request = null
    }

    /** The user pinched or panned. A view that is not back at 1x counts as "free". */
    fun onManualTransform(pageIndex: Int) {
        request = null
        if (!transformFor(pageIndex).isIdentity) {
            ensurePage(pageIndex)
            free = true
        }
    }

    suspend fun run(request: ViewRequest) {
        val t = transformFor(request.pageIndex)
        val s0 = t.scale
        val x0 = t.offsetX
        val y0 = t.offsetY
        val to = request.target
        animate(0f, 1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) { p, _ ->
            t.scale = s0 + (to.scale - s0) * p
            t.offsetX = x0 + (to.offsetX - x0) * p
            t.offsetY = y0 + (to.offsetY - y0) * p
        }
    }

    // ---- stepping -----------------------------------------------------------------------------

    /** True when the step moved the view; false means the caller should turn the page. */
    fun step(
        isNext: Boolean,
        pageIndex: Int,
        panelMap: PanelMap?,
        rtl: Boolean,
        bubbleNavActive: Boolean
    ): Boolean {
        if (!isLarge(pageIndex)) return false
        val g = geometryFor(pageIndex) ?: return false
        val mode = effectiveMode(bubbleNavActive)
        val targets = buildTargets(pageIndex, g, panelMap, rtl, mode)
        if (targets.isEmpty()) return false

        ensurePage(pageIndex)
        val t = transformFor(pageIndex)
        if (stepMode != null && stepMode != mode) {
            // The step list changed under us (mode switched): fall back to "whole page, then turn".
            if (t.isIdentity) {
                step = 0
                free = false
            } else {
                free = true
            }
        }
        stepMode = mode

        val last = targets.size + 1
        if (free) {
            free = false
            step = if (isNext) last else 0
            if (t.isIdentity) return false
            animateTo(pageIndex, IDENTITY)
            return true
        }

        val current = step.coerceIn(0, last)
        val next = if (isNext) current + 1 else current - 1
        if (next < 0 || next > last) {
            step = current
            return false
        }
        step = next
        animateTo(pageIndex, if (next in 1..targets.size) viewTargetFor(targets[next - 1], g) else IDENTITY)
        return true
    }

    /** A bubble was magnified: bring its panel (guided) or window (scroll) into view. */
    fun focusBubble(
        pageIndex: Int,
        box: NormRect,
        panelMap: PanelMap?,
        rtl: Boolean,
        bubbleNavActive: Boolean
    ) {
        if (!isLarge(pageIndex)) return
        val g = geometryFor(pageIndex) ?: return
        val mode = effectiveMode(bubbleNavActive)
        val targets = buildTargets(pageIndex, g, panelMap, rtl, mode)
        if (targets.isEmpty()) return
        ensurePage(pageIndex)

        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        fun contains(r: NormRect) = cx in r.left..r.right && cy in r.top..r.bottom

        // Same panel as before: keep the view still.
        val currentIdx = step - 1
        if (!free && stepMode == mode && currentIdx in targets.indices && contains(targets[currentIdx])) return

        var best = -1
        var bestArea = Float.MAX_VALUE
        targets.forEachIndexed { i, r ->
            if (contains(r)) {
                val area = (r.right - r.left) * (r.bottom - r.top)
                if (area < bestArea) {
                    best = i
                    bestArea = area
                }
            }
        }
        if (best < 0) {
            var bestDist = Float.MAX_VALUE
            targets.forEachIndexed { i, r ->
                val dx = cx - (r.left + r.right) / 2f
                val dy = cy - (r.top + r.bottom) / 2f
                val d = dx * dx + dy * dy
                if (d < bestDist) {
                    best = i
                    bestDist = d
                }
            }
        }
        step = best + 1
        free = false
        stepMode = mode
        animateTo(pageIndex, viewTargetFor(targets[best], g))
    }

    // ---- geometry -----------------------------------------------------------------------------

    private fun ensurePage(pageIndex: Int) {
        if (stepPage != pageIndex) {
            stepPage = pageIndex
            step = 0
            free = false
            stepMode = null
        }
    }

    private fun animateTo(pageIndex: Int, target: ViewTarget) {
        request = ViewRequest(++requestId, pageIndex, target)
    }

    /** Where the page sits in the box at 1x (ContentScale.Fit). Only the aspect ratio is needed. */
    private fun geometryFor(pageIndex: Int): Geometry? {
        val aspect = aspects[pageIndex] ?: return null
        if (boxWidth <= 0f || boxHeight <= 0f) return null
        val s = min(boxWidth / aspect, boxHeight)
        val dispW = aspect * s
        val dispH = s
        return Geometry((boxWidth - dispW) / 2f, (boxHeight - dispH) / 2f, dispW, dispH)
    }

    private fun buildTargets(
        pageIndex: Int,
        g: Geometry,
        panelMap: PanelMap?,
        rtl: Boolean,
        mode: LargePageMode
    ): List<NormRect> {
        fun useful(r: NormRect) = viewTargetFor(r, g).scale > MIN_USEFUL_ZOOM
        return if (mode == LargePageMode.GUIDED) {
            panelTargets(pageIndex, panelMap, rtl).filter { useful(it) }
                .ifEmpty { halves(rtl).filter { useful(it) } }
        } else {
            scrollTargets(g, rtl).filter { useful(it) }
        }
    }

    private fun panelTargets(pageIndex: Int, panelMap: PanelMap?, rtl: Boolean): List<NormRect> {
        val panels = panelMap?.panels.orEmpty().filter { it.area >= MIN_PANEL_AREA }
        if (panels.isEmpty() || panels.sumOf { it.area.toDouble() } < MIN_PANEL_COVERAGE) return emptyList()
        val spread = (aspects[pageIndex] ?: 1f) > 1.1f
        return orderPanels(panels, rtl, spread).map { it.box.padded(PANEL_PADDING) }
    }

    private fun halves(rtl: Boolean): List<NormRect> {
        val left = NormRect(0f, 0f, 0.5f + HALF_OVERLAP, 1f)
        val right = NormRect(0.5f - HALF_OVERLAP, 0f, 1f, 1f)
        return if (rtl) listOf(right, left) else listOf(left, right)
    }

    /** Full-height windows across the page, scaled so the page fills the screen height. */
    private fun scrollTargets(g: Geometry, rtl: Boolean): List<NormRect> {
        val zoom = (boxHeight / g.dispH).coerceIn(1f, MAX_VIEW_ZOOM)
        val visible = boxWidth / (zoom * g.dispW)
        if (visible >= 0.98f) return emptyList()
        val count = ceil((1f - visible) / (visible * SCROLL_STRIDE)).toInt() + 1
        val half = visible / 2f
        val windows = (0 until count).map { i ->
            val c = half + (1f - visible) * i / (count - 1)
            NormRect(c - half, 0f, c + half, 1f)
        }
        return if (rtl) windows.reversed() else windows
    }

    private fun viewTargetFor(rect: NormRect, g: Geometry): ViewTarget {
        val rw = (rect.right - rect.left).coerceAtLeast(MIN_RECT)
        val rh = (rect.bottom - rect.top).coerceAtLeast(MIN_RECT)
        val zoom = min(boxWidth / (rw * g.dispW), boxHeight / (rh * g.dispH)).coerceIn(1f, MAX_VIEW_ZOOM)
        val px = g.originX + (rect.left + rect.right) / 2f * g.dispW
        val py = g.originY + (rect.top + rect.bottom) / 2f * g.dispH
        val ox = clampAxis(-zoom * (px - boxWidth / 2f), zoom, boxWidth, g.originX, g.dispW)
        val oy = clampAxis(-zoom * (py - boxHeight / 2f), zoom, boxHeight, g.originY, g.dispH)
        return ViewTarget(zoom, ox, oy)
    }

    /** Keeps the view inside the page when the zoomed page is larger than the box, centres it otherwise. */
    private fun clampAxis(offset: Float, zoom: Float, box: Float, origin: Float, disp: Float): Float {
        val c = box / 2f
        val base = c + zoom * (origin - c)
        val size = disp * zoom
        return if (size <= box) {
            box / 2f - (base + size / 2f)
        } else {
            offset.coerceIn(box - size - base, -base)
        }
    }

    private fun NormRect.padded(p: Float) = NormRect(
        (left - p).coerceAtLeast(0f),
        (top - p).coerceAtLeast(0f),
        (right + p).coerceAtMost(1f),
        (bottom + p).coerceAtMost(1f)
    )

    private companion object {
        val IDENTITY = ViewTarget(1f, 0f, 0f)
    }
}