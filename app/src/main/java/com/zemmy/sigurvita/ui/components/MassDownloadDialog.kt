package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.DownloadFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MassDownloadDialog(
    totalAvailableIssues: Int,
    unreadIssuesCount: Int,
    onDismiss: () -> Unit,
    onConfirmDownload: (count: Int, unreadOnly: Boolean, format: DownloadFormat) -> Unit
) {
    var unreadOnly by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf(DownloadFormat.CBZ) }
    val maxAvailable = if (unreadOnly) unreadIssuesCount else totalAvailableIssues

    val rawPresetCounts = listOf(
        1, 3, 5, 10, 15, 20, 25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000, maxAvailable
    )
    val presetCounts = remember(maxAvailable) {
        rawPresetCounts.filter { it in 1..maxAvailable }.distinct().sorted()
    }

    var selectedCount by remember(presetCounts) {
        mutableIntStateOf(presetCounts.lastOrNull() ?: maxAvailable)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.mass_download_issues),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.unread_only),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (unreadOnly) stringResource(R.string.unread_issues_count, unreadIssuesCount) else stringResource(R.string.all_issues_count, totalAvailableIssues),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = unreadOnly,
                        onCheckedChange = { unreadOnly = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Download File Format",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DownloadFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = (selectedFormat == fmt),
                            onClick = { selectedFormat = fmt },
                            label = { Text(if (fmt == DownloadFormat.CBZ) "CBZ Archive" else "Images") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.select_quantity_to_download),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetCounts.forEach { count ->
                        FilterChip(
                            selected = (selectedCount == count),
                            onClick = { selectedCount = count },
                            label = { Text(if (count == maxAvailable) stringResource(R.string.all_count, count) else count.toString()) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDownload(selectedCount, unreadOnly, selectedFormat)
                    onDismiss()
                },
                enabled = selectedCount > 0
            ) {
                Text(stringResource(R.string.download_count, selectedCount))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
