package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.provider.Settings;

/** Explicit per-user opt-in. Availability is separate from the real AOD on/off switch. */
final class ForcedAodSupport {
    static boolean active(Context context) {
        if(context==null) return false;
        try {
            int user=(Integer)Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null);
            return (Integer)Settings.Secure.class.getMethod("getIntForUser",android.content.ContentResolver.class,String.class,int.class,int.class)
                .invoke(null,context.getContentResolver(),SystemAodControl.FORCE,0,user)==1;
        } catch(ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    static boolean feature(String key) {
        return "support_aod_fullscreen".equals(key) || "inner_screen_support_aod_fullscreen".equals(key);
    }
    static void prepare(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> configs=Class.forName("com.miui.utils.configs.MiuiConfigs",true,loader);
        AodSupportFlags.prepare(configs);
    }
    private ForcedAodSupport() {}
}
