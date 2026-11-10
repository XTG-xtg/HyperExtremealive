package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
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
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExtensionEditorTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun checkEditor(name: String) {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var latest = ExtensionOptions.defaults()
        var styles = 0
        var plays = 0
        try {
            host.get().setContent {
                MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }, textStyles = HyperTextStyles) {
                    var draft by remember { mutableStateOf(latest) }
                    Column(Modifier.fillMaxSize().background(Color(0xFF111113)).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        ExtensionControls(draft, false, { draft = it; latest = it }, { styles++ }, { plays++ })
                    }
                }
            }
            compose.onNodeWithTag("extension-styles").performClick(); assertEquals(1, styles)
            compose.onNodeWithText("光环").performScrollTo().performClick(); assertEquals(1, latest.light)
            compose.onNodeWithText("15 秒").performScrollTo().performClick(); assertEquals(15, latest.seconds)
            compose.onNodeWithTag("extension-color-2").performScrollTo().performClick(); assertEquals(0xffffd6a0.toInt(), latest.color)
            compose.onNodeWithText("播放光效").performScrollTo().performClick(); assertEquals(1, plays)
            assertFalse(ExtensionOptions.load(RuntimeEnvironment.getApplication()).clock)
            assertEquals(0, ExtensionOptions.load(RuntimeEnvironment.getApplication()).light)
            val out = File("build/reports/alive-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
                File(out, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { host.pause().stop().destroy() }
    }
    @Test fun controlsOnlyEditDraftAndPreviewIsExplicit() = checkEditor("ui8-extension-controls")
    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun compactControlsKeepAllActionsReachable() = checkEditor("ui8-extension-controls-compact")
    @Test fun restoredDraftPreservesEveryFieldWithoutSaving() {
        val draft = ExtensionOptions.defaults().edit(clock = true, light = 2, seconds = 5, scale = 1.2f, style = "flyme-4")
        val saved = with(ExtensionSaver) { androidx.compose.runtime.saveable.SaverScope { true }.save(draft) }!!
        val result = ExtensionSaver.restore(saved)!!
        assertEquals(draft.clockKey(), result.clockKey()); assertTrue(result.clock)
        assertEquals(2, result.light); assertEquals(5, result.seconds)
        assertEquals(0, ExtensionOptions.load(RuntimeEnvironment.getApplication()).light)
    }
}
