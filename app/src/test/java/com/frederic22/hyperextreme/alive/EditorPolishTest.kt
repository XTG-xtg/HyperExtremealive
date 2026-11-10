package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
class EditorPolishTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun capture(name: String) {
        val dir = File("build/reports/alive-ui").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "ui10-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun mask(name: String, large: Boolean = false, subject: Boolean = true) {
        RuntimeEnvironment.setFontScale(if (large) 1.4f else 1f)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var selected = 0; var compared = false; var recognized = false; var saved = false
        var working by mutableStateOf(false)
        try {
            val image = host.get().assets.open("vivo/preview/liquid.jpg").use(BitmapFactory::decodeStream).asImageBitmap()
            host.get().setContent {
                MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }, textStyles = HyperTextStyles) {
                    var tool by remember { mutableIntStateOf(0) }
                    var show by remember { mutableStateOf(true) }
                    var brush by remember { mutableFloatStateOf(.03f) }
                    MaskEditorContent(subject, true, working, tool, brush, show, false, "", {}, { saved = true },
                        { tool = it; selected = it }, { brush = it }, { show = !show; compared = true }, {}, {}, { recognized = true }) {
                        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
            compose.onNodeWithTag("mask-tool-画笔").assertIsSelected().assertIsDisplayed()
            compose.onNodeWithTag("mask-tool-撤销").assertIsNotEnabled()
            compose.onNodeWithTag("mask-tool-擦除").performClick().assertIsSelected(); assertEquals(1, selected)
            compose.onNodeWithTag("mask-compare").performClick(); assertTrue(compared)
            compose.onNodeWithTag("editor-slider-画笔大小").assertIsNotEnabled()
            compose.onNodeWithTag("mask-compare").performClick()
            capture(name)
            val canvas = compose.onNodeWithTag("mask-canvas").fetchSemanticsNode().boundsInRoot
            assertTrue("Canvas must retain useful height", canvas.height > 180)
            if (subject) {
                compose.onNodeWithTag("mask-tool-框选").performClick().assertIsSelected()
                assertEquals("Switching tools must not move the photo", canvas.height,
                    compose.onNodeWithTag("mask-canvas").fetchSemanticsNode().boundsInRoot.height, .1f)
                compose.onNodeWithText("识别选区").performScrollTo().performClick(); assertTrue(recognized)
                capture("$name-selection")
            } else compose.onNodeWithTag("mask-recognize").assertDoesNotExist()
            compose.runOnIdle { working = true }
            compose.onNodeWithText("完成").assertIsNotEnabled()
            compose.onNodeWithTag("mask-tool-画笔").assertIsNotEnabled()
            compose.runOnIdle { working = false }
            compose.onNodeWithText("完成").performClick(); assertTrue(saved)
        } finally { host.pause().stop().destroy(); RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun maskToolsAndComparison() = mask("mask")
    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun compactMaskRetainsCanvas() = mask("mask-compact")
    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun largeFontMaskKeepsActionsReachable() = mask("mask-large", true)
    @Test fun paintMaskDoesNotExposeSubjectRecognition() = mask("paint", subject = false)

    private fun clock(name: String, dark: Boolean, large: Boolean = false) {
        RuntimeEnvironment.setFontScale(if (large) 1.4f else 1f)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var applied = false; var styles = false
        try {
            host.get().setContent {
                MiuixTheme(controller = remember { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) }, textStyles = HyperTextStyles) {
                    var options by remember { mutableStateOf(ExtensionOptions.defaults()) }
                    ExtensionEditorContent(options, false, 0, false, "unsupported", "", clockTitle(options.style),
                        {}, { applied = true }, {}, { options = it }, { styles = true }, {}, {}) {}
                }
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("所选时钟的静态样式参考").fetchSemanticsNodes().isNotEmpty() }
            capture(name)
            compose.onNodeWithText("替换息屏时钟").performScrollTo().performClick()
            compose.onNodeWithText("应用").assertIsNotEnabled(); assertFalse(applied)
            compose.onNodeWithTag("extension-styles").performScrollTo().performClick(); assertTrue(styles)
            compose.onNodeWithText("双侧光带").performScrollTo().performClick()
            compose.onNodeWithText("播放光效").performScrollTo().assertIsDisplayed()
            capture("$name-notification")
        } finally { host.pause().stop().destroy(); RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun clockLightHasHonestFallback() = clock("clock-light", false)
    @Test fun clockDarkHasHonestFallback() = clock("clock-dark", true)
    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun compactClockAndLargeText() = clock("clock-compact-large", false, true)

    @Test fun galleryShowsActualClockArtworkAndSelection() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val styles = NativeHyperOsStyles.ALL.take(6)
            host.get().setContent {
                MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }, textStyles = HyperTextStyles) {
                    var selected by remember { mutableStateOf(styles[0].id) }
                    LazyVerticalGrid(GridCells.Fixed(2), modifier = Modifier.fillMaxSize().background(EditorSurface.page), contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(styles) { style -> ClockStyleTile(arrayOf(style.id, style.title), selected == style.id) { selected = style.id } }
                    }
                }
            }
            compose.onNodeWithTag("clock-style-${styles[1].id}").performClick().assertIsSelected()
            compose.waitUntil(15_000) {
                styles.all { compose.onAllNodesWithContentDescription("样式缩略图：${it.title}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            }
            capture("clock-gallery")
            val artwork = loadClockArtwork(host.get(), styles[0].id)!!
            assertTrue("Full-screen black padding must be removed", artwork.height < 500)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun maskCanvasUndoAndComparisonDoNotMutateOriginal() {
        val app = RuntimeEnvironment.getApplication()
        val photo = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val view = VivoMaskCanvas(app, photo, null)
        FrameLayout(app).addView(view)
        var changes = 0; view.onHistoryChanged = Runnable { changes++ }
        try {
            view.layout(0, 0, 100, 200)
            val output = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(output))
            fun tap() {
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val e = MotionEvent.obtain(0, 10, action, 50f, 100f, 0)
                    view.dispatchTouchEvent(e); e.recycle()
                }
            }
            view.showMask = false; tap()
            assertFalse(view.canUndo()); assertEquals(android.graphics.Color.BLACK, view.mask.getPixel(50, 100))
            view.showMask = true; tap()
            assertTrue(view.canUndo()); assertEquals(android.graphics.Color.WHITE, view.mask.getPixel(50, 100))
            assertEquals(android.graphics.Color.RED, view.photo.getPixel(50, 100))
            view.undo(); assertFalse(view.canUndo()); assertEquals(android.graphics.Color.BLACK, view.mask.getPixel(50, 100))
            assertEquals(2, changes)
            output.recycle()
        } finally { view.close() }
    }

    @Test fun clockPreviewFitsTallNativeGeometryWithoutChangingItsLayoutWidth() {
        val frame = ClockPreviewFrame(RuntimeEnvironment.getApplication())
        frame.stage.addView(View(frame.context), FrameLayout.LayoutParams(-1, 1500))
        frame.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, 600, 400)
        assertEquals(frame.resources.displayMetrics.widthPixels, frame.stage.width)
        assertTrue(frame.stage.height * frame.stage.scaleY * 1.35f <= 400.01f)
        assertTrue(frame.stage.translationY >= 0)
    }
}
