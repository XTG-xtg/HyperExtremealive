package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/** One applied snapshot; editor controls never mutate the running wallpaper. */
final class AliveRenderOptions {
    final boolean keepLock, continuousAod, continuousHome, followClock, burnIn;
    final boolean stereoAod;
    final int sensitivity;
    final float brightness;

    AliveRenderOptions(boolean keepLock, boolean continuousAod, boolean continuousHome,
            boolean followClock, boolean burnIn, boolean stereoAod, int sensitivity, float brightness) {
        this.keepLock=keepLock; this.continuousAod=continuousAod; this.continuousHome=continuousHome;
        this.followClock=followClock; this.burnIn=burnIn; this.stereoAod=stereoAod;
        this.sensitivity=Math.max(-50,Math.min(50,sensitivity));
        this.brightness=Float.isFinite(brightness)?Math.max(.25f,Math.min(3f,brightness)):1.3f;
    }
    static AliveRenderOptions load(Context c) {
        SharedPreferences p=Config.prefs(c);
        return new AliveRenderOptions(p.getBoolean("keep_lock",false),p.getBoolean("continuous_aod",true),
            p.getBoolean("continuous_home",true),p.getBoolean("follow_clock",true),p.getBoolean("burn_in",true),
            p.getBoolean("stereo_aod",true),p.getInt("sensitivity",0),p.getFloat("aod_brightness",1.3f));
    }
    SharedPreferences.Editor write(SharedPreferences.Editor e) {
        return e.putBoolean("keep_lock",keepLock).putBoolean("continuous_aod",continuousAod)
            .putBoolean("continuous_home",continuousHome).putBoolean("follow_clock",followClock)
            .putBoolean("burn_in",burnIn).putBoolean("stereo_aod",stereoAod)
            .putInt("sensitivity",sensitivity).putFloat("aod_brightness",brightness);
    }
    void write(Bundle b) {
        b.putBoolean("follow_clock",followClock);b.putBoolean("burn_in",burnIn);
        b.putFloat("aod_brightness",brightness);
    }
}
