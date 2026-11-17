package com.frederic22.hyperextreme.hook

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Handler
import android.os.Looper
import android.view.View
import com.frederic22.hyperextreme.alive.AodBrightness
import java.lang.ref.WeakReference

/** Exposure belongs to our backdrop only; preserve the system's subject and existing password blur. */
internal object ParallaxAodBrightness {
    private var backdrop: WeakReference<View>? = null
    private var blur: RenderEffect? = null
    private var watch: AodBrightness.Watch? = null
    private var shader: RuntimeShader? = null
    private var animation: ValueAnimator? = null
    private var black = 0f
    private var target = 0f
    private var aodBlack = .5f
    private val main = Handler(Looper.getMainLooper())
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { if(backdrop?.get() === v) update() }
        override fun onViewDetachedFromWindow(v: View) { if(backdrop?.get() === v) detach() }
    }
    fun apply(view: View, baseBlur: RenderEffect?) {
        if(backdrop?.get() !== view) {
            detach(); backdrop = WeakReference(view); view.addOnAttachStateChangeListener(attachment)
            watch = AodBrightness.Watch(view.context, main) { update() }
        }
        blur = baseBlur; update()
    }
    fun transition(show: Boolean, targetBlack: Float, animate: Boolean) {
        if(Looper.myLooper() != Looper.getMainLooper()) { main.post { transition(show, targetBlack, animate) }; return }
        if(!targetBlack.isFinite()) return
        animation?.cancel()
        if(!show) aodBlack = targetBlack.coerceIn(0f, 1f)
        target = if(show) 0f else aodBlack
        if(show) update() // Drop extra exposure before the first native wake animation frame.
        if(!animate || backdrop?.get()?.isAttachedToWindow != true) { black = target; update(); return }
        animation = ValueAnimator.ofFloat(black, target).apply {
            duration = 700
            addUpdateListener { black = it.animatedValue as Float; update() }
            start()
        }
    }
    private fun update() {
        val view = backdrop?.get()?.takeIf { it.isAttachedToWindow } ?: return
        if(black <= 0) { view.setRenderEffect(blur); return }
        runCatching {
            val effectShader = shader ?: RuntimeShader(SHADER).also { shader = it }
            val value = watch?.value ?: AodBrightness.DEFAULT
            effectShader.setFloatUniform("dim", AodBrightness.wallpaperDim(1-black, 1-aodBlack, value))
            effectShader.setFloatUniform("exposure", AodBrightness.renderGain(value, black/aodBlack.coerceAtLeast(.001f), target>0f, false, watch?.managed == true))
            val exposure = RenderEffect.createRuntimeShaderEffect(effectShader, "content")
            view.setRenderEffect(blur?.let { RenderEffect.createChainEffect(it, exposure) } ?: exposure)
        }.onFailure { HookRuntime.log("视差息屏亮度设置失败：${it.message}") }
    }
    fun detach() {
        animation?.cancel(); animation = null
        // No frames are visible while detached. A new backdrop starts at the latest
        // requested state instead of retaining a half-finished AOD transition.
        black = target
        backdrop?.get()?.removeOnAttachStateChangeListener(attachment)
        backdrop = null; blur = null; watch?.close(); watch = null
    }

    internal val SHADER = """
        uniform shader content;
        uniform float dim;
        uniform float exposure;
        ${AodBrightness.AGSL}
        half4 main(float2 p) {
            half4 c = content.eval(p);
            return half4(aodExpose(c.rgb / max(c.a, 0.00001) * dim, exposure) * c.a, c.a);
        }
    """.trimIndent()
}
