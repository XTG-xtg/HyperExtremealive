package com.frederic22.hyperextreme.alive;

import com.frederic22.hyperextreme.alive.core.ModuleIdentity;
import com.frederic22.hyperextreme.alive.core.MusicLockRecovery;
import org.junit.Test;
import static org.junit.Assert.*;

public class MusicLockRecoveryTest {
    private static final String HOME = ModuleIdentity.PACKAGE + "/" + ModuleIdentity.HOME_SERVICE;
    @Test public void recoversUserLogsSystemFallbackWithoutReapplyingHome() {
        assertTrue(MusicLockRecovery.canRestore(HOME,"STATIC"));
        assertTrue(MusicLockRecovery.canRestore(HOME,"com.miui.miwallpaper/com.miui.miwallpaper.wallpaperservice.MiuiKeyguardPictorialWallpaper"));
        assertTrue(MusicLockRecovery.canRestore(HOME,"com.miui.miwallpaper/com.miui.miwallpaper.wallpaperservice.ImageWallpaper"));
    }
    @Test public void neverReclaimsAnotherLiveWallpaperOrHealthyLock() {
        for (String lock : new String[]{ModuleIdentity.PACKAGE+"/"+ModuleIdentity.LOCK_SERVICE,
                "com.example.wallpaper/.Service", ModuleIdentity.PACKAGE+"/"+ModuleIdentity.RASTER_SERVICE})
            assertFalse(MusicLockRecovery.canRestore(HOME,lock));
    }
    @Test public void changingHomeDisarmsAutomaticRecovery() {
        assertFalse(MusicLockRecovery.canRestore("STATIC","STATIC"));
        assertFalse(MusicLockRecovery.canRestore("com.example.wallpaper/.Service","STATIC"));
        assertFalse(MusicLockRecovery.canRestore(null,null));
    }
}
