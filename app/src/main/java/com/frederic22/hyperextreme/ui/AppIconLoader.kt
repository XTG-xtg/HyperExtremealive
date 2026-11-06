package com.frederic22.hyperextreme.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared by the visible function cards, never by activities or wallpaper engines. */
internal class AppIconLoader(
    context: Context,
    private val sizePx: Int,
    private val decode: (String) -> ImageBitmap? = { name ->
        try {
            val drawable = context.packageManager.getApplicationIcon(name)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    },
) {
    private val mutex = Mutex()
    private val icons = LinkedHashMap<String, ImageBitmap?>()

    init { require(sizePx in 1..512) }

    suspend fun load(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            // containsKey also caches missing packages; repeated cards must not repeat binder calls.
            if (!icons.containsKey(packageName)) {
                if (icons.size >= 8) icons.remove(icons.keys.first())
                icons[packageName] = decode(packageName)
            }
            icons[packageName]
        }
    }
}
