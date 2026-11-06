package com.frederic22.hyperextreme.alive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Cover the SurfaceView before replacing EGL, then reveal only the new generation's first frame. */
@Stable
internal class PreviewSceneTransition {
    val shade = Animatable(0f)
    var running by mutableStateOf(false)
        private set

    suspend fun switch(replaceAndAwaitFrame: suspend () -> Unit) {
        if(running) return
        running = true
        try {
            shade.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            // Let Compose draw the opaque cover before the independent SurfaceView is replaced.
            repeat(2) { withFrameNanos { } }
            replaceAndAwaitFrame()
        } finally {
            try { shade.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) }
            finally {
                withContext(NonCancellable) { shade.snapTo(0f) }
                running = false
            }
        }
    }
}
