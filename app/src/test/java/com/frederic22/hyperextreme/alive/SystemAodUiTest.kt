package com.frederic22.hyperextreme.alive

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w393dp-h873dp-xhdpi")
class SystemAodUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun actualSystemStateDrivesTheSwitchInBothWallpaperFamilies() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var model by mutableStateOf(AliveHomeModel(busy=false,aodEnabled=false))
            var infinity by mutableStateOf(false)
            var requested: Boolean?=null
            var forceRequested: Boolean?=null
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Light) }) {
                    AliveHomeContent(model,onBack={},onDiagnostics={},onMode={},onPhoto={},onLibrary={},
                        onToggle={},onRetry={},onNew={},infinity=infinity,onAodToggle={requested=it},onForceAod={forceRequested=it})
                }
            }
            compose.onNodeWithText("系统息屏显示").performScrollTo().performClick()
            assertEquals(true,requested)
            compose.runOnIdle { model=model.copy(aodEnabled=true); infinity=true }
            compose.onNodeWithText("系统息屏显示").performScrollTo().performClick()
            assertEquals(false,requested)
            compose.onNodeWithText("强制息屏支持").performScrollTo().performClick()
            assertEquals(true,forceRequested)
            compose.runOnIdle { model=model.copy(aodUpdating=true) }
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("system-aod-switch")),useUnmergedTree=true).assertIsNotEnabled()
            compose.runOnIdle { model=model.copy(aodUpdating=false,aodEnabled=null,aodError="读取失败") }
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("system-aod-switch")),useUnmergedTree=true).assertIsNotEnabled()
            compose.onNodeWithText("息屏状态读取失败，点击重试").assertExists()
        } finally { host.pause().stop().destroy() }
    }
}
