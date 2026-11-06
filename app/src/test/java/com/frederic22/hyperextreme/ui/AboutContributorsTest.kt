package com.frederic22.hyperextreme.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.android.controller.ActivityController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutContributorsTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun host(
        dark: Boolean = false,
        fontScale: Float = 1f,
        onOpenUrl: (String) -> Unit = {},
    ): ActivityController<ComponentActivity> {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        host.get().setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MiuixTheme(
                    controller = remember { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) },
                    textStyles = HyperTextStyles,
                ) {
                    Column(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.background).padding(12.dp)) {
                        AboutContributorsCard(onOpenUrl)
                    }
                }
            }
        }
        return host
    }

    private fun screenshot(name: String, dialog: Boolean = false) {
        val node = if (dialog) compose.onNodeWithTag("contributor-platforms") else compose.onRoot()
        val image = node.captureToImage().asAndroidBitmap()
        val file = File("build/reports/about-ui/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertSingleRow() {
        val nodes = AboutContributors.map { contributor ->
            compose.onNodeWithTag("contributor-${contributor.id}")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assertContentDescriptionEquals("${contributor.project} · ${contributor.name}")
        }
        val bounds = nodes.map { it.getUnclippedBoundsInRoot() }
        bounds.forEach { assertEquals(bounds.first().top, it.top) }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
    }

    @Test fun fourContributorsShareOneRowInLightTheme() {
        val host = host()
        try { assertSingleRow(); screenshot("contributors-light") } finally { host.close() }
    }

    @Test fun fourContributorsShareOneRowInDarkTheme() {
        val host = host(dark = true)
        try { assertSingleRow(); screenshot("contributors-dark") } finally { host.close() }
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun compactScreenAndLargeTextKeepAllFourTargetsAccessible() {
        val host = host(fontScale = 1.6f)
        try {
            assertSingleRow()
            screenshot("contributors-compact-large-text")
            compose.onNodeWithTag("contributor-openalive").performClick()
            compose.onNodeWithText("下北沢最速傳說").assertIsDisplayed()
            compose.onNodeWithText("打开Github").assertIsDisplayed()
            compose.onNodeWithText("打开酷安").assertIsDisplayed()
            compose.onNodeWithText("取消").assertIsDisplayed()
            screenshot("platforms-compact-large-text", dialog = true)
        } finally { host.close() }
    }

    @Test fun everyPlatformRoutesToItsSelectedContributorNotThePreviousOne() {
        val opened = mutableListOf<String>()
        val host = host(onOpenUrl = opened::add)
        try {
            AboutContributors.forEach { contributor ->
                contributor.platforms.forEach { (label, url) ->
                    compose.onNodeWithTag("contributor-${contributor.id}").performClick()
                    compose.onNodeWithText(contributor.name).assertIsDisplayed()
                    compose.onNodeWithText(label).performClick()
                    compose.waitForIdle()
                    assertEquals(url, opened.last())
                    compose.onNodeWithTag("contributor-platforms").assertDoesNotExist()
                }
            }
            assertEquals(AboutContributors.sumOf { it.platforms.size }, opened.size)
        } finally { host.close() }
    }

    @Test fun cancellingNeverNavigatesAndNextAvatarGetsItsOwnLinks() {
        val opened = mutableListOf<String>()
        val host = host(onOpenUrl = opened::add)
        try {
            compose.onNodeWithTag("contributor-hyperextreme").performClick()
            compose.onNodeWithText("打开哔哩哔哩").assertIsDisplayed()
            screenshot("platforms-original", dialog = true)
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("contributor-maintainer").performClick()
            compose.onNodeWithText("年华虚度").assertIsDisplayed()
            compose.onNodeWithText("打开哔哩哔哩").assertDoesNotExist()
            screenshot("platforms-maintainer", dialog = true)
            compose.onNodeWithText("取消").performClick()
            assertTrue(opened.isEmpty())
        } finally { host.close() }
    }

    @Test fun hyperAliveOnlyOffersCoolapkAsRequested() {
        val contributor = AboutContributors.single { it.id == "hyperalive" }
        assertEquals(listOf("打开酷安" to "https://www.coolapk.com/u/39588100"), contributor.platforms)
        val host = host()
        try {
            compose.onNodeWithTag("contributor-hyperalive").performClick()
            compose.onNodeWithText("打开Github").assertDoesNotExist()
            compose.onNodeWithText("打开酷安").assertIsDisplayed()
        } finally { host.close() }
    }

    @Test fun publicLinksAndBundledAvatarsRemainValidOffline() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            assertEquals(4, AboutContributors.map { it.id }.distinct().size)
            assertEquals(
                listOf("3268364", "44199044", "39588100", "24655132"),
                AboutContributors.map { contributor -> contributor.platforms.single { it.first == "打开酷安" }.second.substringAfterLast('/') },
            )
            AboutContributors.forEach { contributor ->
                assertFalse(contributor.platforms.isEmpty())
                contributor.platforms.forEach { (_, url) -> assertTrue(url.startsWith("https://")) }
                val bitmap = BitmapFactory.decodeResource(host.get().resources, contributor.avatar)
                assertNotNull(bitmap)
                assertTrue(bitmap.width >= 128 && bitmap.height >= 128)
                bitmap.recycle()
            }
        } finally { host.close() }
    }
}
