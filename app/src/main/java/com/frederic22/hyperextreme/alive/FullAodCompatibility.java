package com.frederic22.hyperextreme.alive;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import com.frederic22.hyperextreme.alive.core.ModuleIdentity;
import java.lang.reflect.Field;

/** Adjust only this ROM's full-AOD wallpaper classification, using the actual lock binding. */
final class FullAodCompatibility {
    static boolean owns(ComponentName component) {
        return component!=null && ModuleIdentity.PACKAGE.equals(component.getPackageName()) &&
            (ModuleIdentity.HOME_SERVICE.equals(component.getClassName()) || ModuleIdentity.LOCK_SERVICE.equals(component.getClassName()));
    }
    static boolean eligible(ComponentName component, boolean ambient, boolean defaultTheme, String type) {
        return owns(component) && ambient && defaultTheme && type!=null && !"super_wallpaper".equals(type);
    }
    static void reconcile(Object host) throws ReflectiveOperationException {
        Class<?> type=host.getClass();
        Object context=type.getField("mContext").get(host);
        Object manager=type.getField("mMiuiKeyguardWallPaperManager").get(host);
        Field support=type.getField("isWallpaperTypeSupport");
        if(!(context instanceof Context) || manager==null || support.getType()!=boolean.class || Build.VERSION.SDK_INT<34) return;
        boolean defaultTheme=Boolean.TRUE.equals(manager.getClass().getMethod("isDefaultLockScreenTheme").invoke(manager));
        Object wallpaperType=manager.getClass().getField("mKeyguardWallpaperType").get(manager);
        if(!defaultTheme || !(wallpaperType instanceof String)) return;
        try {
            WallpaperManager wallpapers=WallpaperManager.getInstance((Context)context);
            WallpaperInfo lock=wallpapers.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
            // A null lock binding means the system wallpaper is shared by both slots.
            if(lock==null) lock=wallpapers.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM);
            boolean ambient=lock!=null && Boolean.TRUE.equals(WallpaperInfo.class.getMethod("supportsAmbientMode").invoke(lock));
            if(lock!=null && eligible(lock.getComponent(),ambient,defaultTheme,(String)wallpaperType))
                support.setBoolean(host,true);
        } catch(RuntimeException unavailable) { /* Unknown binding preserves the ROM's result. */ }
    }
    private FullAodCompatibility() {}
}
