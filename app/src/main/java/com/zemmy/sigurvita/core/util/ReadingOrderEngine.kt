package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.model.PageBubbles
import kotlin.math.abs

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

    fun nearestPanel(box: NormRect, rtl: Boolean = false): Int {
        if (panels.isEmpty()) return -1

        val bcx = (box.left + box.right) / 2f
        val bcy = (box.top + box.bottom) / 2f

        // Check for exact enclosure: panels whose bounding box contains the bubble center
        val enclosing = panels.filter { p ->
            bcx >= p.box.left && bcx <= p.box.right && bcy >= p.box.top && bcy <= p.box.bottom
        }
        if (enclosing.isNotEmpty()) {
            // Prefer the smallest area panel (e.g. inset panel)
            return enclosing.minByOrNull { it.area }?.label ?: enclosing[0].label
        }

        var minScore = Float.MAX_VALUE
        var bestLabel = -1

        for (p in panels) {
            val dx = maxOf(0f, maxOf(p.box.left - box.right, box.left - p.box.right))
            val dy = maxOf(0f, maxOf(p.box.top - box.bottom, box.top - p.box.bottom))
            val sqDist = dx * dx + dy * dy

            // Directional bias for gutter bubbles (prefer panel following reading flow if distance is tied)
            val hDiff = if (rtl) (bcx - p.cx) else (p.cx - bcx)
            val readingBias = if (hDiff >= 0) -0.002f else 0.002f

            val score = sqDist + readingBias
            if (score < minScore) {
                minScore = score
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

            // Check container / inset panel relationship (one item mostly contained inside or heavily overlapping the other)
            val interLeft = maxOf(a.box.left, b.box.left)
            val interTop = maxOf(a.box.top, b.box.top)
            val interRight = minOf(a.box.right, b.box.right)
            val interBottom = minOf(a.box.bottom, b.box.bottom)
            val interW = maxOf(0f, interRight - interLeft)
            val interH = maxOf(0f, interBottom - interTop)
            val interArea = interW * interH
            val areaA = (a.box.right - a.box.left) * (a.box.bottom - a.box.top)
            val areaB = (b.box.right - b.box.left) * (b.box.bottom - b.box.top)
            val minArea = minOf(areaA, areaB).coerceAtLeast(1e-6f)
            val isInsetOverlap = (interArea / minArea) >= 0.70f && abs(areaA - areaB) > 0.05f

            val cyDiff = abs(a.cy - b.cy)
            val minH = minOf(a.box.height, b.box.height).coerceAtLeast(1e-6f)
            val vOv = span(a.box.top, a.box.bottom, b.box.top, b.box.bottom) / minH
            val hOv = span(a.box.left, a.box.right, b.box.left, b.box.right) /
                    minOf(a.box.width, b.box.width).coerceAtLeast(1e-6f)
            val aLeads = if (rtl) a.cx > b.cx else a.cx < b.cx
            val topDiff = abs(a.box.top - b.box.top)

            val aFirst = if (isInsetOverlap) {
                // Inset / container relationship:
                val smallerIsA = areaA < areaB
                val smaller = if (smallerIsA) a else b
                val larger = if (smallerIsA) b else a
                // If smaller panel sits in the upper part of larger panel (or top < larger.cy)
                val smallerFirst = smaller.cy < larger.cy || smaller.box.top < larger.cy
                if (smallerIsA) smallerFirst else !smallerFirst
            } else if (maxOf(areaA, areaB) >= 0.35f && minOf(areaA, areaB) < 0.10f && abs(a.cy - b.cy) > 0.10f) {
                // Giant background/splash panel vs small header/corner panel: smaller panel in upper portion comes first
                val smallerIsA = areaA < areaB
                val smaller = if (smallerIsA) a else b
                val larger = if (smallerIsA) b else a
                val smallerFirst = smaller.cy < larger.cy
                if (smallerIsA) smallerFirst else !smallerFirst
            } else if (a.box.top + 0.30f <= b.box.top && a.cy + 0.15f <= b.cy && a.box.height < 0.80f) {
                // Panel A is distinctly higher up in a separate row than Panel B
                true
            } else if (b.box.top + 0.30f <= a.box.top && b.cy + 0.15f <= a.cy && b.box.height < 0.80f) {
                false
            } else if (vOv >= rowOverlap && hOv < rowOverlap) {
                // Side-by-side columns with vertical overlap: horizontal column order leads
                aLeads
            } else if (topDiff <= 0.05f && vOv >= rowOverlap) {
                // Both panels start at nearly the same top coordinate: leading side first
                aLeads
            } else if (cyDiff > maxOf(0.04f, minH * 0.40f)) {
                // Distinct top-to-bottom vertical separation
                a.cy < b.cy
            } else {
                when {
                    vOv < rowOverlap -> a.cy < b.cy
                    abs(a.cy - b.cy) > sameCy -> a.cy < b.cy
                    else -> aLeads
                }
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

fun panelOfBubble(
    b: DetectedBubble,
    bubbleLb: LetterboxParams,
    map: PanelMap,
    rtl: Boolean = false
): Int {
    val sz = b.mask.size
    if (sz <= 0 || b.mask.probs.size < sz * sz || map.panels.isEmpty()) {
        return map.nearestPanel(b.box, rtl)
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

    val candidateLabels = (1..map.panels.size).filter { votes[it] > 0 }
    if (candidateLabels.isNotEmpty()) {
        val maxVotes = candidateLabels.maxOf { votes[it] }
        val topCandidates = candidateLabels.filter { votes[it] >= (maxVotes * 0.5f).toInt().coerceAtLeast(1) }
        if (topCandidates.size == 1) {
            return topCandidates[0]
        }
        // If multiple candidate panels have significant votes (e.g. inset panel vs enclosing background panel),
        // prefer the smaller inset panel
        return topCandidates.minByOrNull { map.panels[it - 1].area } ?: topCandidates[0]
    }

    return map.nearestPanel(b.box, rtl)
}

private data class ReadingUnit(
    val box: NormRect,
    val cx: Float,
    val cy: Float,
    val bubbles: List<DetectedBubble>
)

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
        val label = panelOfBubble(b, pageBubbles.letterbox, reorderedMap, rtl)
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

    // Build reading units. Large background container panels (area >= 0.25)
    // are partitioned into row-based bubble clusters so that unassigned bubbles
    // in the container panel are spatially ordered alongside any inset panels.
    val units = mutableListOf<ReadingUnit>()
    for (label in 1..reorderedMap.panels.size) {
        val pBubbles = panelToBubbles[label]
        if (pBubbles.isEmpty()) continue

        val panel = reorderedMap.panels[label - 1]
        val isContainer = (panel.area >= 0.25f || (panel.box.right - panel.box.left) >= 0.65f) && pBubbles.size > 1

        if (isContainer) {
            val leftBubbles = pBubbles.filter { (it.box.left + it.box.right) / 2f < 0.50f }
            val rightBubbles = pBubbles.filter { (it.box.left + it.box.right) / 2f >= 0.50f }

            val partitions = if (leftBubbles.isNotEmpty() && rightBubbles.isNotEmpty() && (panel.box.right - panel.box.left) >= 0.65f) {
                listOf(leftBubbles, rightBubbles)
            } else {
                listOf(pBubbles)
            }

            for (partBubbles in partitions) {
                val clusters = mutableListOf<MutableList<DetectedBubble>>()
                var currentCluster = mutableListOf<DetectedBubble>()

                for (b in partBubbles.sortedBy { it.box.top }) {
                    if (currentCluster.isEmpty()) {
                        currentCluster.add(b)
                    } else {
                        val prevCy =
                            currentCluster.map { (it.box.top + it.box.bottom) / 2f }.average()
                                .toFloat()
                        val curCy = (b.box.top + b.box.bottom) / 2f
                        if (abs(curCy - prevCy) <= 0.07f || b.box.top <= currentCluster.maxOf { it.box.bottom } + 0.02f) {
                            currentCluster.add(b)
                        } else {
                            clusters.add(currentCluster)
                            currentCluster = mutableListOf(b)
                        }
                    }
                }
                if (currentCluster.isNotEmpty()) {
                    clusters.add(currentCluster)
                }

                for (cluster in clusters) {
                    val cLeft = cluster.minOf { it.box.left }
                    val cTop = cluster.minOf { it.box.top }
                    val cRight = cluster.maxOf { it.box.right }
                    val cBottom = cluster.maxOf { it.box.bottom }
                    val cCx = (cLeft + cRight) / 2f
                    val cCy = (cTop + cBottom) / 2f
                    units.add(
                        ReadingUnit(
                            NormRect(cLeft, cTop, cRight, cBottom),
                            cCx,
                            cCy,
                            cluster
                        )
                    )
                }
            }
        } else {
            units.add(ReadingUnit(panel.box, panel.cx, panel.cy, pBubbles))
        }
    }

    // Spatially order all reading units (standalone/inset panels + container bubble groups)
    val orderedUnits = readingOrder(
        units,
        spot = { Spot(it.box, it.cx, it.cy) },
        rtl = rtl
    )

    // Flatten bubbles from ordered units using intra-panel bubble row-banding
    val result = mutableListOf<DetectedBubble>()
    for (unit in orderedUnits) {
        val orderedInUnit = orderBubblesInPanel(unit.bubbles, rtl)
        result.addAll(orderedInUnit)
    }

    // If any unassigned bubbles remained (<=20%), append them in reading order
    if (result.size < bubbles.size) {
        val addedIds = result.map { it.id }.toSet()
        val remaining = bubbles.filter { it.id !in addedIds }
        if (remaining.isNotEmpty()) {
            val orderedRemaining = orderBubblesInPanel(remaining, rtl)
            result.addAll(orderedRemaining)
        }
    }

    return result
}

fun orderBubblesInPanel(
    bubbles: List<DetectedBubble>,
    rtl: Boolean
): List<DetectedBubble> {
    if (bubbles.size <= 1) return bubbles

    val sortedByTop = bubbles.sortedWith(compareBy({ it.box.top }, { it.id }))
    val rows = mutableListOf<MutableList<DetectedBubble>>()
    var rowTop = 0f
    var rowBottom = 0f

    for (b in sortedByTop) {
        if (rows.isEmpty()) {
            rows.add(mutableListOf(b))
            rowTop = b.box.top
            rowBottom = b.box.bottom
        } else {
            val bH = b.box.bottom - b.box.top
            val curH = rowBottom - rowTop
            val minH = minOf(curH, bH).coerceAtLeast(1e-6f)
            val overlap = minOf(rowBottom, b.box.bottom) - maxOf(rowTop, b.box.top)

            if (overlap >= 0.35f * minH) {
                rows.last().add(b)
                rowTop = minOf(rowTop, b.box.top)
                rowBottom = maxOf(rowBottom, b.box.bottom)
            } else {
                rows.add(mutableListOf(b))
                rowTop = b.box.top
                rowBottom = b.box.bottom
            }
        }
    }

    return rows.flatMap { row ->
        row.sortedWith(
            compareBy<DetectedBubble> {
                val cx = (it.box.left + it.box.right) / 2f
                if (rtl) -cx else cx
            }.thenBy { it.box.top }.thenBy { it.id }
        )
    }
}

