package com.frederic22.hyperextreme.alive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun clockFamily(style: String) = when {
    style.startsWith("flyme-") -> 1
    style.contains(".vivo.") -> 2
    else -> 0
}

internal fun clockTitle(style: String): String = NativeHyperOsStyles.find(style)?.title
    ?: if (style.startsWith("flyme-")) "Flyme · ${when (style) {
        "flyme-0" -> "横向时钟"; "flyme-2" -> "纵向时钟"; else -> "原版样式 ${(style.substringAfter('-').toIntOrNull() ?: 0) + 1}"
    }}" else "Vivo · ${style.substringAfterLast('.')}"

private val clockArtworkCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

@Composable
internal fun rememberClockArtwork(style: String): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val result by produceState<ImageBitmap?>(null, style) {
        value = null
        value = withContext(Dispatchers.IO) { loadClockArtwork(context, style)?.asImageBitmap() }
    }
    return result
}

internal fun loadClockArtwork(context: Context, style: String): Bitmap? {
    clockArtworkCache.get(style)?.let { return it }
    val path = when (clockFamily(style)) {
        0 -> NativeHyperOsStyles.find(style)?.let { "native-clock/previews/${it.preview}" }
        2 -> "native-clock/vivo/previews/${style.substringAfterLast('.')}.png"
        else -> null
    } ?: return null
    return runCatching {
        val source = context.assets.open(path).use(BitmapFactory::decodeStream) ?: return null
        // Bundled references are full-screen captures. Fit their actual ink, not the empty screen.
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        var left = source.width; var top = source.height; var right = -1; var bottom = -1
        pixels.forEachIndexed { index, color ->
            if (color ushr 24 > 16 && ((color shr 16 and 255) > 24 || (color shr 8 and 255) > 24 || (color and 255) > 24)) {
                val x = index % source.width; val y = index / source.width
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        if (right < left) { source.recycle(); return null }
        val cropped = Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
        if (cropped !== source) source.recycle()
        clockArtworkCache.put(style, cropped)
        cropped
    }.getOrNull()
}
