package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zemmy.sigurvita.ui.theme.SigurvitaTheme
import com.zemmy.sigurvita.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesReadIndicator(
    pagesRead: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    issuesRead: Int = 0,
    totalIssues: Int = 0,
    isRead: Boolean = totalPages in 1..pagesRead,
    showProgressBar: Boolean = true,
    compact: Boolean = false
) {
    val progress = if (totalPages > 0) (pagesRead.toFloat() / totalPages).coerceIn(0f, 1f) else 0f
    val isPartiallyRead = (pagesRead > 0 || issuesRead > 0) && !isRead
    val colors = SigurvitaTheme.colors
    val countStyle = if (compact) {
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.sp
        )
    } else {
        MaterialTheme.typography.labelSmall
    }

    val issuePart = if (totalIssues > 0) {
        if (isRead) "#$totalIssues issues"
        else if (isPartiallyRead) "$issuesRead/$totalIssues issues"
        else "#$totalIssues issues"
    } else ""

    val pagePart = if (totalPages > 0) {
        if (isRead) ""
        else if (isPartiallyRead) "$pagesRead/$totalPages pgs"
        else "$totalPages pgs"
    } else ""

    // Compact cards only have ~90-110dp of content width, so the worded labels
    // ("10/134 issues 258/3129 pgs") wrap onto a second line and make cards uneven.
    // Compact mode stays on one fixed line by pairing the ratios with small icons
    // (book = issues, page = pages) instead of dropping the labels entirely.
    val issueCount = when {
        totalIssues <= 0 -> ""
        isPartiallyRead -> "$issuesRead/$totalIssues"
        else -> "$totalIssues"
    }
    val pageCount = when {
        totalPages <= 0 || isRead -> ""
        isPartiallyRead -> "$pagesRead/$totalPages"
        else -> "$totalPages"
    }

    val statusColor = when {
        isRead -> colors.completed
        isPartiallyRead -> colors.inProgress
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textWeight = if (isRead || isPartiallyRead) FontWeight.SemiBold else FontWeight.Normal

    val countDescription = if (isRead) {
        "Read"
    } else {
        buildList {
            if (totalIssues > 0) {
                add(if (isPartiallyRead) "$issuesRead of $totalIssues issues" else "$totalIssues issues")
            }
            if (totalPages > 0) {
                add(if (isPartiallyRead) "$pagesRead of $totalPages pages" else "$totalPages pages")
            }
        }.joinToString(", ").ifEmpty { "Unread" }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = countDescription }
        ) {
            if (compact) {
                when {
                    isRead -> Text(
                        text = "Read",
                        style = countStyle,
                        color = statusColor,
                        fontWeight = textWeight,
                        maxLines = 1
                    )
                    issueCount.isNotEmpty() -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = issueCount,
                            style = countStyle,
                            color = statusColor,
                            fontWeight = textWeight,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (pageCount.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = pageCount,
                                style = countStyle,
                                color = statusColor,
                                fontWeight = textWeight,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    pageCount.isNotEmpty() -> {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = pageCount,
                            style = countStyle,
                            color = statusColor,
                            fontWeight = textWeight,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> Text(
                        text = "Unread",
                        style = countStyle,
                        color = statusColor,
                        fontWeight = textWeight,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = when {
                        isRead -> if (issuePart.isNotEmpty()) "Read ($issuePart)" else "Read"
                        isPartiallyRead -> issuePart.ifEmpty { "$pagesRead pgs" }
                        else -> issuePart.ifEmpty { "Unread" }
                    },
                    style = countStyle,
                    color = statusColor,
                    fontWeight = textWeight,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (pagePart.isNotEmpty()) {
                    Text(
                        text = pagePart,
                        style = countStyle,
                        color = statusColor,
                        fontWeight = textWeight,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (showProgressBar && totalPages > 0) {
            Spacer(modifier = Modifier.height(Spacing.xxs))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 2.dp else 3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isRead) SigurvitaTheme.colors.completed else colors.inProgress,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarReadingListIndicator(
    pagesRead: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    issuesRead: Int = 0,
    totalIssues: Int = 0
) {
    if (totalPages <= 0 && totalIssues <= 0) return
    val isRead = (totalPages in 1..pagesRead) || (totalIssues in 1..issuesRead)
    val isPartiallyRead = (pagesRead > 0 || issuesRead > 0) && !isRead
    val progress = if (totalPages > 0) (pagesRead.toFloat() / totalPages).coerceIn(0f, 1f) else 0f
    val colors = SigurvitaTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = Spacing.xxs)
    ) {
        if (isRead) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Read",
                tint = colors.completed,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = if (totalIssues > 0) "READ ($totalIssues issues)" else "READ",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.completed
            )
        } else if (isPartiallyRead) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = colors.inProgress,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            val label = if (totalIssues > 0) "#$issuesRead/$totalIssues issues" else "${(progress * 100).toInt()}%"
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.inProgress,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
