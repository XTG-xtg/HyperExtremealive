package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class ForcedAodSupportTest {
    @Test public void explicitSystemOptInDoesNotRequireTheAodSwitchToRemainOn() {
        Context c=RuntimeEnvironment.getApplication();
        Settings.Secure.putInt(c.getContentResolver(),SystemAodControl.FORCE,0);
        assertFalse(ForcedAodSupport.active(c));assertFalse(ForcedAodSupport.active(null));
        Settings.Secure.putInt(c.getContentResolver(),SystemAodControl.FORCE,1);
        Settings.Secure.putInt(c.getContentResolver(),SystemAodControl.ENABLED,0);
        assertTrue(ForcedAodSupport.active(c));
        assertEquals(0,Settings.Secure.getInt(c.getContentResolver(),SystemAodControl.ENABLED,-1));
        Settings.Secure.putInt(c.getContentResolver(),SystemAodControl.FORCE,0);
        assertFalse(ForcedAodSupport.active(c));
    }
    @Test public void onlyKnownFullscreenFeatureKeysAreOverridden() {
        assertTrue(ForcedAodSupport.feature("support_aod_fullscreen"));
        assertTrue(ForcedAodSupport.feature("inner_screen_support_aod_fullscreen"));
        assertFalse(ForcedAodSupport.feature("support_aod_aon"));assertFalse(ForcedAodSupport.feature("support_low_brightness_fod"));
    }
    @Test public void unsupportedFinalFlagsCanBePreparedBeforeManagerConstruction() throws Exception {
        ClassLoader loader=getClass().getClassLoader();
        Class<?> configs=Class.forName("com.miui.utils.configs.MiuiConfigs",true,loader);
        assertFalse(configs.getField("SUPPORT_FULL_AOD").getBoolean(null));
        ForcedAodSupport.prepare(loader);
        assertTrue(configs.getField("SUPPORT_FULL_AOD").getBoolean(null));
        assertTrue(configs.getField("INNER_SCREEN_SUPPORT_FULL_AOD").getBoolean(null));
    }
}
