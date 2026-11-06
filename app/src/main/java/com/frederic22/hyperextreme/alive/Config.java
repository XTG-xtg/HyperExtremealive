package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

final class Config {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("alive", Context.MODE_PRIVATE); }
    static int variant(Context c) { return prefs(c).getInt("variant", 1); }
    static boolean dark(Context c) { return prefs(c).getBoolean("dark", false); }
    static Bundle read(Context c) {
        SharedPreferences p = prefs(c); Bundle b = new Bundle();
        b.putInt("variant", variant(c)); b.putBoolean("dark", dark(c));
        b.putBoolean("enabled", p.getBoolean("enabled", false) && EngineRegistry.hasEngine());
        b.putBoolean("clock", p.getBoolean("clock", false));
        b.putInt("clock_color", p.getInt("clock_color", 0xffeeeeee));
        b.putFloat("clock_scale", Math.max(.5f, Math.min(2f, p.getFloat("clock_scale", 1f))));
        b.putLong("revision", p.getLong("revision", 0));
        AliveRenderOptions.load(c).write(b);
        return b;
    }
    static void changed(Context c) {
        prefs(c).edit().putLong("revision", System.currentTimeMillis()).apply();
        c.getContentResolver().notifyChange(Protocol.CONFIG, null);
        if (prefs(c).getBoolean("enabled", false)) EngineRegistry.reload();
    }
}
