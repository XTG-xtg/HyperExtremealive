package com.frederic22.hyperextreme.alive

import android.app.Activity
import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.frederic22.hyperextreme.ui.HyperTextStyles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DynamicPackUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun content(activity: ComponentActivity,dark: Boolean=false) {
        DynamicPackFixture.storage(activity)
        activity.setContent {
            MiuixTheme(controller=remember { ThemeController(if(dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) },textStyles=HyperTextStyles) {
                AliveLibrary {}
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dynamic-pack-empty").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val dir=File("build/reports/dynamic-pack-ui").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun missingLibraryUsesDownloadChooserAndCopiesQuarkWithoutDisplayingShareText() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity=host.get();content(activity)
            compose.onNodeWithText("导入动态壁纸").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("alive-library-grid").assertDoesNotExist()
            aliveDynamicLabels.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
            val importBounds=compose.onNodeWithText("导入动态壁纸").fetchSemanticsNode().boundsInRoot
            val downloadBounds=compose.onNodeWithText("去下载").fetchSemanticsNode().boundsInRoot
            val emptyBounds=compose.onNodeWithTag("dynamic-pack-empty").fetchSemanticsNode().boundsInRoot
            assertTrue(importBounds.bottom<downloadBounds.top)
            assertEquals(emptyBounds.center.x,importBounds.center.x,1f)
            assertEquals(emptyBounds.center.x,downloadBounds.center.x,1f)
            assertEquals(emptyBounds.center.y,(importBounds.center.y+downloadBounds.center.y)/2,2f)
            screenshot("dynamic-pack-light")
            compose.onNodeWithText("去下载").performClick()
            compose.onNodeWithText("夸克网盘").assertIsDisplayed()
            compose.onNodeWithText("GitHub").assertIsDisplayed()
            compose.onNodeWithText(DynamicPackDownloads.quarkShare).assertDoesNotExist()
            compose.onNodeWithText("pan.quark.cn",substring=true).assertDoesNotExist()
            compose.onNodeWithText("夸克网盘").performClick()
            compose.runOnIdle {
                val clip=activity.getSystemService(ClipboardManager::class.java).primaryClip!!
                assertEquals(DynamicPackDownloads.quarkShare,clip.getItemAt(0).text.toString())
                assertTrue(clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
                assertNull(shadowOf(activity).nextStartedActivity)
            }
            compose.onNodeWithText("去下载").performClick()
            compose.onNodeWithText("GitHub").performClick()
            compose.runOnIdle {
                val intent=shadowOf(activity).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW,intent.action)
                assertEquals("https://github.com/XTG-xtg/HyperExtremealive",intent.dataString)
            }
        } finally { host.pause().stop().destroy() }
    }
    @Test fun missingLibraryDarkTheme() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try { content(host.get(),true);screenshot("dynamic-pack-dark") }
        finally { host.pause().stop().destroy() }
    }
    @Test fun selectingZipImportsResourcesAndRevealsScenesWithoutChangingWallpaper() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity=host.get();content(activity)
            val before=HashMap(com.frederic22.hyperextreme.alive.Config.prefs(activity).all)
            compose.onNodeWithText("导入动态壁纸").performClick()
            val request=shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,request.intent.action)
            assertTrue(request.intent.categories.contains(Intent.CATEGORY_OPENABLE))
            val uri=Uri.parse("content://test-dynamic-pack/pack.zip")
            shadowOf(activity.contentResolver).registerInputStream(uri,DynamicPackFixture.file.inputStream())
            compose.runOnIdle { activity.activityResultRegistry.dispatchResult(request.requestCode,Activity.RESULT_OK,Intent().setData(uri)) }
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("alive-library-grid").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("星云绿").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(DynamicResources.installed(activity))
            assertEquals(before,com.frederic22.hyperextreme.alive.Config.prefs(activity).all)
            compose.onNodeWithText("导入动态壁纸").assertDoesNotExist()
            compose.onNodeWithText("去下载").assertDoesNotExist()
            compose.onNodeWithText("启航").assertIsDisplayed()
            screenshot("dynamic-pack-imported")
            compose.onNodeWithText("流光").performClick()
            compose.onNodeWithText("大漠").performClick()
            val preview=shadowOf(activity).nextStartedActivity
            assertEquals(301,preview.getIntExtra("variant",-1))
        } finally { host.pause().stop().destroy() }
    }
}
