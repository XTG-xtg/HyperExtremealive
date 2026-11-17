package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

final class Config {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("alive", Context.MODE_PRIVATE); }
    static int variant(Context c) { return prefs(c).getInt("variant", 1); }
    static boolean dark(Context c) { return prefs(c).getBoolean("dark", false); }
    static boolean launcherScroll(Context c){return prefs(c).getBoolean("launcher_scroll",true);}
    static void setLauncherScroll(Context c,boolean value){
        prefs(c).edit().putBoolean("launcher_scroll",value).apply();
        c.getContentResolver().notifyChange(Protocol.CONFIG,null);
    }
    static Bundle read(Context c) {
        SharedPreferences p = prefs(c); Bundle b = new Bundle();
        b.putInt("variant", variant(c)); b.putBoolean("dark", dark(c));
        b.putBoolean("launcher_scroll",launcherScroll(c));
        b.putBoolean(AodBrightness.PANEL_MANAGED,AodBrightness.panelManaged());
        b.putBoolean("configured", p.getBoolean("enabled", false));
        b.putBoolean("enabled", b.getBoolean("configured") && EngineRegistry.hasEngine());
        // Retired overlays remain disabled even with legacy saved settings.
        b.putBoolean("clock", false);
        b.putLong("revision", p.getLong("revision", 0));
        if(b.getBoolean("enabled")&&variant(c)==701){
            LiquidTouchMap map=EngineRegistry.liquidMap();
            if(map!=null&&map.scene.equals(VivoOptions.load(c,701,false).json()))b.putBundle("liquid_touch",map.bundle());
        }
        AliveRenderOptions.load(c).write(b);
        return b;
    }
    static void changed(Context c) {
        prefs(c).edit().putLong("revision", System.currentTimeMillis()).apply();
        c.getContentResolver().notifyChange(Protocol.CONFIG, null);
        if (prefs(c).getBoolean("enabled", false)) EngineRegistry.reload();
    }
}
