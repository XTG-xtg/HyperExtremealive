package com.frederic22.hyperextreme.alive

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.frederic22.hyperextreme.ui.DropdownRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VivoDropdownWindowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun allPreviewSelectorsOpenAboveBottomSheetAndCommitSelection() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var options = VivoOptions(1)
        var changes = 0
        try {
            host.get().setContent {
                MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }) {
                    var value by remember { mutableStateOf(options) }
                    WindowBottomSheet(show = true, title = "预览设置", onDismissRequest = {}) {
                        Column {
                            DropdownRow("效果范围", listOf("涂抹区域", "仅主体", "仅背景", "整张壁纸"), value.area - 1, {
                                value = value.edited { area = it + 1 }; options = value; changes++
                            })
                            DropdownRow("效果样式", listOf("原版效果 1", "原版效果 2"), value.style - 1, {
                                value = value.edited { style = it + 1 }; options = value; changes++
                            })
                            DropdownRow("主体识别引擎", listOf("自动 · 系统 AI 优先", "仅系统 AI", "仅本地 AI"), value.subjectEngine, {
                                value = value.edited { subjectEngine = it }; options = value; changes++
                            })
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText("效果范围").performClick()
            compose.onNodeWithText("整张壁纸").assertIsDisplayed().performClick()
            compose.waitForIdle(); assertEquals(4, options.area)
            compose.onNodeWithText("效果范围").performClick()
            compose.onNodeWithText("涂抹区域").assertIsDisplayed().performClick()
            compose.waitForIdle(); assertEquals(1, options.area)
            compose.onNodeWithText("效果样式").performClick()
            compose.onNodeWithText("原版效果 2").assertIsDisplayed().performClick()
            compose.waitForIdle(); assertEquals(2, options.style)
            compose.onNodeWithText("主体识别引擎").performClick()
            compose.onNodeWithText("仅本地 AI").assertIsDisplayed().performClick()
            compose.waitForIdle(); assertEquals(2, options.subjectEngine)
            assertEquals(4, changes)
        } finally { host.pause().stop().destroy() }
    }
}
