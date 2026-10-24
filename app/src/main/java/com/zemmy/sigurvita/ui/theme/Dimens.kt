package com.zemmy.sigurvita.ui.theme

import androidx.compose.ui.unit.dp

/** Shared spacing scale (4dp grid). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Standard screen horizontal padding. */
    val screenH = 16.dp

    /** Standard gap between a section header and its content. */
    val sectionGap = 12.dp

    /** Vertical gap between major sections. */
    val sectionSpacing = 28.dp
}

/** Shared component dimensions. */
object Dimens {
    /** Poster aspect ratio (width / height). */
    const val CoverAspect = 0.68f

    /** Wide hero/banner aspect ratio. */
    const val HeroAspect = 1.65f

    /** Minimum touch target. */
    val TouchTarget = 48.dp

    /** Cover card corner radius. */
    val CoverRadius = Radius.sm

    /** Height of a cover thumbnail in list rows. */
    val RowThumbHeight = 92.dp

    /** Reader top chrome height. */
    val ReaderChromeHeight = 64.dp

    /** Reader thumbnail strip item size. */
    val ReaderThumbHeight = 56.dp
}
