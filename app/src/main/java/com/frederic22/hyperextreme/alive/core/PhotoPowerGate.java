package com.frederic22.hyperextreme.alive.core;

/** 兼容旧类名：电源保护现用于所有 Alive 壁纸，可见的 AOD 不等于亮屏锁屏。 */
public final class PhotoPowerGate {
    public static final int REJECT=-1;
    private boolean sleeping;
    private long lastEvent=-1;
    private long lastBoundary=-1,unlockAt=-1;

    public void reset(boolean interactive){sleeping=!interactive;lastEvent=lastBoundary=unlockAt=-1;}
    public boolean sleeping(){return sleeping;}

    public long orderedAt(long at,String reason) {
        // A faster visibility IPC may overtake the accepted exit. Never reorder it across
        // a newer sleep/wake, completed unlock, or explicit exit cancellation.
        return "keyguard-going-away".equals(reason)&&at>=lastBoundary?Math.max(at,lastEvent):at;
    }

    public int resolve(int requested,long at,String reason,boolean interactive) {
        if(requested<0||requested>2||at<lastEvent)return REJECT;
        lastEvent=at;
        boolean cancel="keyguard-exit-cancelled".equals(reason);
        boolean boundary="screen-off".equals(reason)||"screen-on".equals(reason)||"power-wake".equals(reason)
            ||"wallpaper-waking".equals(reason)||"display-awake".equals(reason)
            ||"biometric".equals(reason)||"user-present".equals(reason)||cancel;
        if(boundary)lastBoundary=at;
        // goToSleep precedes the display becoming noninteractive. Hold this fence through
        // transient KEYGUARD / visibility callbacks even while isInteractive is still true.
        if(!interactive||requested==AliveState.AOD&&"screen-off".equals(reason)){sleeping=true;unlockAt=-1;lastBoundary=at;return AliveState.AOD;}
        // The scrim/display may still report doze for a frame after a real wake.
        if(requested==AliveState.AOD)return sleeping?AliveState.AOD:REJECT;
        if(sleeping) {
            boolean wake="screen-on".equals(reason)||"power-wake".equals(reason)
                ||"wallpaper-waking".equals(reason)||"display-awake".equals(reason)
                ||"biometric".equals(reason)||"user-present".equals(reason)||"keyguard-going-away".equals(reason);
            if(!wake)return AliveState.AOD;
            sleeping=false;
        }
        if(cancel)unlockAt=-1;
        if(requested==AliveState.HOME&&"keyguard-going-away".equals(reason))unlockAt=at;
        if(requested==AliveState.HOME&&"user-present".equals(reason))unlockAt=-1;
        if(requested==AliveState.LOCK&&unlockAt>=0&&at-unlockAt<2000
                &&(reason.startsWith("visible-slot-")||"scrim-keyguard".equals(reason)))return AliveState.HOME;
        return requested;
    }
}
