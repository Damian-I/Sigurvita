package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.util.cleanReadingListTitle
import com.zemmy.sigurvita.core.util.extractEraInfo

@Immutable
data class ListBadge(val listId: Int, val eraName: String, val partName: String)

fun toListBadge(listId: Int, listTitle: String): ListBadge {
    val eraName = extractEraInfo(listTitle).name
    val cleanTitle = cleanReadingListTitle(listTitle)
    val partName = cleanTitle.removePrefix(eraName).trimStart('-', ':', '•', ' ').ifBlank { cleanTitle }
    return ListBadge(listId, eraName, partName)
}

@Composable
fun ReadingListBadgeRow(
    badges: List<ListBadge>,
    onEraClick: (String) -> Unit,
    onReadingListClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (badges.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        badges.forEach { badge ->
            IntegratedReadingListBadge(
                eraName = badge.eraName,
                partName = badge.partName,
                listId = badge.listId,
                onEraClick = onEraClick,
                onReadingListClick = onReadingListClick,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
fun IntegratedReadingListBadge(
    eraName: String,
    partName: String,
    listId: Int,
    onEraClick: (String) -> Unit,
    onReadingListClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val showEra = eraName.isNotBlank() && eraName != "General"
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showEra) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onEraClick(eraName) }
                ) {
                    Text(
                        text = eraName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.clickable { onReadingListClick(listId) }
            ) {
                Text(
                    text = partName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
