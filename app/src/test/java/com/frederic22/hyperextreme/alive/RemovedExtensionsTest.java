package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class RemovedExtensionsTest {
    @Test public void legacyEnabledSettingsCannotRestartRetiredClockOrNotificationFeatures() {
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("alive",0).edit().putBoolean("clock",true).putInt("variant",803).commit();
        context.getSharedPreferences("alive_extensions",0).edit().putBoolean("clock",true)
                .putInt("light",2).putString("style","flyme-2").putFloat("scale",1.2f).commit();
        HashMap<String,Object> before=new HashMap<>(context.getSharedPreferences("alive",0).getAll());
        Bundle config=com.frederic22.hyperextreme.alive.Config.read(context);
        assertFalse(config.getBoolean("clock"));
        assertFalse(config.containsKey("ext_clock"));assertFalse(config.containsKey("ext_light"));
        assertEquals(803,config.getInt("variant"));
        assertEquals(before,context.getSharedPreferences("alive",0).getAll());
    }

    @Test public void retiredResourcesAndEntryAreAbsentWhileWallpaperFamiliesRemainAvailable() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        for(String name:new String[]{"native-clock","notification","ui"})
            assertFalse(Arrays.asList(context.getAssets().list("")).contains(name));
        for(String name:new String[]{"native-clock/runtime/hyperos/runtime.apk","notification/circle.svga","ui/editor-ui.apk"})
            assertThrows(IOException.class,()->context.getAssets().open(name));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.frederic22.hyperextreme.alive.AliveExtensionsActivity"));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.frederic22.hyperextreme.alive.HyperOsExtensionHooks"));
        for(int variant:new int[]{1,301,401,501,601,701,702,703,803}) {
            assertTrue(AliveCatalog.load(context).stream().anyMatch(scene->scene.id==variant));
        }
    }
}
