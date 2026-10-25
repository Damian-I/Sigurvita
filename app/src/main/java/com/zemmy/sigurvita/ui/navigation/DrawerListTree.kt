package com.zemmy.sigurvita.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.util.ReadingListEraGroup
import com.zemmy.sigurvita.ui.components.SidebarReadingListIndicator

/** Reading lists as an Era -> collapsible Part -> Subparts tree. */
fun LazyListScope.drawerReadingListTree(
    hierarchy: List<ReadingListEraGroup>,
    expandedEras: Set<String>,
    expandedParts: Set<String>,
    onToggleEra: (String) -> Unit,
    onTogglePart: (String) -> Unit,
    onOpenReadingList: (Int) -> Unit
) {
    hierarchy.forEach { eraGroup ->
        val isEraExpanded = expandedEras.contains(eraGroup.eraName)
        item(key = "era_${eraGroup.eraName}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleEra(eraGroup.eraName) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isEraExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${eraGroup.eraName} (${eraGroup.parts.sumOf { it.subparts.size }})",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (isEraExpanded) {
            eraGroup.parts.forEach { partGroup ->
                val partKey = "${eraGroup.eraName}_${partGroup.partName}"
                val isPartExpanded = expandedParts.contains(partKey)
                item(key = "part_$partKey") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePart(partKey) }
                            .padding(start = 32.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPartExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${partGroup.partDisplayName} (${partGroup.subparts.size})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isPartExpanded) {
                    items(partGroup.subparts, key = { sub -> sub.list.id }) { sub ->
                        val rl = sub.list
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenReadingList(rl.id) }
                                .padding(start = 56.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "• ${sub.subpartDisplayTitle}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                SidebarReadingListIndicator(
                                    pagesRead = rl.pagesRead,
                                    totalPages = rl.totalPages,
                                    issuesRead = rl.issuesRead,
                                    totalIssues = if (rl.itemCount > 0) rl.itemCount else 0
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
