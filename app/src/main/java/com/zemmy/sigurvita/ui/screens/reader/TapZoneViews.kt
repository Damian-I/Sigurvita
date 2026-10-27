package com.zemmy.sigurvita.ui.screens.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.CustomTapZone
import com.zemmy.sigurvita.core.model.NormPoint
import com.zemmy.sigurvita.core.model.TapAction
import kotlin.math.abs

/** Next = primary, Back = tertiary: distinct hues in every scheme, and they follow dynamic colour. */
@Composable
private fun tapActionColor(action: TapAction): Color =
    if (action == TapAction.NEXT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

private fun List<NormPoint>.toPath(w: Float, h: Float): Path = Path().apply {
    forEachIndexed { i, p -> if (i == 0) moveTo(p.x * w, p.y * h) else lineTo(p.x * w, p.y * h) }
    close()
}

private fun DrawScope.drawZone(points: List<NormPoint>, color: Color, fillAlpha: Float, strokeWidth: Float) {
    val path = points.toPath(size.width, size.height)
    drawPath(path, color.copy(alpha = fillAlpha), style = Fill)
    if (strokeWidth > 0f) drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawZoneLabel(measurer: TextMeasurer, text: String, style: TextStyle, at: NormPoint) {
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        color = Color.White,
        topLeft = Offset(
            at.x * size.width - layout.size.width / 2f,
            at.y * size.height - layout.size.height / 2f
        )
    )
}

/** Fading highlight of the tap regions over the reader. Draws only; it never consumes touches. */
@Composable
internal fun TapZoneOverlay(regions: List<TapRegion>, visible: Boolean, modifier: Modifier = Modifier) {
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "TapZoneOverlayFade")
    val nextColor = tapActionColor(TapAction.NEXT)
    val prevColor = tapActionColor(TapAction.PREVIOUS)
    val nextLabel = stringResource(R.string.tap_zone_next)
    val prevLabel = stringResource(R.string.tap_zone_previous)
    val labelStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val labelPoints = remember(regions) { regions.map { labelPoint(it.points) } }

    if (fade < 0.01f || regions.isEmpty()) return

    Canvas(modifier = modifier.graphicsLayer { alpha = fade }) {
        regions.forEachIndexed { index, region ->
            val isNext = region.action == TapAction.NEXT
            drawZone(region.points, if (isNext) nextColor else prevColor, 0.30f, 2.dp.toPx())
            drawZoneLabel(measurer, if (isNext) nextLabel else prevLabel, labelStyle, labelPoints[index])
        }
    }
}

/** Miniature of a layout, for the picker tiles. */
@Composable
internal fun TapZonePreview(regions: List<TapRegion>, modifier: Modifier = Modifier) {
    val nextColor = tapActionColor(TapAction.NEXT)
    val prevColor = tapActionColor(TapAction.PREVIOUS)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        drawRect(outline, style = Stroke(width = 1.dp.toPx()))
        regions.forEach { region ->
            drawZone(region.points, if (region.action == TapAction.NEXT) nextColor else prevColor, 0.55f, 0f)
        }
    }
}

private enum class EditorTool { SELECT, RECTANGLE, DRAW }

/**
 * Full-screen editor for the custom layout. Zones are stored as screen fractions for left-to-right reading.
 * Select: tap a zone, drag to move, pinch to resize. Rectangle / Draw: create a zone with the chosen action.
 */
@Composable
internal fun TapZoneEditorDialog(
    initial: List<CustomTapZone>,
    onSave: (List<CustomTapZone>) -> Unit,
    onDismiss: () -> Unit
) {
    var zones by remember { mutableStateOf(initial) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var tool by remember { mutableStateOf(EditorTool.RECTANGLE) }
    var newAction by remember { mutableStateOf(TapAction.NEXT) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draft by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val config = LocalConfiguration.current
    val ratio = (config.screenWidthDp.toFloat() / config.screenHeightDp.toFloat()).coerceIn(0.3f, 3f)
    val nextColor = tapActionColor(TapAction.NEXT)
    val prevColor = tapActionColor(TapAction.PREVIOUS)
    val nextLabel = stringResource(R.string.tap_zone_next)
    val prevLabel = stringResource(R.string.tap_zone_previous)
    val labelStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val outline = MaterialTheme.colorScheme.outline
    val canvasBackground = MaterialTheme.colorScheme.surfaceVariant

    val selectedZone = selected?.let { zones.getOrNull(it) }
    val shownAction = if (tool == EditorTool.SELECT && selectedZone != null) selectedZone.action else newAction

    fun setAction(action: TapAction) {
        val idx = selected
        if (tool == EditorTool.SELECT && idx != null) {
            zones = zones.mapIndexed { i, z -> if (i == idx) z.copy(action = action) else z }
        } else {
            newAction = action
        }
    }

    fun addZone(pixelPoints: List<Offset>) {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val pts = pixelPoints.map { NormPoint((it.x / w).coerceIn(0f, 1f), (it.y / h).coerceIn(0f, 1f)) }
        zones = zones + CustomTapZone(newAction, pts)
        selected = zones.lastIndex
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.zone_editor_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.zone_editor_cancel)) }
                    Button(onClick = { onSave(zones) }) { Text(stringResource(R.string.zone_editor_save)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tool == EditorTool.SELECT,
                        onClick = { tool = EditorTool.SELECT; draft = emptyList() },
                        label = { Text(stringResource(R.string.zone_editor_select)) }
                    )
                    FilterChip(
                        selected = tool == EditorTool.RECTANGLE,
                        onClick = { tool = EditorTool.RECTANGLE; draft = emptyList() },
                        label = { Text(stringResource(R.string.zone_editor_rectangle)) }
                    )
                    FilterChip(
                        selected = tool == EditorTool.DRAW,
                        onClick = { tool = EditorTool.DRAW; draft = emptyList() },
                        label = { Text(stringResource(R.string.zone_editor_draw)) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = shownAction == TapAction.NEXT,
                        onClick = { setAction(TapAction.NEXT) },
                        label = { Text(nextLabel) }
                    )
                    FilterChip(
                        selected = shownAction == TapAction.PREVIOUS,
                        onClick = { setAction(TapAction.PREVIOUS) },
                        label = { Text(prevLabel) }
                    )
                    TextButton(
                        enabled = selected != null,
                        onClick = {
                            val idx = selected
                            zones = zones.filterIndexed { i, _ -> i != idx }
                            selected = null
                        }
                    ) { Text(stringResource(R.string.zone_editor_delete)) }
                    TextButton(onClick = { zones = emptyList(); selected = null }) {
                        Text(stringResource(R.string.zone_editor_clear))
                    }
                }
                Text(
                    stringResource(
                        when (tool) {
                            EditorTool.SELECT -> R.string.zone_editor_hint_select
                            EditorTool.RECTANGLE -> R.string.zone_editor_hint_rectangle
                            EditorTool.DRAW -> R.string.zone_editor_hint_draw
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .aspectRatio(ratio)
                            .background(canvasBackground)
                            .border(1.dp, outline)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(tool) {
                                when (tool) {
                                    EditorTool.RECTANGLE -> detectDragGestures(
                                        onDragStart = { draft = listOf(it, it) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val start = draft.firstOrNull() ?: change.position
                                            draft = listOf(start, change.position)
                                        },
                                        onDragEnd = {
                                            if (draft.size >= 2) {
                                                val a = draft.first()
                                                val b = draft.last()
                                                val l = minOf(a.x, b.x)
                                                val r = maxOf(a.x, b.x)
                                                val t = minOf(a.y, b.y)
                                                val bt = maxOf(a.y, b.y)
                                                if (r - l > 24f && bt - t > 24f) {
                                                    addZone(listOf(Offset(l, t), Offset(r, t), Offset(r, bt), Offset(l, bt)))
                                                }
                                            }
                                            draft = emptyList()
                                        },
                                        onDragCancel = { draft = emptyList() }
                                    )
                                    EditorTool.DRAW -> detectDragGestures(
                                        onDragStart = { draft = listOf(it) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val last = draft.lastOrNull()
                                            if (last == null || (change.position - last).getDistance() > 12f) {
                                                draft = draft + change.position
                                            }
                                        },
                                        onDragEnd = {
                                            if (draft.size >= 3) addZone(draft)
                                            draft = emptyList()
                                        },
                                        onDragCancel = { draft = emptyList() }
                                    )
                                    EditorTool.SELECT -> detectTapGestures(
                                        onTap = { pos ->
                                            val w = canvasSize.width.toFloat()
                                            val h = canvasSize.height.toFloat()
                                            if (w > 0f && h > 0f) {
                                                selected = zones.indexOfLast {
                                                    containsPoint(it.points, pos.x / w, pos.y / h)
                                                }.takeIf { it >= 0 }
                                            }
                                        }
                                    )
                                }
                            }
                            .pointerInput(tool) {
                                if (tool == EditorTool.SELECT) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val idx = selected ?: return@detectTransformGestures
                                        val zone = zones.getOrNull(idx) ?: return@detectTransformGestures
                                        val w = canvasSize.width.toFloat()
                                        val h = canvasSize.height.toFloat()
                                        if (w <= 0f || h <= 0f || zoom < 0.05f) return@detectTransformGestures
                                        val cx = zone.points.map { it.x }.average().toFloat()
                                        val cy = zone.points.map { it.y }.average().toFloat()
                                        val moved = zone.points.map { p ->
                                            NormPoint(
                                                (cx + (p.x - cx) * zoom + pan.x / w).coerceIn(0f, 1f),
                                                (cy + (p.y - cy) * zoom + pan.y / h).coerceIn(0f, 1f)
                                            )
                                        }
                                        zones = zones.mapIndexed { i, z -> if (i == idx) z.copy(points = moved) else z }
                                    }
                                }
                            }
                    ) {
                        zones.forEachIndexed { index, zone ->
                            val isNext = zone.action == TapAction.NEXT
                            drawZone(
                                zone.points,
                                if (isNext) nextColor else prevColor,
                                0.35f,
                                if (index == selected) 4.dp.toPx() else 1.5.dp.toPx()
                            )
                            drawZoneLabel(
                                measurer,
                                if (isNext) nextLabel else prevLabel,
                                labelStyle,
                                labelPoint(zone.points)
                            )
                        }
                        if (draft.size >= 2) {
                            val c = if (newAction == TapAction.NEXT) nextColor else prevColor
                            if (tool == EditorTool.RECTANGLE) {
                                val a = draft.first()
                                val b = draft.last()
                                drawRect(
                                    c.copy(alpha = 0.3f),
                                    topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                                    size = Size(abs(b.x - a.x), abs(b.y - a.y))
                                )
                            } else {
                                val p = Path().apply {
                                    draft.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                                }
                                drawPath(p, c, style = Stroke(width = 3.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}