package com.frederic22.hyperextreme.alive;

import android.content.Context;
import java.lang.reflect.Method;

/** Keep native device/settings gates; only the OEM wallpaper classification needs adaptation. */
final class AodWallpaperEligibility {
    static boolean allowed(Context context,ClassLoader loader) {
        try {
            return query(context,loader,"com.miui.aod.widget.AODSettings","isSupportAodWallpaper")
                &&query(context,loader,"com.miui.aod.Utils","isAodEnable");
        }catch(ReflectiveOperationException|RuntimeException|LinkageError absent){return false;}
    }
    private static boolean query(Context context,ClassLoader loader,String type,String name)throws ReflectiveOperationException {
        Method method=Class.forName(type,false,loader).getDeclaredMethod(name,Context.class);
        method.setAccessible(true);return Boolean.TRUE.equals(method.invoke(null,context));
    }
    static boolean classify(boolean inCreation,boolean enabled){return inCreation&&enabled;}
}
