package com.frederic22.hyperextreme.ui

import android.content.Context
import android.graphics.Bitmap
import com.frederic22.hyperextreme.data.MaskRole
import com.frederic22.hyperextreme.data.MaskStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class MaskPreviewContent { NONE, MASK, PARALLAX }

/** Per-screen cache: unrelated settings writes and resume events must not decode full photos again. */
internal class MaskPreviewLoader(
    private val context: Context,
    private val content: MaskPreviewContent,
    private val decode: (File) -> Bitmap? = { MaskStore.loadPreview(it, PreviewMaxEdge) },
) {
    data class Snapshot(val masks: Map<MaskRole, Bitmap?> = emptyMap(), val parallax: Bitmap? = null)
    private data class Stamp(val path: String, val length: Long, val modified: Long)
    private val mutex = Mutex()
    private var key: List<Stamp>? = null
    private var revision: Long? = null
    private var cached = Snapshot()

    suspend fun load(sourceRevision: Long): Snapshot = mutex.withLock {
        if (content == MaskPreviewContent.NONE) return@withLock cached
        withContext(Dispatchers.IO) {
            val files = when (content) {
                MaskPreviewContent.MASK -> MaskRole.selectable.map { MaskStore.file(context, it) }
                MaskPreviewContent.PARALLAX -> listOf(MaskStore.parallaxFile(context))
                MaskPreviewContent.NONE -> emptyList()
            }
            val next = files.map { Stamp(it.path, it.length(), it.lastModified()) }
            if (key != next || revision != sourceRevision) {
                val images = files.map { if (it.isFile && it.length() > 0) decode(it) else null }
                cached = if (content == MaskPreviewContent.MASK) Snapshot(MaskRole.selectable.zip(images).toMap())
                    else Snapshot(parallax = images.firstOrNull())
                // Use the pre-decode stamp: a file replaced during decoding invalidates the next load.
                key = next
                revision = sourceRevision
            }
            cached
        }
    }
}
