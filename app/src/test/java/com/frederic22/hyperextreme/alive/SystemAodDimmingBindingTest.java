package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import com.frederic22.hyperextreme.alive.core.ModuleIdentity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowWallpaperManager;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class,shadows=SystemAodDimmingBindingTest.Wallpapers.class)
public class SystemAodDimmingBindingTest {
    @Implements(WallpaperManager.class)
    public static class Wallpapers extends ShadowWallpaperManager {
        @RealObject WallpaperManager real;
        static WallpaperInfo home,lock;
        static int lockId,queriedUser;
        @Implementation protected WallpaperInfo getWallpaperInfo(int which){
            Context context=ReflectionHelpers.getField(real,"mContext");
            queriedUser=ReflectionHelpers.callInstanceMethod(context,"getUserId");
            return which==WallpaperManager.FLAG_LOCK?lock:home;
        }
        @Implementation protected int getWallpaperId(int which){return lockId;}
    }
    public static class FullAod {
        public boolean enabled=true;
        public boolean fullAodEnable(){return enabled;}
    }
    public static class Users {
        public int user;
        public int getSelectedUserId(){return user;}
    }
    public static class Panel {
        public Context context=RuntimeEnvironment.getApplication();
        public FullAod miuiFullAodManager=new FullAod();
        public Users selectedUserInteractor=new Users();
    }
    private WallpaperInfo info(String service){
        ResolveInfo resolve=new ResolveInfo();resolve.serviceInfo=new ServiceInfo();
        resolve.serviceInfo.packageName=ModuleIdentity.PACKAGE;resolve.serviceInfo.name=service;
        WallpaperInfo info=ReflectionHelpers.newInstance(WallpaperInfo.class);
        ReflectionHelpers.setField(info,"mService",resolve);return info;
    }
    @Before public void setup(){Wallpapers.home=null;Wallpapers.lock=null;Wallpapers.lockId=-1;Wallpapers.queriedUser=-1;}
    @Test public void reflectedRomContractUsesSelectedUserAndRealLockService(){
        Panel panel=new Panel();panel.selectedUserInteractor.user=10;
        for(String service:new String[]{ModuleIdentity.HOME_SERVICE,ModuleIdentity.LOCK_SERVICE,
                ModuleIdentity.SPATIAL_SERVICE,ModuleIdentity.RASTER_SERVICE}){
            Wallpapers.lock=info(service);
            assertTrue(SystemAodDimming.ownsFullAod(panel));
            assertEquals(10,Wallpapers.queriedUser);
        }
        panel.miuiFullAodManager.enabled=false;
        assertFalse(SystemAodDimming.ownsFullAod(panel));
    }
    @Test public void sharedHomeFallbackDoesNotOverrideASeparateStaticLock(){
        Panel panel=new Panel();Wallpapers.home=info(ModuleIdentity.HOME_SERVICE);
        assertTrue(SystemAodDimming.ownsFullAod(panel));
        Wallpapers.lockId=42;
        assertFalse(SystemAodDimming.ownsFullAod(panel));
        Wallpapers.lock=info("unrelated.Service");
        assertFalse(SystemAodDimming.ownsFullAod(panel));
    }
}
