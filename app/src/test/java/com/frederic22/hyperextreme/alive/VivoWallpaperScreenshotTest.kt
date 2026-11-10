package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.frederic22.hyperextreme.ui.DetailTabHost
import com.frederic22.hyperextreme.ui.FunctionHome
import com.frederic22.hyperextreme.ui.DetailPage
import com.frederic22.hyperextreme.ui.HyperTextStyles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VivoWallpaperScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun capture(name: String, dark: Boolean = false, family: Int = 1, large: Boolean = false) {
        RuntimeEnvironment.setFontScale(if (large) 1.4f else 1f)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val app = host.get()
            val cover = File(app.cacheDir, "vivo-test-cover.jpg")
            app.assets.open("vivo/preview/${if (family == 3) "flash" else "liquid"}.jpg").use { input ->
                cover.outputStream().use { input.copyTo(it) }
            }
            var imported = -1; var preview = false; var mask = -1; var recognized = -1
            var options = VivoOptions(family)
            val before = HashMap(Config.prefs(app).all)
            app.setContent {
                MiuixTheme(controller = remember { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) }, textStyles = HyperTextStyles) {
                    var value by remember { mutableStateOf(options) }
                    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                        DetailTabHost(vivoTitle(700 + family), {}, null) {
                            VivoWallpaperContent(value, cover, false, false, "", false,
                                onChange = { value = it; options = it }, onImport = { imported = it },
                                onPreview = { preview = true }, onCrop = {}, onMask = { mask = it },
                                onRecognize = { recognized = it }, onSwap = { _, _ -> }, onReset = {}, onStop = {})
                        }
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("wallpaper-cover-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            fun save(suffix: String) {
                val dir = File("build/reports/alive-ui").apply { mkdirs() }
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    File(dir, "$name$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            save("")
            compose.onNodeWithTag("vivo-preview").performScrollTo().performClick(); assertTrue(preview)
            compose.onNodeWithText("更换壁纸").performScrollTo().performClick()
            assertEquals(VivoMediaSelection.WALLPAPER, imported)
            compose.onNodeWithText("重新识别壁纸主体").performScrollTo().performClick(); assertEquals(2, recognized)
            compose.onNodeWithText("修整主体").performScrollTo().performClick(); assertEquals(2, mask)
            compose.onNodeWithText("主体识别引擎").performScrollTo()
            save("-subject")
            compose.onNodeWithText("主体识别引擎").performClick()
            compose.onNodeWithText("仅本地 AI").performClick(); assertEquals(2, options.subjectEngine)
            compose.onNodeWithText("动作响应").performScrollTo(); save("-motion")
            compose.onNodeWithText("效果与素材").assertDoesNotExist()
            assertEquals(before, Config.prefs(app).all)
        } finally { host.pause().stop().destroy(); RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun liquidLight() = capture("vivo-liquid-light")
    @Test fun liquidDark() = capture("vivo-liquid-dark", dark = true)
    @Test fun flashLight() = capture("vivo-flash-light", family = 3)
    @Test fun liquidLargeText() = capture("vivo-liquid-large-text", large = true)
    @Test @org.robolectric.annotation.Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun liquidCompact() = capture("vivo-liquid-compact")
    @Test fun flashCurrentCoverStaysIndependentFromDraftAndHistory() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val app=host.get();var opened="";var saved=false
            val draft=VivoOptions(3).apply { style=3 };val current=VivoOptions(3).apply { style=2 }
            app.setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Light) }) {
                    DetailTabHost("闪卡壁纸",{},null) {
                        VivoWallpaperContent(draft,null,true,true,"",false,{},{},{opened="working"},{},{},{},{_,_->},{},{},
                            currentOptions=current,savedCards=listOf(com.frederic22.hyperextreme.ui.CoverItem("history",null,description="已保存闪卡")),
                            savedLabels=mapOf("history" to "镭射光带"),onCurrent={opened="current"},onSaved={opened=it},onSaveDraft={saved=true})
                    }
                }
            }
            compose.onNodeWithContentDescription("当前壁纸封面").assertIsDisplayed().performClick()
            assertEquals("current",opened)
            compose.onNodeWithTag("wallpaper-cover-carousel").performScrollToIndex(2);compose.waitForIdle()
            compose.onNodeWithContentDescription("未应用的草稿").performClick();assertEquals("working",opened)
            compose.onNodeWithTag("wallpaper-cover-carousel").performScrollToIndex(3);compose.waitForIdle()
            compose.onNodeWithContentDescription("已保存闪卡").performClick();assertEquals("history",opened)
            compose.onNodeWithText("保存当前草稿为预设").performScrollTo().performClick();assertTrue(saved)
            assertEquals(2,current.style)
        } finally { host.pause().stop().destroy() }
    }
    @Test fun featureHomeHasPeerEntries() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var page: DetailPage? = null
            host.get().setContent {
                MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Light) }, textStyles = HyperTextStyles) {
                    FunctionHome { page = it }
                }
            }
            compose.onNodeWithText("液态壁纸").performScrollTo().performClick(); assertEquals(DetailPage.LiquidWallpaper, page)
            compose.onNodeWithText("闪卡壁纸").performScrollTo().performClick(); assertEquals(DetailPage.FlashWallpaper, page)
            compose.onNodeWithText("光栅壁纸").performScrollTo().performClick(); assertEquals(DetailPage.RasterWallpaper, page)
        } finally { host.pause().stop().destroy() }
    }
}
