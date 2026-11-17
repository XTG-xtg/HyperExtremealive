package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class LauncherWallpaperScrollTest {
    private final Context context=RuntimeEnvironment.getApplication();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String key="pref_key_wallpaper_screen_scrolled_span",backup="hyperextreme_alive_scroll_previous";
    private Bundle config(boolean enabled){Bundle b=new Bundle();b.putInt("variant",801);b.putBoolean("configured",true);b.putBoolean("launcher_scroll",enabled);return b;}
    private void update(LauncherWallpaperScroll lease,boolean enabled,boolean owned){lease.refreshForUser(0,context,handler,config(enabled),owned,()->{});}
    private int value(){return Settings.Secure.getInt(context.getContentResolver(),key,0);}
    @Before public void time(){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));}
    @Test public void allInfinityVariantsRequireAppliedOwnedHomeAndOptIn(){
        for(int v=801;v<=820;v++){
            Bundle b=config(true);b.putInt("variant",v);assertTrue(LauncherWallpaperScroll.wants(b,true));
            assertFalse(LauncherWallpaperScroll.wants(b,false));b.putBoolean("configured",false);assertFalse(LauncherWallpaperScroll.wants(b,true));
        }
        for(int v:new int[]{1,701,800,821}){Bundle b=config(true);b.putInt("variant",v);assertFalse(LauncherWallpaperScroll.wants(b,true));}
        assertFalse(LauncherWallpaperScroll.wants(config(false),true));
    }
    @Test public void offAndWallpaperSwitchRestoreTheOriginalFlag(){
        Settings.Secure.putInt(context.getContentResolver(),key,0);
        LauncherWallpaperScroll lease=new LauncherWallpaperScroll();update(lease,true,true);assertEquals(1,value());
        update(lease,false,true);assertEquals(0,value());
        update(lease,true,true);update(lease,true,false);assertEquals(0,value());
        assertNull(Settings.Secure.getString(context.getContentResolver(),backup));
    }
    @Test public void nativeEnabledOrExternalChangesArePreserved(){
        Settings.Secure.putInt(context.getContentResolver(),key,1);
        LauncherWallpaperScroll lease=new LauncherWallpaperScroll();update(lease,true,true);update(lease,false,true);assertEquals(1,value());
        Settings.Secure.putInt(context.getContentResolver(),key,0);update(lease,true,true);
        Settings.Secure.putInt(context.getContentResolver(),key,2);update(lease,false,true);assertEquals(2,value());
    }
    @Test public void processRestartCanRestoreThePersistedOriginal(){
        Settings.Secure.putInt(context.getContentResolver(),key,0);
        LauncherWallpaperScroll first=new LauncherWallpaperScroll();update(first,true,true);assertEquals(1,value());
        LauncherWallpaperScroll restarted=new LauncherWallpaperScroll();update(restarted,false,true);assertEquals(0,value());
        assertNull(Settings.Secure.getString(context.getContentResolver(),backup));
    }
}
