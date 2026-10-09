package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.model.PageBubbles
import kotlin.math.abs
import kotlin.math.max

data class PanelInfo(
    val label: Int,          // 1..n, equals value in label map
    val box: NormRect,       // page-normalized bounding box
    val cx: Float, val cy: Float,   // centroid of panel pixels, page-normalized
    val area: Float          // fraction of page area (0.0..1.0)
)

data class PanelMap(
    val gridW: Int,
    val gridH: Int,
    val labels: ShortArray,   // row-major, 0 = gutter/none, 1..n = panel
    val letterbox: LetterboxParams,
    val panels: List<PanelInfo> // index i corresponds to label i + 1
) {
    fun labelAtNorm(nx: Float, ny: Float): Int {
        val lb = letterbox
        val lx = nx * lb.originalWidth * lb.gain + lb.padX
        val ly = ny * lb.originalHeight * lb.gain + lb.padY
        val gx = (lx / lb.inputSize * gridW).toInt().coerceIn(0, gridW - 1)
        val gy = (ly / lb.inputSize * gridH).toInt().coerceIn(0, gridH - 1)
        val idx = gy * gridW + gx
        return if (idx in labels.indices) labels[idx].toInt() else 0
    }

    fun nearestPanel(box: NormRect): Int {
        if (panels.isEmpty()) return -1
        var minSqDist = Float.MAX_VALUE
        var bestLabel = -1
        for (p in panels) {
            val dx = maxOf(0f, maxOf(p.box.left - box.right, box.left - p.box.right))
            val dy = maxOf(0f, maxOf(p.box.top - box.bottom, box.top - p.box.bottom))
            val sqDist = dx * dx + dy * dy
            if (sqDist < minSqDist) {
                minSqDist = sqDist
                bestLabel = p.label
            }
        }
        return bestLabel
    }

    fun reordered(order: List<PanelInfo>): PanelMap {
        if (order.size != panels.size) return this
        val oldToNew = IntArray(panels.size + 1)
        for ((newIdx, p) in order.withIndex()) {
            oldToNew[p.label] = newIdx + 1
        }
        val newLabels = ShortArray(labels.size)
        for (i in labels.indices) {
            val oldLab = labels[i].toInt()
            newLabels[i] = if (oldLab in 1..panels.size) oldToNew[oldLab].toShort() else 0
        }
        val newPanels = order.mapIndexed { newIdx, p ->
            PanelInfo(
                label = newIdx + 1,
                box = p.box,
                cx = p.cx,
                cy = p.cy,
                area = p.area
            )
        }
        return PanelMap(gridW, gridH, newLabels, letterbox, newPanels)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PanelMap

        if (gridW != other.gridW) return false
        if (gridH != other.gridH) return false
        if (!labels.contentEquals(other.labels)) return false
        if (letterbox != other.letterbox) return false
        return panels == other.panels
    }

    override fun hashCode(): Int {
        var result = gridW
        result = 31 * result + gridH
        result = 31 * result + labels.contentHashCode()
        result = 31 * result + letterbox.hashCode()
        result = 31 * result + panels.hashCode()
        return result
    }
}

data class Spot(
    val box: NormRect,
    val cx: Float,
    val cy: Float
)

private val NormRect.width get() = right - left
private val NormRect.height get() = bottom - top
private fun span(a0: Float, a1: Float, b0: Float, b1: Float) =
    (minOf(a1, b1) - maxOf(a0, b0)).coerceAtLeast(0f)

fun <T> readingOrder(
    items: List<T>,
    spot: (T) -> Spot,
    rtl: Boolean,
    rowOverlap: Float = 0.5f,
    sameCy: Float = 0.03f
): List<T> {
    val n = items.size
    if (n < 2) return items
    val s = items.map(spot)
    val wins = IntArray(n)
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            val a = s[i]
            val b = s[j]
            val vOv = span(a.box.top, a.box.bottom, b.box.top, b.box.bottom) /
                    minOf(a.box.height, b.box.height).coerceAtLeast(1e-6f)
            val hOv = span(a.box.left, a.box.right, b.box.left, b.box.right) /
                    minOf(a.box.width, b.box.width).coerceAtLeast(1e-6f)
            val aLeads = if (rtl) a.cx > b.cx else a.cx < b.cx
            val aFirst = when {
                vOv >= rowOverlap && hOv < rowOverlap -> aLeads
                vOv < rowOverlap -> a.cy < b.cy
                abs(a.cy - b.cy) > sameCy -> a.cy < b.cy
                else -> aLeads
            }
            if (aFirst) wins[i]++ else wins[j]++
        }
    }
    return items.indices
        .sortedWith(compareByDescending<Int> { wins[it] }.thenBy { s[it].box.top }.thenBy { s[it].box.left })
        .map { items[it] }
}

fun orderPanels(panels: List<PanelInfo>, rtl: Boolean, isSpread: Boolean): List<PanelInfo> {
    fun one(l: List<PanelInfo>) = readingOrder(l, { Spot(it.box, it.cx, it.cy) }, rtl)
    if (!isSpread) return one(panels)
    val crossesFold = panels.any { it.box.left < 0.47f && it.box.right > 0.53f }
    if (crossesFold) return one(panels)
    val left = panels.filter { it.cx < 0.5f }
    val right = panels.filter { it.cx >= 0.5f }
    return if (rtl) one(right) + one(left) else one(left) + one(right)
}

fun panelOfBubble(b: DetectedBubble, bubbleLb: LetterboxParams, map: PanelMap): Int {
    val sz = b.mask.size
    if (sz <= 0 || b.mask.probs.size < sz * sz || map.panels.isEmpty()) {
        return map.nearestPanel(b.box)
    }
    val scale = bubbleLb.inputSize.toFloat() / sz
    val votes = IntArray(map.panels.size + 1)
    for (gy in 0 until sz) {
        for (gx in 0 until sz) {
            val prob = b.mask.probs[gy * sz + gx].toInt() and 0xFF
            if (prob < 50) continue
            val (nx, ny) = bubbleLb.letterboxToNorm(gx * scale, gy * scale)
            val label = map.labelAtNorm(nx, ny)
            if (label in 1..map.panels.size) {
                votes[label]++
            }
        }
    }
    val best = (1..map.panels.size).maxByOrNull { votes[it] }
    if (best != null && votes[best] > 0) return best
    return map.nearestPanel(b.box)
}

fun orderBubbles(
    pageBubbles: PageBubbles,
    panelMap: PanelMap?,
    rtl: Boolean,
    panelAwareEnabled: Boolean = true
): List<DetectedBubble> {
    val bubbles = pageBubbles.bubbles
    if (bubbles.size <= 1) return bubbles

    // Sanity gate for panel map
    if (!panelAwareEnabled || panelMap == null || panelMap.panels.isEmpty()) {
        return sortBubblesForReading(bubbles, rtl)
    }

    val isSpread = (pageBubbles.pageWidth.toFloat() / pageBubbles.pageHeight.coerceAtLeast(1)) > 1.1f
    val orderedPanels = orderPanels(panelMap.panels, rtl, isSpread)
    val reorderedMap = panelMap.reordered(orderedPanels)

    // Check panel coverage (sum of panel areas)
    val totalPanelArea = reorderedMap.panels.sumOf { it.area.toDouble() }.toFloat()
    if (totalPanelArea < 0.15f) {
        // Implausibly small share of the page covered by panels
        return sortBubblesForReading(bubbles, rtl)
    }

    // Assign each bubble to panel
    val panelToBubbles = Array(reorderedMap.panels.size + 1) { mutableListOf<DetectedBubble>() }
    var unassignedCount = 0

    for (b in bubbles) {
        val label = panelOfBubble(b, pageBubbles.letterbox, reorderedMap)
        if (label in 1..reorderedMap.panels.size) {
            panelToBubbles[label].add(b)
        } else {
            unassignedCount++
        }
    }

    // Fallback if > 20% of bubbles fell in no panel
    if (unassignedCount > 0 && (unassignedCount.toFloat() / bubbles.size) > 0.20f) {
        return sortBubblesForReading(bubbles, rtl)
    }

    // Order bubbles inside each panel and flatten
    val result = mutableListOf<DetectedBubble>()
    for (label in 1..reorderedMap.panels.size) {
        val pBubbles = panelToBubbles[label]
        if (pBubbles.isEmpty()) continue
        val orderedInPanel = readingOrder(
            pBubbles,
            spot = { b ->
                val cx = (b.box.left + b.box.right) / 2f
                val cy = (b.box.top + b.box.bottom) / 2f
                Spot(b.box, cx, cy)
            },
            rtl = rtl,
            rowOverlap = 0.40f
        )
        result.addAll(orderedInPanel)
    }

    // If any unassigned bubbles remained (<=20%), append them or re-sort
    if (result.size < bubbles.size) {
        val addedIds = result.map { it.id }.toSet()
        val remaining = bubbles.filter { it.id !in addedIds }
        if (remaining.isNotEmpty()) {
            val orderedRemaining = readingOrder(
                remaining,
                spot = { b -> Spot(b.box, (b.box.left + b.box.right) / 2f, (b.box.top + b.box.bottom) / 2f) },
                rtl = rtl,
                rowOverlap = 0.40f
            )
            result.addAll(orderedRemaining)
        }
    }

    return result
}
