package com.frederic22.hyperextreme.data

import android.app.Application
import android.content.pm.PackageManager
import com.frederic22.hyperextreme.alive.Protocol
import com.frederic22.hyperextreme.alive.core.ModuleIdentity
import com.frederic22.hyperextreme.wallpaper.RasterWallpaperService
import com.frederic22.hyperextreme.wallpaper.SpatialServiceMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [WallpaperFeaturesTest.FakeWallpapers::class])
class ModuleIdentityTest {
    @Test fun installedComponentsResolveRetainedClassNamespace() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ctx.packageManager.getPackageInfo(ctx.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS)
        assertEquals(ModuleIdentity.PACKAGE, info.packageName)
        assertEquals(22L, info.longVersionCode)
        assertEquals("0.5-alive.7-ui.7", info.versionName)
        val providers = info.providers.orEmpty().map { it.authority }.toSet()
        assertTrue(providers.containsAll(setOf(ModuleIdentity.CONFIG_AUTHORITY, ModuleIdentity.MASK_AUTHORITY,
            ModuleIdentity.XPOSED_AUTHORITY)))
        assertTrue(providers.all { it.startsWith(ModuleIdentity.PACKAGE + ".") })
        val services = info.services.orEmpty().map { it.name }.toSet()
        assertTrue(services.containsAll(listOf(ModuleIdentity.HOME_SERVICE, ModuleIdentity.LOCK_SERVICE,
            ModuleIdentity.SPATIAL_SERVICE, ModuleIdentity.RASTER_SERVICE)))
        (info.activities.orEmpty().map { it.name } + services + info.providers.orEmpty().map { it.name }).forEach {
            Class.forName(it, false, javaClass.classLoader)
        }
        assertEquals(ModuleIdentity.HOME_SERVICE, Protocol.HOME)
        assertEquals(ModuleIdentity.LOCK_SERVICE, Protocol.LOCK)
        assertEquals(ModuleIdentity.CONFIG_AUTHORITY, Protocol.CONFIG.authority)
    }

    @Test fun rasterDisableIsScopedAndPreservesTargetOnFailure() {
        val fixture = WallpaperFeaturesTest()
        fixture.setup()
        val ctx = RuntimeEnvironment.getApplication()
        var cleared = false
        WallpaperFeaturesTest.FakeWallpapers.rejectQuery = true
        val failed = SpatialServiceMode.disable(ctx, RasterWallpaperService::class.java) { cleared = true }
        assertTrue(failed.isFailure)
        assertFalse(cleared)
        WallpaperFeaturesTest.FakeWallpapers.rejectQuery = false
        assertTrue(SpatialServiceMode.disable(ctx, RasterWallpaperService::class.java) { cleared = true }.isSuccess)
        assertTrue(cleared)
    }
}
