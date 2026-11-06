package com.frederic22.hyperextreme.data

import android.app.Application
import android.content.ContextWrapper
import com.frederic22.hyperextreme.wallpaper.RasterServiceTarget
import com.frederic22.hyperextreme.wallpaper.SpatialServiceTarget
import com.frederic22.hyperextreme.wallpaper.SpatialServiceMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [WallpaperFeaturesTest.FakeWallpapers::class])
class ServiceTargetTest {
    @Test fun independentTargetsRoundTripAndClampParameters() {
        val ctx = RuntimeEnvironment.getApplication()
        SpatialServiceTarget.write(ctx, "0123456789abcdef").getOrThrow()
        RasterServiceTarget.write(ctx, "123456", 90, false).getOrThrow()
        assertEquals("0123456789abcdef", SpatialServiceTarget.readKey(ctx))
        assertEquals("123456", RasterServiceTarget.readKey(ctx))
        assertEquals(45, RasterServiceTarget.readPanDeg(ctx))
        assertFalse(RasterServiceTarget.readSettle(ctx))
        assertTrue(RasterServiceTarget.write(ctx, "../invalid").isFailure)
        assertEquals("123456", RasterServiceTarget.readKey(ctx))
    }

    @Test fun unwritableTargetsReportFailure() {
        val app = RuntimeEnvironment.getApplication()
        val blocker = File(app.filesDir, "not-a-directory").apply { writeText("keep") }
        val ctx = object : ContextWrapper(app) { override fun getFilesDir(): File = blocker }
        assertTrue(SpatialServiceTarget.write(ctx, "0123456789abcdef").isFailure)
        assertTrue(RasterServiceTarget.write(ctx, "123456").isFailure)
        assertEquals("keep", blocker.readText())
    }

    @Test fun preparationFailureCannotOpenPickerOrMarkHookActive() {
        WallpaperFeaturesTest().setup()
        val ctx = RuntimeEnvironment.getApplication()
        assertThrows(IOException::class.java) {
            SpatialServiceMode.enable(ctx, "0123456789abcdef", writeTarget = { _, _ -> throw IOException("full") })
        }
        assertFalse(MaskPrefs(ctx).spatialActive)
        assertNull(org.robolectric.Shadows.shadowOf(ctx).nextStartedActivity)
    }
}
