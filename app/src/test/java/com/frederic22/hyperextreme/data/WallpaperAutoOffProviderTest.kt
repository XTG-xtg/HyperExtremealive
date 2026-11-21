package com.frederic22.hyperextreme.data

import android.app.Application
import android.os.Bundle
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class WallpaperAutoOffProviderTest {
    @Test fun oldOrMissingEvidenceCannotDisableNewSpatialSelection() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=MaskPrefs(context)
        val json="""{"lockscreenInfo":{"wallpaperInfo":{"source":"/new-picture","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}}"""
        Settings.Secure.putString(context.contentResolver,WallpaperVersion.TEMPLATE_KEY,json)
        val current=WallpaperVersion.computeFrom(context,json).value
        prefs.setSpatialActive("7a01c52648de2320")
        prefs.setSpatialWallpaperFp(current)
        val provider=MaskProvider().apply { attachInfo(context,null) }
        val stale=Bundle().apply {
            putString(MaskProvider.KEY_EXPECTED_WALLPAPER_FP,"tpl2:content:old")
            putString(MaskProvider.KEY_OBSERVED_WALLPAPER_FP,current)
        }
        provider.call(MaskProvider.METHOD_SPATIAL_AUTO_OFF,null,stale)
        assertTrue(prefs.spatialActive);assertEquals(current,prefs.spatialWallpaperFp)
        provider.call(MaskProvider.METHOD_SPATIAL_AUTO_OFF,null,null)
        assertTrue(prefs.spatialActive);assertFalse(prefs.spatialStopPending)
    }
}
