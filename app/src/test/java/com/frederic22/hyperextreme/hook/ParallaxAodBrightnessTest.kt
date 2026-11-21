package com.frederic22.hyperextreme.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import com.frederic22.hyperextreme.alive.AodBrightness
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParallaxAodBrightnessTest {
    private class Backdrop(context: Context) : View(context) {
        var applied: RenderEffect? = null
        override fun setRenderEffect(effect: RenderEffect?) { applied = effect; super.setRenderEffect(effect) }
    }

    @After fun release() {
        ParallaxAodBrightness.transition(true, .5f, false)
        ParallaxAodBrightness.detach()
    }

    @Test fun actualAgslCompilesWithUnboundedExposureAndCanChainPasswordBlur() {
        val shader = RuntimeShader(ParallaxAodBrightness.SHADER)
        for(value in listOf(.25f, 1.3f, 1000f, Float.MAX_VALUE)) {
            shader.setFloatUniform("dim", 1-AodBrightness.wallpaperBlack(.5f, value))
            shader.setFloatUniform("exposure", AodBrightness.gain(value, 1f))
            val exposure = RenderEffect.createRuntimeShaderEffect(shader, "content")
            val blur = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            assertNotNull(RenderEffect.createChainEffect(blur, exposure))
        }
    }

    @Test fun awakeRestoresExistingBlurAndReattachmentUsesLatestRequestedState() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val first = Backdrop(activity)
            activity.setContentView(first)
            assertTrue(first.isAttachedToWindow)
            val blur = RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP)
            ParallaxAodBrightness.apply(first, blur)
            assertSame(blur, first.applied)
            ParallaxAodBrightness.transition(false, .5f, false)
            assertNotNull(first.applied)
            assertNotSame(blur, first.applied)
            ParallaxAodBrightness.transition(true, .5f, false)
            assertSame(blur, first.applied)

            // Detach before the animated entry advances; the next backdrop must
            // receive the requested AOD state rather than the first awake frame.
            ParallaxAodBrightness.transition(false, .5f, true)
            val second = Backdrop(activity)
            activity.setContentView(second)
            ParallaxAodBrightness.apply(second, null)
            assertNotNull(second.applied)
            ParallaxAodBrightness.transition(true, .5f, false)
            assertNull(second.applied)
        } finally { controller.pause().stop().destroy() }
    }
}
