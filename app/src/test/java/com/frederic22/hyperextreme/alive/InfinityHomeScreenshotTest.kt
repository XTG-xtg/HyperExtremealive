package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.frederic22.hyperextreme.ui.HyperTextStyles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfinityHomeScreenshotTest {
    @get:Rule val compose=createEmptyComposeRule()

    private fun capture(name: String,dark: Boolean) {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val app=host.get()
            // Shipped reference thumbnail; UI capture does not validate Android GLES or AOD.
            val frame=app.assets.open("infinity/blue/preview.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() }
            var library=0;var preview=0;var toggle=false
            app.setContent {
                MiuixTheme(controller=remember { ThemeController(if(dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) },textStyles=HyperTextStyles) {
                    AliveHomeContent(AliveHomeModel(name="Infinity · 蓝色",variant=803,busy=false,frames=listOf(frame)),
                        onBack={},onDiagnostics={},onMode={preview++},onPhoto={},onLibrary={library++},
                        onToggle={toggle=it},onRetry={},onNew={library++},infinity=true)
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("infinity-home-page").assertIsDisplayed()
            compose.onAllNodesWithText("Infinity 壁纸").onFirst().assertExists()
            compose.onNodeWithText("预览与应用").performClick();assertEquals(1,preview)
            compose.onNodeWithText("照片壁纸").assertDoesNotExist()
            compose.onNodeWithText("动态壁纸").assertDoesNotExist()
            val directory=File("build/reports/infinity-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            compose.onNodeWithText("主题样式").performScrollTo().performClick();assertEquals(1,library)
            compose.onNodeWithText("启用 Infinity 壁纸").performScrollTo().performClick();assertTrue(toggle)
            compose.onNodeWithText("启用 Alive 壁纸").assertDoesNotExist()
        } finally { host.pause().stop().destroy() }
    }
    @Test fun light()=capture("infinity-home-light",false)
    @Test fun dark()=capture("infinity-home-dark",true)
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
    fun compact()=capture("infinity-home-compact",false)

    @Test fun infinityLibraryShowsTwentyThemesAndOpensTheirEditor() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val app=host.get()
            app.setContent { MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Light) }) { AliveLibrary(infinity=true) {} } }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("alive-library-grid").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("alive-library-grid").performScrollToNode(hasText("Infinity · 蓝色"))
            compose.onNodeWithText("启航").assertDoesNotExist()
            compose.onNodeWithText("照片壁纸").assertDoesNotExist()
            for((name,variant) in listOf("Infinity · 蓝色" to 803,"N8 · 黑色" to 807,"几何 · 紫色渐变" to 820)) {
                compose.onNodeWithTag("alive-library-grid").performScrollToNode(hasText(name))
                compose.onNodeWithText(name).performClick()
                val intent=org.robolectric.Shadows.shadowOf(app).nextStartedActivity
                assertEquals(AlivePreviewActivity::class.java.name,intent.component?.className)
                assertEquals(variant,intent.getIntExtra("variant",0))
            }
            assertEquals(20,AliveCatalog.load(app).count { isWallpaperLibraryScene(it,true) })
        } finally { host.pause().stop().destroy() }
    }
}
