package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto
import com.zemmy.sigurvita.core.data.repository.ColorScapeKind
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.ServerConfig
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import com.zemmy.sigurvita.ui.theme.accent
import com.zemmy.sigurvita.ui.theme.headerBrush
import com.zemmy.sigurvita.ui.theme.isSyntheticPlaceholder
import com.zemmy.sigurvita.ui.theme.onAccent
import com.zemmy.sigurvita.ui.theme.rememberColorScape
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailBottomSheet(
    chapter: Chapter,
    seriesName: String,
    seriesCoverUrl: String? = null,
    metadata: SeriesMetadataDto? = null,
    onReadClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    onToggleRead: (chapter: Chapter) -> Unit,
    onDownloadClick: (chapter: Chapter) -> Unit,
    onDeleteDownloadClick: (chapterId: Int) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val prefs = SigurvitaApp.instance.userPreferencesRepository
    val config by prefs.serverConfigFlow.collectAsState(initial = ServerConfig())
    val colors = rememberColorScape(ColorScapeKind.CHAPTER, if (chapter.isSyntheticPlaceholder()) 0 else chapter.id)
    val accent = colors?.accent()
    val displayTitle = formatChapterDisplayTitle(chapter.title, chapter.chapterNumber)
    val files = chapter.files
    val totalSizeBytes = files.sumOf { it.bytes }
    val formatExtension = files.firstOrNull { !it.extension.isNullOrBlank() }?.extension?.uppercase(Locale.ROOT)
        ?: if (chapter.localPath?.endsWith(".cbz", true) == true) "CBZ" else "ARCHIVE"

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (colors != null) Modifier.background(colors.headerBrush()) else Modifier)
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
                        tint = accent ?: MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Issue Details",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Info Row: Cover + Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                CoverImage(
                    url = chapter.coverUrl ?: seriesCoverUrl,
                    modifier = Modifier
                        .width(100.dp)
                        .height(145.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent ?: MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ReadStatusBadge(
                        isRead = chapter.isRead,
                        isNextUp = false,
                        onClick = { onToggleRead(chapter) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${chapter.pagesRead} / ${chapter.pages} pages read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary Section
            val summaryText = chapter.summary?.takeIf { it.isNotBlank() }
                ?: metadata?.summary?.takeIf { it.isNotBlank() }
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Characters, Teams & Creators
            metadata?.let { meta ->
                PersonRowSection(
                    title = "Characters",
                    people = meta.characters,
                    serverUrl = config.serverUrl,
                    apiKey = config.apiKey
                )

                PersonRowSection(
                    title = "Teams",
                    people = meta.teams,
                    serverUrl = config.serverUrl,
                    apiKey = config.apiKey
                )

                PersonRowSection(
                    title = "Writers & Creators",
                    people = (meta.writers.orEmpty() + meta.pencillers.orEmpty() + meta.inkers.orEmpty() + meta.colorists.orEmpty()).distinctBy { it.name },
                    serverUrl = config.serverUrl,
                    apiKey = config.apiKey
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // File & Technical Details Section
            Text(
                text = "File & Archive Details",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Format: $formatExtension",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Total Pages: ${chapter.pages}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (totalSizeBytes > 0L) {
                        val sizeMb = totalSizeBytes / (1024f * 1024f)
                        val sizeGb = sizeMb / 1024f
                        val formattedSize = if (sizeGb >= 1.0f) String.format(Locale.ROOT, "%.2f GB", sizeGb) else String.format(Locale.ROOT, "%.1f MB", sizeMb)
                        Text(
                            text = "File Size: $formattedSize",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (chapter.isDownloaded && !chapter.localPath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Downloaded Location:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = chapter.localPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val serverPath = files.firstOrNull { !it.filePath.isNullOrBlank() }?.filePath
                        if (serverPath != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Server File Path:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = serverPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onDismissRequest()
                        onReadClick(chapter.id, chapter.pages, chapter.volumeId)
                    },
                    colors = accent?.let { ButtonDefaults.buttonColors(containerColor = it, contentColor = it.onAccent()) }
                        ?: ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (chapter.pagesRead > 0 && !chapter.isRead) "Resume Issue" else "Read Issue")
                }

                if (chapter.isDownloaded) {
                    OutlinedButton(
                        onClick = { onDeleteDownloadClick(chapter.id) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Local Download",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDownloadClick(chapter) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Issue"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}
