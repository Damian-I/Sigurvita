package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.data.api.dto.PersonDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeriesInfoBottomSheet(
    series: Series?,
    metadata: SeriesMetadataDto?,
    volumes: List<Volume>,
    libraryName: String,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Series Details & Files",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Series Name & Localized Name
            if (series != null) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (!series.localizedName.isNullOrBlank()) {
                    Text(
                        text = series.localizedName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Library: $libraryName",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary
            val summaryText = metadata?.summary?.takeIf { it.isNotBlank() }
                ?: series?.summary?.takeIf { it.isNotBlank() }
            if (summaryText != null) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // People / Credits
            metadata?.let { meta ->
                PersonSection(title = "Writers", people = meta.writers)
                PersonSection(title = "Characters", people = meta.characters)
                PersonSection(title = "Cover Artists", people = meta.coverArtists)
                PersonSection(title = "Pencillers", people = meta.pencillers)
                PersonSection(title = "Inkers", people = meta.inkers)
                PersonSection(title = "Colorists", people = meta.colorists)
                PersonSection(title = "Publishers", people = meta.publishers)
                PersonSection(title = "Teams", people = meta.teams)
                PersonSection(title = "Locations", people = meta.locations)

                // Genres & Tags
                val genres = meta.genres?.map { it.title }?.filter { it.isNotBlank() } ?: emptyList()
                val tags = meta.tags?.map { it.title }?.filter { it.isNotBlank() } ?: emptyList()

                if (genres.isNotEmpty() || tags.isNotEmpty() || meta.releaseYear != null || meta.language != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Metadata & Tags",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        meta.releaseYear?.let { year ->
                            AssistChip(onClick = {}, label = { Text("Year: $year") })
                        }
                        meta.language?.takeIf { it.isNotBlank() }?.let { lang ->
                            AssistChip(onClick = {}, label = { Text("Lang: $lang") })
                        }
                        genres.forEach { genre ->
                            AssistChip(onClick = {}, label = { Text("Genre: $genre") })
                        }
                        tags.forEach { tag ->
                            AssistChip(onClick = {}, label = { Text("#$tag") })
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // File Details Section
            val allChapters = volumes.flatMap { it.chapters }
            val allFiles = allChapters.flatMap { it.files }
            val totalSizeBytes = allFiles.sumOf { it.bytes }
            val totalFiles = allFiles.size
            val fileFormats = allFiles.mapNotNull { it.extension?.uppercase(Locale.ROOT) }.distinct()

            Text(
                text = "File & Archive Details",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Total Issues / Chapters: ${allChapters.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (totalFiles > 0) {
                    Text(
                        text = "Total Files: $totalFiles",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (totalSizeBytes > 0L) {
                    val sizeMb = totalSizeBytes / (1024f * 1024f)
                    val sizeGb = sizeMb / 1024f
                    val formattedSize = if (sizeGb >= 1.0f) String.format(Locale.ROOT, "%.2f GB", sizeGb) else String.format(Locale.ROOT, "%.1f MB", sizeMb)
                    Text(
                        text = "Total Size: $formattedSize",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (fileFormats.isNotEmpty()) {
                    Text(
                        text = "Formats: ${fileFormats.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val firstFilePath = allFiles.firstOrNull { !it.filePath.isNullOrBlank() }?.filePath
                if (firstFilePath != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sample File Path:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = firstFilePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonSection(title: String, people: List<PersonDto>?) {
    val names = people?.map { it.name }?.filter { it.isNotBlank() } ?: emptyList()
    if (names.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$title: ${names.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
