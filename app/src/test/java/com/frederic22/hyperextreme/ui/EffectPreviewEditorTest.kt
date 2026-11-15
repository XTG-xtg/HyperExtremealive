package com.frederic22.hyperextreme.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.frederic22.hyperextreme.alive.AodBrightness
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EffectPreviewEditorTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun check(kind: EffectWallpaperKind, name: String) {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var applied: Float? = null; var cancelled = false; var shownMode = -1; var shownBrightness = 0f
        try {
            val ctx = host.get(); val saved = AodBrightness.load(ctx)
            // Layout fixture only; the GLES preview is checked separately with its production shaders.
            val image = ctx.assets.open("infinity/blue/preview.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() }
            ctx.setContent {
                MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }, textStyles = HyperTextStyles) {
                    EffectPreviewEditor(kind.title, saved, true, "", { cancelled = true }, { applied = it }) { mode, _, brightness, _ ->
                        SideEffect { shownMode = mode; shownBrightness = brightness }
                        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
            compose.onNodeWithTag("alive-editor-modes").assertIsDisplayed()
            compose.onNodeWithTag("aod-brightness-slider").assertDoesNotExist()
            for(label in listOf("息屏", "锁屏", "桌面")) compose.onNodeWithText(label).performClick().assertIsSelected()
            capture(name, compose.onRoot())
            compose.onNodeWithTag("alive-tool-息屏亮度").performClick()
            compose.onNodeWithTag("effect-brightness-panel").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, shownMode) }
            compose.onNodeWithTag("aod-brightness-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
            compose.runOnIdle { assertEquals(5f, shownBrightness, 0f); assertNull(applied); assertEquals(saved, AodBrightness.load(ctx), 0f) }
            capture("$name-brightness", compose.onNodeWithTag("effect-brightness-panel"))
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("effect-brightness-panel").assertDoesNotExist()
            compose.onNodeWithTag("alive-header-取消").performClick()
            assertTrue(cancelled); assertNull(applied); assertEquals(saved, AodBrightness.load(ctx), 0f)
            compose.onNodeWithTag("alive-header-应用").performClick()
            assertEquals(5f, applied!!, 0f)
        } finally { host.pause().stop().destroy() }
    }
    private fun capture(name: String, node: SemanticsNodeInteraction) {
        val dir = File("build/reports/effect-preview").apply { mkdirs() }
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun spatialUsesAliveEditorAndKeepsBrightnessInDraft() = check(EffectWallpaperKind.SPATIAL, "spatial")
    @Test fun rasterUsesAliveEditorAndKeepsBrightnessInDraft() = check(EffectWallpaperKind.RASTER, "raster")
    @Test fun parallaxUsesAliveEditorAndKeepsBrightnessInDraft() = check(EffectWallpaperKind.PARALLAX, "parallax")
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
    fun compactScreenKeepsActionsAndBrightnessReachable() = check(EffectWallpaperKind.SPATIAL, "compact")
}
