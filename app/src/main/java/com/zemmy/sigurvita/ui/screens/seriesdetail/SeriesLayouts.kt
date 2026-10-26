package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.core.data.repository.ColorScapeKind
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.DownloadStatus
import com.zemmy.sigurvita.core.model.DownloadTask
import com.zemmy.sigurvita.core.model.Volume
import com.zemmy.sigurvita.core.util.formatChapterDisplayTitle
import com.zemmy.sigurvita.ui.components.CoverImage
import com.zemmy.sigurvita.ui.components.IssueGridCard
import com.zemmy.sigurvita.ui.components.IssueRow
import com.zemmy.sigurvita.ui.components.IssueUi
import com.zemmy.sigurvita.ui.components.ReadStatusBadge
import com.zemmy.sigurvita.ui.components.ReadingListBadgeRow
import com.zemmy.sigurvita.ui.components.toListBadge
import com.zemmy.sigurvita.ui.theme.ColorScapeColors
import com.zemmy.sigurvita.ui.theme.accent
import com.zemmy.sigurvita.ui.theme.colorScapeBorder
import com.zemmy.sigurvita.ui.theme.isSyntheticPlaceholder
import com.zemmy.sigurvita.ui.theme.rememberColorScape
import kotlin.math.abs

/** "Volume 3", or "Issues" for the 100000 sentinel Kavita uses for loose issues. */
internal fun formatVolumeHeader(name: String, number: Float): String {
    if (name.contains("100000") || abs(number) >= 10000f) {
        return "Issues"
    }
    return if (name.isNotBlank()) "Volume $name" else "Volume $number"
}

@Composable
private fun rememberItemColorScape(item: SeriesItem): ColorScapeColors? = when (item) {
    is SeriesItem.VolumeItem -> rememberColorScape(ColorScapeKind.VOLUME, item.volumeId)
    is SeriesItem.ChapterItem -> rememberColorScape(
        ColorScapeKind.CHAPTER,
        if (item.chapter.isSyntheticPlaceholder()) 0 else item.chapter.id
    )
}

private fun isNextUp(item: SeriesItem, nextToReadChapterId: Int?): Boolean = when (item) {
    is SeriesItem.ChapterItem -> item.chapter.id == nextToReadChapterId
    is SeriesItem.VolumeItem -> item.firstChapterId == nextToReadChapterId
}

private fun openSeriesItem(item: SeriesItem, onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit) {
    when (item) {
        is SeriesItem.VolumeItem -> {
            if (item.firstChapterId > 0) {
                onChapterClick(item.firstChapterId, item.pages, item.volumeId)
            }
        }
        is SeriesItem.ChapterItem -> {
            onChapterClick(item.chapter.id, item.chapter.pages, item.volumeId)
        }
    }
}

/** A volume stands for its first chapter when it is downloaded or toggled. */
private fun SeriesItem.representativeChapter(): Chapter? = when (this) {
    is SeriesItem.ChapterItem -> chapter
    is SeriesItem.VolumeItem -> volume.chapters.firstOrNull()
}

private fun seriesItemKey(item: SeriesItem) = "${item.javaClass.simpleName}_${item.id}"

@Composable
internal fun EmptyResults(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No results",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

// ----------------------------------------------------------------------------- manga (volumes + chapters)

@Composable
fun MangaItemsGrid(
    seriesItems: List<SeriesItem>,
    seriesCoverUrl: String?,
    nextToReadChapterId: Int?,
    chapterReadingLists: Map<Int, List<Pair<Int, String>>>,
    onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    onDownloadChapter: (Chapter) -> Unit,
    onEraClick: (eraName: String) -> Unit,
    onReadingListClick: (readingListId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        items(seriesItems, key = { item -> seriesItemKey(item) }) { item ->
            val nextUp = isNextUp(item, nextToReadChapterId)
            val readingLists = if (item is SeriesItem.ChapterItem) chapterReadingLists[item.chapter.id] else null
            val itemColors = rememberItemColorScape(item)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openSeriesItem(item, onChapterClick) },
                border = colorScapeBorder(nextUp, itemColors?.accent()),
                elevation = CardDefaults.cardElevation(defaultElevation = if (nextUp) 4.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverImage(
                        url = item.coverUrl ?: seriesCoverUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!readingLists.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        ReadingListBadgeRow(
                            badges = readingLists.map { (listId, listTitle) -> toListBadge(listId, listTitle) },
                            onEraClick = onEraClick,
                            onReadingListClick = onReadingListClick
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (item.isRead) "• Read" else if (nextUp) "▶ Next" else "Unread",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant else if (nextUp) com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.nextUp else MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { item.representativeChapter()?.let(onDownloadChapter) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                contentDescription = null,
                                tint = if (item.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MangaItemsList(
    seriesItems: List<SeriesItem>,
    seriesCoverUrl: String?,
    nextToReadChapterId: Int?,
    chapterReadingLists: Map<Int, List<Pair<Int, String>>>,
    onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    onShowDetail: (Chapter) -> Unit,
    onToggleChapterRead: (Chapter) -> Unit,
    onDownloadChapter: (Chapter) -> Unit,
    onEraClick: (eraName: String) -> Unit,
    onReadingListClick: (readingListId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        items(seriesItems, key = { item -> seriesItemKey(item) }) { item ->
            val nextUp = isNextUp(item, nextToReadChapterId)
            val readingLists = if (item is SeriesItem.ChapterItem) chapterReadingLists[item.chapter.id] else null
            val itemColors = rememberItemColorScape(item)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openSeriesItem(item, onChapterClick) },
                border = colorScapeBorder(nextUp, itemColors?.accent()),
                elevation = CardDefaults.cardElevation(defaultElevation = if (nextUp) 4.dp else 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverImage(
                        url = item.coverUrl ?: seriesCoverUrl,
                        modifier = Modifier
                            .width(60.dp)
                            .height(85.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { item.representativeChapter()?.let(onShowDetail) }
                        )
                        if (!item.subtitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subtitle!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!readingLists.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            ReadingListBadgeRow(
                                badges = readingLists.map { (listId, listTitle) -> toListBadge(listId, listTitle) },
                                onEraClick = onEraClick,
                                onReadingListClick = onReadingListClick
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val showProgress = item.pagesRead > 0 && item.pages > 0 && item.pagesRead < item.pages && !item.isRead
                            if (showProgress) {
                                Text(
                                    text = "${item.pagesRead} / ${item.pages} pages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            ReadStatusBadge(
                                isRead = item.isRead,
                                isNextUp = nextUp,
                                onClick = { item.representativeChapter()?.let(onToggleChapterRead) }
                            )
                        }
                    }

                    IconButton(
                        onClick = { item.representativeChapter()?.let(onDownloadChapter) }
                    ) {
                        Icon(
                            imageVector = if (item.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Download",
                            tint = if (item.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------- comics (volumes -> issues)

@Composable
private fun rememberIssueUi(
    chapter: Chapter,
    downloadTask: DownloadTask?,
    isNextUp: Boolean,
    readingLists: List<Pair<Int, String>>?,
    seriesCoverUrl: String?
): IssueUi = remember(chapter, downloadTask, isNextUp, readingLists, seriesCoverUrl) {
    IssueUi(
        id = chapter.id,
        title = formatChapterDisplayTitle(chapter.title, chapter.chapterNumber),
        coverUrl = chapter.coverUrl ?: seriesCoverUrl,
        pages = chapter.pages,
        pagesRead = chapter.pagesRead,
        isRead = chapter.isRead,
        isNextUp = isNextUp,
        badges = readingLists?.map { (id, title) -> toListBadge(id, title) }.orEmpty(),
        download = downloadTask,
        isDownloaded = chapter.isDownloaded || downloadTask?.status == DownloadStatus.COMPLETED
    )
}

@Composable
fun ComicIssuesGrid(
    volumes: List<Volume>,
    seriesCoverUrl: String?,
    nextToReadChapterId: Int?,
    downloadTasks: Map<Int, DownloadTask>,
    chapterReadingLists: Map<Int, List<Pair<Int, String>>>,
    onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    onDownloadChapter: (Chapter) -> Unit,
    onCancelDownload: (chapterId: Int) -> Unit,
    onDeleteLocal: (chapterId: Int) -> Unit,
    onEraClick: (eraName: String) -> Unit,
    onReadingListClick: (readingListId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        volumes.forEach { volume ->
            if (volumes.size > 1) {
                item(span = { GridItemSpan(3) }) {
                    Text(
                        text = formatVolumeHeader(volume.name, volume.number),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                    )
                }
            }
            items(volume.chapters, key = { it.id }) { chapter ->
                val downloadTask = downloadTasks[chapter.id]
                val nextUp = (chapter.id == nextToReadChapterId)
                val issue = rememberIssueUi(chapter, downloadTask, nextUp, chapterReadingLists[chapter.id], seriesCoverUrl)
                val accent = rememberColorScape(ColorScapeKind.CHAPTER, chapter.id)?.accent()
                IssueGridCard(
                    issue = issue,
                    accent = accent,
                    onOpen = { onChapterClick(chapter.id, chapter.pages, volume.id) },
                    onDownload = { onDownloadChapter(chapter) },
                    onCancelDownload = { onCancelDownload(chapter.id) },
                    onDeleteLocal = { onDeleteLocal(chapter.id) },
                    onEraClick = onEraClick,
                    onReadingListClick = onReadingListClick
                )
            }
        }
    }
}

@Composable
fun ComicIssuesList(
    volumes: List<Volume>,
    seriesCoverUrl: String?,
    nextToReadChapterId: Int?,
    downloadTasks: Map<Int, DownloadTask>,
    chapterReadingLists: Map<Int, List<Pair<Int, String>>>,
    onChapterClick: (chapterId: Int, totalPages: Int, volumeId: Int) -> Unit,
    onShowDetail: (Chapter) -> Unit,
    onToggleChapterRead: (Chapter) -> Unit,
    onDownloadChapter: (Chapter) -> Unit,
    onCancelDownload: (chapterId: Int) -> Unit,
    onDeleteLocal: (chapterId: Int) -> Unit,
    onEraClick: (eraName: String) -> Unit,
    onReadingListClick: (readingListId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        volumes.forEach { volume ->
            if (volumes.size > 1) {
                item {
                    Text(
                        text = formatVolumeHeader(volume.name, volume.number),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                    )
                }
            }
            items(volume.chapters, key = { it.id }) { chapter ->
                val downloadTask = downloadTasks[chapter.id]
                val nextUp = (chapter.id == nextToReadChapterId)
                val issue = rememberIssueUi(chapter, downloadTask, nextUp, chapterReadingLists[chapter.id], seriesCoverUrl)
                val accent = rememberColorScape(ColorScapeKind.CHAPTER, chapter.id)?.accent()
                IssueRow(
                    issue = issue,
                    accent = accent,
                    onOpen = { onChapterClick(chapter.id, chapter.pages, volume.id) },
                    onTitleClick = { onShowDetail(chapter) },
                    onToggleRead = { onToggleChapterRead(chapter) },
                    onDownload = { onDownloadChapter(chapter) },
                    onCancelDownload = { onCancelDownload(chapter.id) },
                    onDeleteLocal = { onDeleteLocal(chapter.id) },
                    onEraClick = onEraClick,
                    onReadingListClick = onReadingListClick
                )
            }
        }
    }
}
