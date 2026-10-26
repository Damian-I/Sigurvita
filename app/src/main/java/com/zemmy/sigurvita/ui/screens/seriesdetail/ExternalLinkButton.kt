package com.zemmy.sigurvita.ui.screens.seriesdetail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.util.ExternalLinkProvider
import com.zemmy.sigurvita.core.util.SeriesExternalLink

/** Label, official brand artwork, size and corner shape for each external-link provider. */
private class LinkArt(
    @StringRes val label: Int,
    @DrawableRes val logo: Int,
    val size: Dp,
    val shape: Shape
)

private fun ExternalLinkProvider.art(): LinkArt = when (this) {
    ExternalLinkProvider.MANGABAKA -> LinkArt(R.string.open_mangabaka, R.drawable.mangabaka_logo, 22.dp, CircleShape)
    ExternalLinkProvider.COMIC_VINE -> LinkArt(R.string.open_comic_vine, R.drawable.comic_vine_logo, 24.dp, RoundedCornerShape(4.dp))
}

/** Opens the series' MangaBaka / Comic Vine page. Shows the provider's own artwork (raster, keeps its colours). */
@Composable
fun ExternalLinkButton(link: SeriesExternalLink, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val art = link.provider.art()
    IconButton(
        onClick = { runCatching { uriHandler.openUri(link.url) } },
        modifier = modifier.size(36.dp)
    ) {
        Image(
            painter = painterResource(art.logo),
            contentDescription = stringResource(art.label),
            modifier = Modifier
                .size(art.size)
                .clip(art.shape)
        )
    }
}
