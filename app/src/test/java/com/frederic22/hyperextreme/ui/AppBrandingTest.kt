package com.frederic22.hyperextreme.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.frederic22.hyperextreme.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppBrandingTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun forkHasIndependentPackageAndPreservesOriginalAuthorCredit() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val context = host.get()
            val name = context.getString(R.string.app_name)
            assertEquals("HyperExtreme Alive", name)
            assertEquals(name, context.applicationInfo.loadLabel(context.packageManager).toString())
            assertEquals("com.xtg.hyperextremealive", context.packageName)
            assertTrue(context.getString(R.string.alive_framework_note, name).contains(name))
            assertEquals("HyperExtreme", AboutContributors.single { it.id == "hyperextreme" }.project)
        } finally { host.close() }
    }

    @Test fun homeCardDisplaysTheForkName() = verifyCard("branding-home")

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun homeCardFitsTheForkNameOnCompactScreens() = verifyCard("branding-home-compact")

    private fun verifyCard(screenshot: String) {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            host.get().setContent {
                MiuixTheme(
                    controller = remember { ThemeController(ColorSchemeMode.Light) },
                    textStyles = HyperTextStyles,
                ) {
                    BigStatusCard(activated = true, hasRoot = true, version = "0.5-alive.7-ui.5", settled = true)
                }
            }
            compose.onNodeWithText("HyperExtreme Alive").assertIsDisplayed()
            compose.onNodeWithText("HyperExtreme").assertDoesNotExist()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val file = File("build/reports/about-ui/$screenshot.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { host.close() }
    }
}
