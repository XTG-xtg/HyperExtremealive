package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VivoFlashStylesTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun fiveRealRenderAssetsAreDistinctAndPortrait() {
        val hashes = (1..5).map { style ->
            val bytes = RuntimeEnvironment.getApplication().assets.open("vivo/preview/flash-styles/$style.png").use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(360, bitmap.width); assertEquals(800, bitmap.height); bitmap.recycle()
            bytes.contentHashCode()
        }
        assertEquals(5, hashes.toSet().size)
    }
    @Test fun namedCardsSelectAllFiveStylesWithoutRepeatedTiltHint() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var selected = 1
        try {
            host.get().setContent {
                MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }) {
                    var options by remember { mutableStateOf(VivoOptions(3)) }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        VivoControls(options) { options = it; selected = it.style }
                    }
                }
            }
            compose.onNodeWithText(VivoFlashStyles.HINT).assertDoesNotExist()
            compose.onNodeWithText("原版效果 1").assertDoesNotExist()
            for (id in 1..5) {
                compose.onNodeWithTag("flash-style-gallery").performScrollToIndex(id - 1)
                compose.onNodeWithTag("flash-style-$id").performClick().assertIsSelected()
                assertEquals(id, selected)
                if(id==3) compose.onNodeWithText("光斑图案").assertIsDisplayed()
            }
            compose.onNodeWithTag("flash-style-gallery").performScrollToIndex(1)
            compose.onNodeWithTag("flash-style-2").performClick()
            compose.onNodeWithText("深色壁纸更明显", substring = true).performScrollTo().assertExists()
            val directory = File("build/reports/alive-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
                File(directory, "flash-style-gallery.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { host.pause().stop().destroy() }
    }
    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun patternSelectorIsVisibleWithoutVerticalScrollInPreviewSheet() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) }) {
                    top.yukonga.miuix.kmp.window.WindowBottomSheet(show=true,title="效果设置",onDismissRequest={}) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            VivoControls(VivoOptions(3).apply { style=3 }) {}
                        }
                    }
                }
            }
            compose.onNodeWithText("光斑图案").assertIsDisplayed().performClick()
            compose.onNodeWithText("爱心").assertIsDisplayed()
            compose.onNodeWithText(VivoFlashStyles.HINT).assertDoesNotExist()
        } finally { host.pause().stop().destroy() }
    }
}
