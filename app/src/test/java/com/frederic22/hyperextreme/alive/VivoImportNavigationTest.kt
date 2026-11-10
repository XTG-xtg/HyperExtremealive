package com.frederic22.hyperextreme.alive

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VivoImportNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun completedImportOpensOnceAfterBusyClears() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val pending = mutableStateOf(false)
        val busy = mutableStateOf(true)
        var opened = 0
        try {
            host.get().setContent {
                ImportedWallpaperPreviewEffect(pending.value, busy.value) {
                    pending.value = false
                    opened++
                }
            }
            compose.runOnIdle { pending.value = true }
            compose.waitForIdle()
            assertEquals(0, opened)
            compose.runOnIdle { busy.value = false }
            compose.waitForIdle()
            assertEquals(1, opened)
            compose.runOnIdle { busy.value = true }
            compose.runOnIdle { busy.value = false }
            compose.waitForIdle()
            assertEquals(1, opened)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun cancelOrFailedImportDoesNotOpenPreview() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val busy = mutableStateOf(true)
        var opened = 0
        try {
            host.get().setContent {
                ImportedWallpaperPreviewEffect(false, busy.value) { opened++ }
            }
            compose.runOnIdle { busy.value = false }
            compose.waitForIdle()
            assertEquals(0, opened)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun backgroundCompletionWaitsForResume() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val pending = mutableStateOf(false)
        var opened = 0
        try {
            host.get().setContent {
                ImportedWallpaperPreviewEffect(pending.value, false) {
                    pending.value = false
                    opened++
                }
            }
            compose.waitForIdle()
            host.pause()
            compose.runOnIdle { pending.value = true }
            compose.waitForIdle()
            assertEquals(0, opened)
            host.resume()
            compose.waitForIdle()
            assertEquals(1, opened)
        } finally { host.pause().stop().destroy() }
    }
}
