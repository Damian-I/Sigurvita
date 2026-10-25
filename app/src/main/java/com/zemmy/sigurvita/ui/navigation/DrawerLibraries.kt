package com.zemmy.sigurvita.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zemmy.sigurvita.core.model.Library

/** Live state of the drag-to-reorder interaction in the drawer's library list. */
@Stable
class LibraryDragState(initial: List<Library>) {
    var libraries by mutableStateOf(initial)
    var draggingItemId by mutableStateOf<Int?>(null)
    var dragOffsetY by mutableFloatStateOf(0f)
}

/** Follows [libraries] (already in the saved order) except while the user is dragging. */
@Composable
fun rememberLibraryDragState(libraries: List<Library>): LibraryDragState {
    val state = remember { LibraryDragState(libraries) }
    LaunchedEffect(libraries) {
        if (state.draggingItemId == null) state.libraries = libraries
    }
    return state
}

/** Individual libraries with a drag handle (clean live drag to reorder). */
fun LazyListScope.drawerLibraries(
    dragState: LibraryDragState,
    currentLibraryId: Int?,
    itemHeightPx: Float,
    onOpenLibrary: (Int) -> Unit,
    onOrderChanged: (List<Int>) -> Unit
) {
    items(dragState.libraries, key = { it.id }) { library ->
        val isDragging = (dragState.draggingItemId == library.id)
        val isSelected = currentLibraryId == library.id

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 10f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragState.dragOffsetY else 0f
                    shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                }
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .clickable {
                    if (dragState.draggingItemId == null) onOpenLibrary(library.id)
                }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = library.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(library.id) {
                        detectDragGestures(
                            onDragStart = {
                                dragState.draggingItemId = library.id
                                dragState.dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragState.dragOffsetY += dragAmount.y
                                val current = dragState.libraries
                                val currentIdx = current.indexOfFirst { it.id == library.id }
                                if (currentIdx != -1 && itemHeightPx > 0f) {
                                    val steps = (dragState.dragOffsetY / itemHeightPx).toInt()
                                    if (steps != 0) {
                                        val targetIdx = (currentIdx + steps).coerceIn(0, current.size - 1)
                                        if (targetIdx != currentIdx) {
                                            val list = current.toMutableList()
                                            val item = list.removeAt(currentIdx)
                                            list.add(targetIdx, item)
                                            dragState.libraries = list
                                            dragState.dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                onOrderChanged(dragState.libraries.map { it.id })
                                dragState.draggingItemId = null
                                dragState.dragOffsetY = 0f
                            },
                            onDragCancel = {
                                dragState.draggingItemId = null
                                dragState.dragOffsetY = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Drag to Rearrange",
                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
