package com.frederic22.hyperextreme.hook

/**
 * SystemUI-only flag: a third-party music cover/lyric foreground is actually showing.
 *
 * HyperMusicCover paints lyrics into keyguard_foreground_layer. Alive/spatial
 * hide that layer when there is no depth subject, so lyrics vanish. Native media
 * controls do not own that layer and must not detach spatial/raster subjects.
 */
internal object Coexist {
    @Volatile
    var mcMusicScreen: Boolean = false

    @Volatile
    var onChanged: (() -> Unit)? = null
}
