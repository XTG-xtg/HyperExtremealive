package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frederic22.hyperextreme.ui.HyperTextStyles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AliveHomeScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun capture(name: String, dark: Boolean, error: Boolean = false, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = host.get()
            // Historical real-renderer stills are test inputs, not a new GPU/device acceptance test.
            val frames = (0..2).map { mode ->
                javaClass.getResourceAsStream("/alive/layout-1-$mode.png").use { input ->
                    requireNotNull(BitmapFactory.decodeStream(requireNotNull(input))).asImageBitmap()
                }
            }
            val texture = activity.assets.open("stereo/preview_v6.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
            var selected = -1
            var library = false
            var toggle: Boolean? = null
            activity.setContent {
                val controller = remember { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) }
                MiuixTheme(controller = controller, textStyles = HyperTextStyles) {
                    AliveHomeContent(AliveHomeModel(name = "星云绿", running = false, busy = false, frameworkConnected = true,
                        frames = if (error) emptyList() else frames, photo = texture,
                        previewError = if (error) "Test unavailable renderer" else ""),
                        onBack = {}, onDiagnostics = {}, onMode = { selected = it }, onPhoto = { selected = it },
                        onLibrary = { library = true }, onToggle = { toggle = it }, onRetry = {})
                }
            }
            compose.waitForIdle()
            val heights = listOf("息屏", "锁屏", "桌面").map {
                compose.onNodeWithTag("alive-layout-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
            }
            org.junit.Assert.assertEquals(heights[0], heights[1], .1f)
            org.junit.Assert.assertEquals(heights[1], heights[2], .1f)
            val directory = File("build/reports/alive-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithText("锁屏").performClick()
            org.junit.Assert.assertEquals(1, selected)
            compose.onNodeWithText("动态壁纸").performScrollTo().performClick()
            org.junit.Assert.assertTrue(library)
            compose.onNodeWithText("启用 Alive 壁纸").performScrollTo().performClick()
            org.junit.Assert.assertEquals(true, toggle)
            compose.onNodeWithText("圆景").performScrollTo().performClick()
            org.junit.Assert.assertEquals(601, selected)
            compose.onNodeWithText("留光").performScrollTo().performClick()
            org.junit.Assert.assertEquals(602, selected)
            compose.onNodeWithText("留光").performScrollTo()
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(directory, "$name-bottom.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { host.pause().stop().destroy(); RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun light() = capture("alive-home-light", false)
    @Test fun dark() = capture("alive-home-dark", true)
    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi") fun compact() = capture("alive-home-compact", false)
    @Test fun unavailablePreview() = capture("alive-home-error", true, true)
    @Test fun largeText() = capture("alive-home-large-text", false, fontScale = 1.4f)

    @Test fun dynamicLibraryContainsOnlyPresetScenesAndOpensTheirEditor() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = host.get()
            activity.setContent {
                val controller = remember { ThemeController(ColorSchemeMode.Dark) }
                MiuixTheme(controller = controller, textStyles = HyperTextStyles) { AliveLibrary {} }
            }
            fun waitForImage(name: String) = compose.waitUntil(10_000) {
                compose.onAllNodes(hasContentDescription(name), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            fun save(name: String) {
                val directory = File("build/reports/alive-ui").apply { mkdirs() }
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            waitForImage("星云绿")
            compose.onNodeWithText("圆景").assertDoesNotExist()
            compose.onNodeWithText("留光").assertDoesNotExist()
            compose.onNodeWithText("照片").assertDoesNotExist()
            save("alive-library-cosmic")
            compose.onNodeWithText("流光").performClick()
            waitForImage("大漠")
            save("alive-library-gradient")
            compose.onNodeWithText("大漠").performClick()
            val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
            org.junit.Assert.assertEquals(AlivePreviewActivity::class.java.name, intent.component?.className)
            org.junit.Assert.assertEquals(301, intent.getIntExtra("variant", -1))
            org.junit.Assert.assertEquals(1, intent.getIntExtra("mode", -1))
        } finally { host.pause().stop().destroy() }
    }

    @Test fun editorChromeSelectsStateAndOnlyEmitsExplicitActions() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = host.get()
            var selected = -1
            var applied = false
            var cancelled = false
            var cropped = false
            activity.setContent {
                val controller = remember { ThemeController(ColorSchemeMode.Dark) }
                MiuixTheme(controller = controller, textStyles = HyperTextStyles) {
                    val bitmap = remember {
                        javaClass.getResourceAsStream("/alive/layout-1-1.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
                    }
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        androidx.compose.foundation.Image(bitmap, null, androidx.compose.ui.Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        AliveEditorChrome("星云绿", 1, false, true, "预览不会改变正在使用的壁纸",
                            listOf(AliveEditorTool("取景", AliveToolIcon.CROP) { cropped = true },
                                AliveEditorTool("选项", AliveToolIcon.OPTIONS) {}, AliveEditorTool("演示", AliveToolIcon.PLAY) {}),
                            onCancel = { cancelled = true }, onApply = { applied = true }, onMode = { selected = it })
                    }
                }
            }
            compose.waitForIdle()
            val directory = File("build/reports/alive-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(directory, "alive-editor.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithText("锁屏").assertIsSelected()
            compose.onNodeWithText("桌面").performClick()
            org.junit.Assert.assertEquals(2, selected)
            org.junit.Assert.assertFalse(applied)
            compose.onNodeWithText("取景").performClick()
            org.junit.Assert.assertTrue(cropped)
            compose.onNodeWithText("应用").performClick()
            org.junit.Assert.assertTrue(applied)
            compose.onNodeWithText("取消").performClick()
            org.junit.Assert.assertTrue(cancelled)
        } finally { host.pause().stop().destroy() }
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun editorBusyAndUnavailableActionsAreDisabled() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            host.get().setContent {
                val controller = remember { ThemeController(ColorSchemeMode.Dark) }
                MiuixTheme(controller = controller, textStyles = HyperTextStyles) {
                    AliveEditorChrome("圆景", 0, true, false, "正在导入照片",
                        listOf(AliveEditorTool("照片", AliveToolIcon.PHOTO) {},
                            AliveEditorTool("位置", AliveToolIcon.POSITION) {}, AliveEditorTool("取景", AliveToolIcon.CROP) {}),
                        onCancel = { error("Disabled cancel") }, onApply = { error("Disabled apply") },
                        onMode = { error("Disabled mode") })
                }
            }
            compose.onNodeWithText("处理中").assertIsNotEnabled()
            compose.onNodeWithText("取消").assertIsNotEnabled()
            compose.onNodeWithText("息屏").assertIsNotEnabled()
            compose.onNodeWithText("照片").assertIsNotEnabled()
        } finally { host.pause().stop().destroy() }
    }

    @Test fun editorPhotoModeKeepsFiveToolsTappable() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = host.get()
            var picked = false
            activity.setContent {
                val controller = remember { ThemeController(ColorSchemeMode.Dark) }
                MiuixTheme(controller = controller, textStyles = HyperTextStyles) {
                    val bitmap = remember {
                        javaClass.getResourceAsStream("/alive/layout-1-0.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
                    }
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        androidx.compose.foundation.Image(bitmap, null, androidx.compose.ui.Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        AliveEditorChrome("圆景", 0, false, true, "调整仅保留为草稿，应用后生效",
                            listOf(AliveEditorTool("照片", AliveToolIcon.PHOTO) { picked = true },
                                AliveEditorTool("位置", AliveToolIcon.POSITION) {},
                                AliveEditorTool("取景", AliveToolIcon.CROP) {},
                                AliveEditorTool("选项", AliveToolIcon.OPTIONS) {},
                                AliveEditorTool("演示", AliveToolIcon.PLAY) {}),
                            onCancel = {}, onApply = {}, onMode = {})
                    }
                }
            }
            compose.waitForIdle()
            val directory = File("build/reports/alive-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(directory, "alive-editor-photo.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            // 五个工具都必须保持至少 48dp 的点击区域，不能被横向压缩。
            listOf("照片", "位置", "取景", "选项", "演示").forEach { label ->
                val node = compose.onNodeWithTag("alive-tool-$label", useUnmergedTree = true).fetchSemanticsNode()
                org.junit.Assert.assertTrue("$label 点击区域过小", node.boundsInRoot.height >= 48f)
                org.junit.Assert.assertTrue("$label 点击区域过窄", node.boundsInRoot.width >= 48f)
            }
            compose.onNodeWithText("照片").performClick()
            org.junit.Assert.assertTrue(picked)
        } finally { host.pause().stop().destroy() }
    }
    @Test fun panelSegmentsAreEvenAndCentred() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var selected = 0
            host.get().setContent {
                val controller = remember { ThemeController(ColorSchemeMode.Dark) }
                MiuixTheme(controller = controller, textStyles = HyperTextStyles) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxSize()
                            .background(Color(0xFF282828))
                            .testTag("alive-segment-host")
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        Text("锁屏纹理", color = Color.White, fontSize = 20.sp)
                        AliveSegments(listOf("原图", "条纹玻璃", "曲面玻璃"), selected) { selected = it }
                        Text("只改变锁屏纹理，桌面保留原图。", style = MiuixTheme.textStyles.body2)
                    }
                }
            }
            compose.waitForIdle()
            val labels = listOf("原图", "条纹玻璃", "曲面玻璃")
            val segments = labels.associateWith {
                compose.onNodeWithTag("alive-segment-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
            val hostBounds = compose.onNodeWithTag("alive-segment-host", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // 等宽、等高、不越出弹窗内容区。
            val first = segments.getValue("原图")
            labels.forEach { label ->
                val bounds = segments.getValue(label)
                // 等分权重在整像素上会差 1px，允许 1.5px 误差。
                org.junit.Assert.assertEquals("$label 宽度与首项不一致", first.width, bounds.width, 1.5f)
                org.junit.Assert.assertEquals("$label 高度与首项不一致", first.height, bounds.height, .5f)
                org.junit.Assert.assertTrue("$label 超出弹窗内容区", bounds.left >= hostBounds.left - .5f && bounds.right <= hostBounds.right + .5f)
                org.junit.Assert.assertTrue("$label 太矮", bounds.height >= 42f)
            }
            // 文字必须在分段里垂直居中，而不是贴顶。
            labels.forEach { label ->
                val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val box = segments.getValue(label)
                org.junit.Assert.assertEquals("$label 文字未垂直居中", box.center.y, text.center.y, 1f)
            }
            val directory = File("build/reports/alive-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(directory, "alive-panel-segments.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithText("曲面玻璃").performClick()
            org.junit.Assert.assertEquals(2, selected)
        } finally { host.pause().stop().destroy() }
    }
}
