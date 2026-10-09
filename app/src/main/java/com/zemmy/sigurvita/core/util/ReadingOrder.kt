package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.DetectedBubble

private data class PanelCluster(
    val id: Int,
    val bubbles: MutableList<DetectedBubble> = mutableListOf()
) {
    val minTop: Float get() = bubbles.minOf { it.box.top }
    val minLeft: Float get() = bubbles.minOf { it.box.left }
    val maxBottom: Float get() = bubbles.maxOf { it.box.bottom }
    val height: Float get() = maxBottom - minTop
}

fun sortBubblesForReading(
    bubbles: List<DetectedBubble>,
    rtl: Boolean
): List<DetectedBubble> {
    if (bubbles.size <= 1) return bubbles

    val maxGutterX = 0.15f
    val maxGutterY = 0.15f

    // 1. Union-Find to group bubbles into Panels
    val parent = IntArray(bubbles.size) { it }
    fun find(i: Int): Int {
        var root = i
        while (root != parent[root]) root = parent[root]
        var curr = i
        while (curr != root) {
            val nxt = parent[curr]
            parent[curr] = root
            curr = nxt
        }
        return root
    }
    fun union(i: Int, j: Int) {
        val rootI = find(i)
        val rootJ = find(j)
        if (rootI != rootJ) parent[rootI] = rootJ
    }

    val byTop = bubbles.sortedWith(compareBy({ it.box.top }, { it.id }))
    for (i in byTop.indices) {
        val b1 = byTop[i].box
        for (j in i + 1 until byTop.size) {
            val b2 = byTop[j].box
            val gapX = maxOf(0f, maxOf(b1.left - b2.right, b2.left - b1.right))
            val gapY = maxOf(0f, maxOf(b1.top - b2.bottom, b2.top - b1.bottom))

            if (gapX <= maxGutterX && gapY <= maxGutterY) {
                union(i, j)
            }
        }
    }

    val clusterMap = mutableMapOf<Int, PanelCluster>()
    for (i in bubbles.indices) {
        val root = find(i)
        clusterMap.getOrPut(root) { PanelCluster(id = root) }.bubbles.add(bubbles[i])
    }

    // 2. Group Panels into Horizontal Tiers (rows of panels)
    val panelsByTop = clusterMap.values.sortedWith(compareBy({ it.minTop }, { it.id }))
    val panelTiers = mutableListOf<MutableList<PanelCluster>>()
    var tierTop = 0f
    var tierBottom = 0f

    for (p in panelsByTop) {
        val overlap = minOf(tierBottom, p.maxBottom) - maxOf(tierTop, p.minTop)
        val minH = minOf(tierBottom - tierTop, p.height)
        if (panelTiers.isNotEmpty() && overlap >= 0.35f * minH) {
            panelTiers.last().add(p)
            tierTop = minOf(tierTop, p.minTop)
            tierBottom = maxOf(tierBottom, p.maxBottom)
        } else {
            panelTiers.add(mutableListOf(p))
            tierTop = p.minTop
            tierBottom = p.maxBottom
        }
    }

    // 3. Sort panels within each Tier by horizontal position (LTR / RTL)
    val sortedPanels = panelTiers.flatMap { tier ->
        tier.sortedWith(
            compareBy<PanelCluster> {
                if (rtl) -it.minLeft else it.minLeft
            }.thenBy { it.id }
        )
    }

    // 4. Sort bubbles within each Panel by row-banding and flatten
    val result = mutableListOf<DetectedBubble>()
    for (panel in sortedPanels) {
        val panelBubblesByTop = panel.bubbles.sortedWith(compareBy({ it.box.top }, { it.id }))
        val bubbleRows = mutableListOf<MutableList<DetectedBubble>>()
        var bRowTop = 0f
        var bRowBottom = 0f

        for (b in panelBubblesByTop) {
            val overlap = minOf(bRowBottom, b.box.bottom) - maxOf(bRowTop, b.box.top)
            val minH = minOf(bRowBottom - bRowTop, b.box.bottom - b.box.top)
            if (bubbleRows.isNotEmpty() && overlap >= 0.40f * minH) {
                bubbleRows.last().add(b)
                bRowTop = minOf(bRowTop, b.box.top)
                bRowBottom = maxOf(bRowBottom, b.box.bottom)
            } else {
                bubbleRows.add(mutableListOf(b))
                bRowTop = b.box.top
                bRowBottom = b.box.bottom
            }
        }

        val sortedPanelBubbles = bubbleRows.flatMap { row ->
            row.sortedWith(
                compareBy<DetectedBubble> {
                    val cx = (it.box.left + it.box.right) / 2f
                    if (rtl) -cx else cx
                }.thenBy { it.id }
            )
        }

        result.addAll(sortedPanelBubbles)
    }

    return result
}
