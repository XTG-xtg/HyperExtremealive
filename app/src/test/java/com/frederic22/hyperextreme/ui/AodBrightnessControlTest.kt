package com.frederic22.hyperextreme.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AodBrightnessControlTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun fixedLinearTicksMatchNumbersAndDraggingNeverMovesBackwards() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var applied=0f
        val changes=mutableListOf<Float>()
        try {
            host.get().setContent {
                var value by remember { mutableFloatStateOf(applied) }
                MiuixTheme { AodBrightnessControl(value){value=it;applied=it;changes.add(it)} }
            }
            val slider=compose.onNodeWithTag("aod-brightness-slider")
            for(step in 0..20) {
                val value=step*.25f
                slider.performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
                compose.runOnIdle { assertEquals(value,applied,0f) }
                val range=slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
                assertEquals(0f..5f,range.range);assertEquals(19,range.steps);assertEquals(value,range.current,0f)
                compose.onNodeWithTag("aod-brightness-value").assertTextEquals(if(value==0f)"0 · 不修改" else "${brightnessLabel(value)}×")
            }
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
            compose.runOnIdle { changes.clear() }
            slider.performTouchInput {
                down(Offset(1f,centerY))
                for(step in 1..60) moveTo(Offset((width-1f)*step/60f,centerY),delayMillis=16)
                up()
            }
            compose.runOnIdle {
                assertEquals(5f,applied,0f)
                assertTrue(changes.size>10)
                assertTrue(changes.zipWithNext().all { (before,after)->after>=before })
                assertTrue(changes.all { it in 0f..5f && it*4==(it*4).toInt().toFloat() })
            }
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2.5f) }
            compose.onNodeWithText("1×").assertDoesNotExist()
            compose.onNodeWithText("2×").assertDoesNotExist()
            compose.onNodeWithText("5×").assertDoesNotExist()
            compose.onNodeWithTag("aod-brightness-input").assertDoesNotExist()
            val directory=java.io.File("build/reports/aod-brightness-ui").apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(directory,"brightness-control.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            }
            compose.runOnIdle {assertEquals(2.5f,applied,0f)}
        } finally {host.pause().stop().destroy()}
    }
}
