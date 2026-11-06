package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/** One applied snapshot; editor controls never mutate the running wallpaper. */
final class AliveRenderOptions {
    final boolean keepLock, continuousAod, continuousHome, followClock, burnIn;
    final boolean stereoAod;
    final int sensitivity;
    final float brightness, aodOffsetX, aodOffsetY;

    AliveRenderOptions(boolean keepLock, boolean continuousAod, boolean continuousHome,
            boolean followClock, boolean burnIn, boolean stereoAod, int sensitivity, float brightness) {
        this(keepLock,continuousAod,continuousHome,followClock,burnIn,stereoAod,sensitivity,brightness,0,0);
    }
    AliveRenderOptions(boolean keepLock, boolean continuousAod, boolean continuousHome,
            boolean followClock, boolean burnIn, boolean stereoAod, int sensitivity, float brightness,
            float aodOffsetX,float aodOffsetY) {
        this.keepLock=keepLock; this.continuousAod=continuousAod; this.continuousHome=continuousHome;
        this.followClock=followClock; this.burnIn=burnIn; this.stereoAod=stereoAod;
        this.sensitivity=Math.max(-50,Math.min(50,sensitivity));
        this.brightness=Float.isFinite(brightness)?Math.max(.25f,Math.min(3f,brightness)):1.3f;
        this.aodOffsetX=offset(aodOffsetX);this.aodOffsetY=offset(aodOffsetY);
    }
    private static float offset(float value){return Float.isFinite(value)?Math.max(-.45f,Math.min(.45f,value)):0;}
    AliveRenderOptions position(float x,float y){return new AliveRenderOptions(keepLock,continuousAod,continuousHome,
        false,burnIn,stereoAod,sensitivity,brightness,x,y);}
    static AliveRenderOptions load(Context c) {
        return load(c,false);
    }
    static AliveRenderOptions load(Context c,boolean draft) {
        SharedPreferences p=draft?c.getSharedPreferences("alive_render_draft",0):Config.prefs(c);
        if(draft&&!p.contains("aod_brightness"))return load(c,false);
        return new AliveRenderOptions(p.getBoolean("keep_lock",false),p.getBoolean("continuous_aod",true),
            p.getBoolean("continuous_home",true),p.getBoolean("follow_clock",true),p.getBoolean("burn_in",true),
            p.getBoolean("stereo_aod",true),p.getInt("sensitivity",0),p.getFloat("aod_brightness",1.3f),
            p.getFloat("dynamic_aod_x",0),p.getFloat("dynamic_aod_y",0));
    }
    void saveDraft(Context c){write(c.getSharedPreferences("alive_render_draft",0).edit()).apply();}
    SharedPreferences.Editor write(SharedPreferences.Editor e) {
        return e.putBoolean("keep_lock",keepLock).putBoolean("continuous_aod",continuousAod)
            .putBoolean("continuous_home",continuousHome).putBoolean("follow_clock",followClock)
            .putBoolean("burn_in",burnIn).putBoolean("stereo_aod",stereoAod)
            .putInt("sensitivity",sensitivity).putFloat("aod_brightness",brightness)
            .putFloat("dynamic_aod_x",aodOffsetX).putFloat("dynamic_aod_y",aodOffsetY);
    }
    void write(Bundle b) {
        b.putBoolean("follow_clock",followClock);b.putBoolean("burn_in",burnIn);
        b.putFloat("aod_brightness",brightness);
    }
}
