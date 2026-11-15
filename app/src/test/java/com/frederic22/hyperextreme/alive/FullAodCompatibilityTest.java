package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.ComponentName;
import com.frederic22.hyperextreme.alive.core.ModuleIdentity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class FullAodCompatibilityTest {
    private ComponentName own(String service) { return new ComponentName(ModuleIdentity.PACKAGE,service); }
    @Test public void onlyOurAmbientHomeAndLockServicesQualify() {
        assertTrue(FullAodCompatibility.eligible(own(ModuleIdentity.LOCK_SERVICE),true,true,"image"));
        assertTrue(FullAodCompatibility.eligible(own(ModuleIdentity.HOME_SERVICE),true,true,"image"));
        assertFalse(FullAodCompatibility.eligible(own(ModuleIdentity.RASTER_SERVICE),true,true,"image"));
        assertFalse(FullAodCompatibility.eligible(own(ModuleIdentity.SPATIAL_SERVICE),true,true,"image"));
    }
    @Test public void thirdPartyOrUnknownBindingsNeverQualify() {
        assertFalse(FullAodCompatibility.eligible(new ComponentName("other.app",ModuleIdentity.LOCK_SERVICE),true,true,"image"));
        assertFalse(FullAodCompatibility.eligible(null,true,true,"image"));
        assertFalse(FullAodCompatibility.eligible(own("other.Service"),true,true,"image"));
    }
    @Test public void realThemeAndAmbientRestrictionsRemain() {
        ComponentName lock=own(ModuleIdentity.LOCK_SERVICE);
        assertFalse(FullAodCompatibility.eligible(lock,false,true,"image"));
        assertFalse(FullAodCompatibility.eligible(lock,true,false,"maml"));
        assertFalse(FullAodCompatibility.eligible(lock,true,true,"super_wallpaper"));
        assertFalse(FullAodCompatibility.eligible(lock,true,true,null));
    }
}
