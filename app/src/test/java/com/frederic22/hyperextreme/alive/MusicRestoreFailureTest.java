package com.frederic22.hyperextreme.alive;

import android.app.Application;
import com.frederic22.hyperextreme.data.WallpaperFeaturesTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=Application.class, shadows=WallpaperFeaturesTest.FakeWallpapers.class)
public class MusicRestoreFailureTest {
    @Test public void wallpaperQueryFailureDoesNotEscapeTheRestoreWorker() {
        android.content.Context context=RuntimeEnvironment.getApplication();
        com.frederic22.hyperextreme.alive.Config.prefs(context).edit().putBoolean("enabled",true).commit();
        ReflectionHelpers.setStaticField(AliveMusicRestore.class,"musicScreen",false);
        ReflectionHelpers.setStaticField(WallpaperFeaturesTest.FakeWallpapers.class,"rejectQuery",true);
        try {
            ReflectionHelpers.callStaticMethod(AliveMusicRestore.class,"apply",
                ReflectionHelpers.ClassParameter.from(android.content.Context.class,context));
        } finally { ReflectionHelpers.setStaticField(WallpaperFeaturesTest.FakeWallpapers.class,"rejectQuery",false); }
    }
}
