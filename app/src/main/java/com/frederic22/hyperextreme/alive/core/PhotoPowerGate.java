package com.frederic22.hyperextreme.alive.core;

/** 兼容旧类名：电源保护现用于所有 Alive 壁纸，可见的 AOD 不等于亮屏锁屏。 */
public final class PhotoPowerGate {
    public static final int REJECT=-1;
    private boolean sleeping;
    private long lastEvent=-1;

    public void reset(boolean interactive){sleeping=!interactive;lastEvent=-1;}
    public boolean sleeping(){return sleeping;}

    public int resolve(int requested,long at,String reason,boolean interactive) {
        if(requested<0||requested>2||at<lastEvent)return REJECT;
        lastEvent=at;
        // goToSleep precedes the display becoming noninteractive. Hold this fence through
        // transient KEYGUARD / visibility callbacks even while isInteractive is still true.
        if(!interactive||requested==AliveState.AOD&&"screen-off".equals(reason)){sleeping=true;return AliveState.AOD;}
        // The scrim/display may still report doze for a frame after a real wake.
        if(requested==AliveState.AOD)return sleeping?AliveState.AOD:REJECT;
        if(sleeping) {
            boolean wake="screen-on".equals(reason)||"power-wake".equals(reason)
                ||"wallpaper-waking".equals(reason)||"display-awake".equals(reason)
                ||"biometric".equals(reason)||"user-present".equals(reason);
            if(!wake)return AliveState.AOD;
            sleeping=false;
        }
        return requested;
    }
}
