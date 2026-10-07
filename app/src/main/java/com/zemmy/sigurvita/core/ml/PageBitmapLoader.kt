package com.zemmy.sigurvita.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.imageLoader
import coil.request.ImageRequest
import com.zemmy.sigurvita.core.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PageBitmapLoader {

    suspend fun decodeForInference(context: Context, page: ReaderPage, targetSize: Int = 640): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (page.isLocal || page.imageUrl.startsWith("/") || page.imageUrl.startsWith("file://")) {
                val filePath = page.imageUrl.removePrefix("file://")
                val file = File(filePath)
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    val origW = options.outWidth
                    val origH = options.outHeight
                    if (origW <= 0 || origH <= 0) return@withContext null

                    var sampleSize = 1
                    var maxDim = Math.max(origW, origH)
                    while (maxDim / 2 >= targetSize) {
                        sampleSize *= 2
                        maxDim /= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    return@withContext BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                }
            }

            // Remote / Streaming via Coil Cache
            val request = ImageRequest.Builder(context)
                .data(page.imageUrl)
                .allowHardware(false)
                .build()

            val result = context.imageLoader.execute(request)
            val drawable = result.drawable
            if (drawable != null) {
                return@withContext drawableToBitmap(drawable)
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 640
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 640
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
