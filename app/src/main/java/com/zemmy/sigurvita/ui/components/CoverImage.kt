package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.zemmy.sigurvita.ui.theme.Spacing

fun coverImageRequest(context: android.content.Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(coil.size.Size.ORIGINAL)
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .crossfade(false)
        .build()

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = MaterialTheme.shapes.small
) {
    val context = LocalContext.current
    if (!url.isNullOrBlank()) {
        val painter = rememberAsyncImagePainter(
            model = remember(url) { coverImageRequest(context, url) }
        )
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
            CoverPlaceholder(title = contentDescription, modifier = modifier, shape = shape)
        } else {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = modifier.clip(shape),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        CoverPlaceholder(title = contentDescription, modifier = modifier, shape = shape)
    }
}

@Composable
fun CoverPlaceholder(
    title: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(Spacing.sm)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                if (!title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
