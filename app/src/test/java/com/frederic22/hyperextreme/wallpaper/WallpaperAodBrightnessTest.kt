package com.frederic22.hyperextreme.wallpaper

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.frederic22.hyperextreme.alive.AodBrightness
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35], application=Application::class)
class WallpaperAodBrightnessTest {
    @Test fun settledAodRetainsBrightnessWithoutAnIdleDrawLoop() {
        val context=RuntimeEnvironment.getApplication()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        AodBrightness.save(context,5f)
        var draws=0
        val state=WallpaperAodBrightness(context,Handler(Looper.getMainLooper())){draws++}
        val looper=shadowOf(Looper.getMainLooper())
        try {
            state.visible(true);looper.idleFor(Duration.ofSeconds(1))
            assertEquals(5f,state.exposure,0f)
            val settled=draws
            repeat(60) {
                state.onDisplayChanged(android.view.Display.DEFAULT_DISPLAY)
                looper.idleFor(Duration.ofSeconds(1))
                assertEquals(5f,state.exposure,0f)
            }
            assertEquals(settled,draws)
            AodBrightness.save(context,0f);looper.idle()
            assertEquals(1f,state.exposure,0f)
        } finally { state.close() }
    }
    @Test fun serviceSleepWakeReversalIsContinuousAndStopsDrawing() {
        val context=RuntimeEnvironment.getApplication()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        AodBrightness.save(context,5f)
        var draws=0
        val state=WallpaperAodBrightness(context,Handler(Looper.getMainLooper())){draws++}
        val looper=shadowOf(Looper.getMainLooper())
        try {
            state.visible(true);looper.idle()
            assertEquals(1f,state.exposure,0f)
            state.ambient(true);looper.idleFor(Duration.ofMillis(350))
            assertTrue(state.exposure>1f && state.exposure<5f)
            val before=state.exposure
            state.ambient(false);looper.idle()
            assertEquals(before,state.exposure,.001f)
            looper.idleFor(Duration.ofMillis(750))
            assertEquals(1f,state.exposure,0f)
            val settled=draws;looper.idleFor(Duration.ofSeconds(5));assertEquals(settled,draws)
            repeat(10){state.onDisplayChanged(android.view.Display.DEFAULT_DISPLAY)}
            looper.idleFor(Duration.ofSeconds(1));assertEquals(settled,draws)
            state.visible(false);state.ambient(true);looper.idleFor(Duration.ofSeconds(1))
            assertEquals(settled,draws)
        } finally {state.close()}
    }
}
