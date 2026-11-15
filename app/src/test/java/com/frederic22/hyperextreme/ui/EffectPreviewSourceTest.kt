package com.frederic22.hyperextreme.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import com.frederic22.hyperextreme.alive.AodBrightness
import com.frederic22.hyperextreme.data.MaskStore
import com.frederic22.hyperextreme.data.RasterStore
import com.frederic22.hyperextreme.hook.RasterGlCore
import com.frederic22.hyperextreme.hook.SpatialGlCore
import com.frederic22.hyperextreme.spatial.SpatialDepth
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EffectPreviewSourceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun picture(file: File) {
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(12, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff7799aa.toInt())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun requestsAndResultsKeepWallpaperIdentityAndBoundedBrightness() {
        for(kind in EffectWallpaperKind.entries) {
            val request = EffectPreviewRequest(kind, if(kind == EffectWallpaperKind.PARALLAX) "" else "0123456789abcdef")
            assertEquals(request, EffectPreviewRequest.parse(request.intent(context)))
            assertEquals(5f, EffectPreviewRequest.brightness(Activity.RESULT_OK, request.result(10000f))!!, 0f)
            assertEquals(0f, EffectPreviewRequest.brightness(Activity.RESULT_OK, request.result(0f))!!, 0f)
            assertNull(EffectPreviewRequest.brightness(Activity.RESULT_OK, request.result(1f).putExtra("brightness", 6f)))
            assertNull(EffectPreviewRequest.brightness(Activity.RESULT_CANCELED, request.result(10000f)))
            assertNull(EffectPreviewRequest.brightness(Activity.RESULT_OK, request.result(1f).putExtra("brightness", Float.NaN)))
        }
        assertNull(EffectPreviewRequest.parse(Intent().putExtra("kind", "SPATIAL").putExtra("key", "../another")))
        assertNull(EffectPreviewRequest.parse(Intent().putExtra("kind", "unknown")))
    }
    @Test fun loadingAllThreeSourcesPreservesAppliedBrightnessAndRasterOrder() {
        val before = AodBrightness.load(context)
        val key = "0123456789abcdef"
        val layers = File(SpatialDepth.cacheRoot(context), "$key/layers").apply { mkdirs() }
        picture(File(layers, "layer0.png")); picture(File(layers, "layer1.png")); picture(File(layers, "depth.png"))
        File(layers, "meta.json").writeText("""{"layers":2,"bodyK":0.4}""")
        val spatial = EffectPreviewSource.load(context, EffectPreviewRequest(EffectWallpaperKind.SPATIAL, key))
        assertEquals(2, spatial.images.size); assertNotNull(spatial.depth); assertEquals(.4f, spatial.bodyK, 0f)
        val raster = File(RasterStore.root(context), "12345").apply { mkdirs() }
        picture(File(raster, "a.png")); picture(File(raster, "b.png"))
        File(raster, "meta.json").writeText("""{"id":"12345","items":["b.png","a.png"]}""")
        val group = EffectPreviewSource.load(context, EffectPreviewRequest(EffectWallpaperKind.RASTER, "12345"))
        assertEquals(listOf("b.png", "a.png"), group.images.map { it.name })
        picture(MaskStore.parallaxFile(context))
        val parallax = EffectPreviewSource.load(context, EffectPreviewRequest(EffectWallpaperKind.PARALLAX, ""))
        assertEquals(MaskStore.parallaxFile(context), parallax.images.single())
        assertEquals(before, AodBrightness.load(context), 0f)
        val out = File("build/reports/effect-preview/shaders").apply { mkdirs() }
        File(out, "spatial.vert").writeText(SpatialGlCore.VERT); File(out, "spatial.frag").writeText(SpatialGlCore.FRAG)
        File(out, "raster.vert").writeText(RasterGlCore.VERT); File(out, "raster.frag").writeText(RasterGlCore.FRAG)
    }
    @Test fun missingDepthAndCorruptPicturesCannotBecomeAnApplicablePreview() {
        assertThrows(Exception::class.java) { EffectPreviewSource.load(context, EffectPreviewRequest(EffectWallpaperKind.SPATIAL, "deadbeef")) }
        MaskStore.parallaxFile(context).writeText("corrupt")
        assertThrows(Exception::class.java) { EffectPreviewSource.load(context, EffectPreviewRequest(EffectWallpaperKind.PARALLAX, "")) }
    }
    @Test fun exposureLeavesAwakeScenesAloneAndCoverPreservesAspectRatio() {
        for(value in listOf(0f, .25f, 1f, 1.3f, 5f, 10000f)) {
            assertEquals(1f, EffectPreviewMath.dim(value, 0f), 0f)
            assertEquals(1f, AodBrightness.gain(value, 0f), 0f)
            assertTrue(EffectPreviewMath.dim(value, 1f) >= .5f)
        }
        for((iw, ih) in listOf(1080 to 2400, 2400 to 1080, 1000 to 1000)) {
            val uv = EffectPreviewMath.cover(iw, ih, 1200, 2670)
            assertEquals(1200f/2670, iw*uv[0]/(ih*uv[1]), .00001f)
            assertTrue(uv[2] >= 0 && uv[3] >= 0)
            assertTrue(uv[0]+uv[2] <= 1 && uv[1]+uv[3] <= 1)
        }
        assertEquals(2, EffectPreviewMath.index(-1, 3)); assertEquals(0, EffectPreviewMath.index(3, 3))
    }
}
