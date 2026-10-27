package com.zemmy.sigurvita.ui.screens.reader

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.get
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.zemmy.sigurvita.core.model.ReaderBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ReaderGray = Color(0xFF7A7A7A)

/** The fixed colour for this choice, or null for [ReaderBackground.AUTO] (decided per page). */
fun ReaderBackground.fixedColor(): Color? = when (this) {
    ReaderBackground.BLACK -> Color.Black
    ReaderBackground.GRAY -> ReaderGray
    ReaderBackground.WHITE -> Color.White
    ReaderBackground.AUTO -> null
}

/**
 * Estimates the colour of a page's margin: decodes a small copy, samples a thin ring of pixels just
 * inside its edges and takes the median of each channel (so artwork touching the edge does not skew it).
 * Returns null if the image cannot be read.
 */
suspend fun estimatePageEdgeColor(context: Context, url: String): Color? = withContext(Dispatchers.Default) {
    try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(256)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return@withContext null

        val inset = 2
        val reds = ArrayList<Int>()
        val greens = ArrayList<Int>()
        val blues = ArrayList<Int>()
        fun sample(x: Int, y: Int) {
            val p = bitmap[x, y]
            reds.add((p shr 16) and 0xFF)
            greens.add((p shr 8) and 0xFF)
            blues.add(p and 0xFF)
        }
        val stepX = maxOf(1, w / 48)
        val stepY = maxOf(1, h / 64)
        for (x in 0 until w step stepX) {
            sample(x, inset)
            sample(x, h - 1 - inset)
        }
        for (y in 0 until h step stepY) {
            sample(inset, y)
            sample(w - 1 - inset, y)
        }
        reds.sort(); greens.sort(); blues.sort()
        Color(reds[reds.size / 2], greens[greens.size / 2], blues[blues.size / 2])
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}