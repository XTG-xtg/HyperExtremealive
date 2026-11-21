package com.frederic22.hyperextreme.data

import org.junit.Assert.*
import org.junit.Test

class WallpaperChangeGuardTest {
    private val old="tpl2:content:old"
    private val new="tpl2:content:new"
    @Test fun committedChangeWithCurrentBaselineIsAccepted() {
        assertTrue(WallpaperChangeGuard.accepts(true,old,old,new,new))
    }
    @Test fun staleEventCannotStopANewlyAppliedWallpaper() {
        assertFalse(WallpaperChangeGuard.accepts(true,new,old,new,new))
        assertFalse(WallpaperChangeGuard.accepts(true,old,old,new,"tpl2:content:later"))
    }
    @Test fun migrationMissingEvidenceAndStoppedFeaturesDoNotStop() {
        assertFalse(WallpaperChangeGuard.accepts(true,"tpl:old","tpl:old",new,new))
        assertFalse(WallpaperChangeGuard.accepts(true,old,null,null,new))
        assertFalse(WallpaperChangeGuard.accepts(true,old,old,new,""))
        assertFalse(WallpaperChangeGuard.accepts(false,old,old,new,new))
        assertFalse(WallpaperChangeGuard.accepts(true,MaskPrefs.FINGERPRINT_WEAK,MaskPrefs.FINGERPRINT_WEAK,new,new))
    }
}
