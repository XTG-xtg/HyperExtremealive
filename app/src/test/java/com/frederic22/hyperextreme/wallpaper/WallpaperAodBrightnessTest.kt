package com.frederic22.hyperextreme.wallpaper

import android.app.Application
import android.hardware.display.DisplayManager
import android.view.Display
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
    @Test fun aLaggingDozeNotificationCannotBoostTheFirstAwakeFrameAgain() {
        val context=RuntimeEnvironment.getApplication()
        val power=shadowOf(context.getSystemService(PowerManager::class.java))
        val display=context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val looper=shadowOf(Looper.getMainLooper())
        power.setIsInteractive(false)
        shadowOf(display).setState(Display.STATE_DOZE)
        AodBrightness.save(context,5f)
        val state=WallpaperAodBrightness(context,Handler(Looper.getMainLooper())){}
        try {
            state.visible(true);looper.idleFor(Duration.ofSeconds(1))
            assertEquals(5f,state.exposure,0f)
            power.setIsInteractive(true)
            // Display.state still reports DOZE while PowerManager has already accepted wake.
            repeat(20) {
                state.onDisplayChanged(Display.DEFAULT_DISPLAY);looper.idleFor(Duration.ofMillis(16))
                assertEquals(1f,state.exposure,0f)
            }
            power.setIsInteractive(false)
            state.onDisplayChanged(Display.DEFAULT_DISPLAY);looper.idleFor(Duration.ofSeconds(1))
            assertEquals(5f,state.exposure,0f)
        } finally {state.close();shadowOf(display).setState(Display.STATE_ON)}
    }
    @Test fun subUnityDimmingStillFadesSmoothlyOnWake() {
        val context=RuntimeEnvironment.getApplication()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        AodBrightness.save(context,.25f)
        val state=WallpaperAodBrightness(context,Handler(Looper.getMainLooper())){}
        val looper=shadowOf(Looper.getMainLooper())
        try {
            state.visible(true);looper.idleFor(Duration.ofSeconds(1))
            state.ambient(false);looper.idle()
            assertEquals(.25f,state.exposure,0f)
            looper.idleFor(Duration.ofMillis(350))
            assertTrue(state.exposure>.25f && state.exposure<1f)
            looper.idleFor(Duration.ofMillis(400))
            assertEquals(1f,state.exposure,0f)
        } finally {state.close()}
    }
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
    @Test fun serviceWakeDropsExtraExposureOnItsFirstFrameAndStopsDrawing() {
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
            state.ambient(false);looper.idle()
            assertEquals(1f,state.exposure,0f)
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
