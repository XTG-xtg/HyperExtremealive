package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.frederic22.hyperextreme.alive.core.AliveState;
import com.frederic22.hyperextreme.alive.core.PhotoPowerGate;

final class EngineRegistry {
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AliveState STATE = new AliveState();
    private static final java.util.ArrayDeque<Snapshot> HISTORY = new java.util.ArrayDeque<>();
    private static final PhotoPowerGate POWER_GATE=new PhotoPowerGate();
    private static boolean powerInitialized;
    private static AliveWallpaperService.AliveEngine home, lock;
    private static String lastReason = "initial";
    private static ClockSnapshot aodClock, lockClock;
    static synchronized ClockSnapshot clock(boolean aod){return aod?aodClock:lockClock;}
    static void clock(ClockSnapshot snapshot){
        MAIN.post(()->{synchronized(EngineRegistry.class){
            ClockSnapshot previous=snapshot.aod?aodClock:lockClock;
            if(previous!=null&&snapshot.at<previous.at)return;
            if(snapshot.aod)aodClock=snapshot;else lockClock=snapshot;
            if(snapshot.aod){if(home!=null)home.clock(snapshot);if(lock!=null)lock.clock(snapshot);}
        }});
    }
    static synchronized boolean hasEngine() { return home != null || lock != null; }
    static synchronized void register(int slot, AliveWallpaperService.AliveEngine engine) {
        if(home==null&&lock==null)powerInitialized=false;
        if (slot == 1) home = engine; else lock = engine;
    }
    static synchronized void unregister(int slot, AliveWallpaperService.AliveEngine engine) {
        if (slot == 1 && home == engine) home = null;
        if (slot == 2 && lock == engine) lock = null;
    }
    static void signal(Context context,int requested, long at, String reason) {
        MAIN.post(() -> {
            synchronized (EngineRegistry.class) {
                if(at<STATE.lastEventMs)return;
                android.os.PowerManager power=context.getSystemService(android.os.PowerManager.class);
                boolean interactive=power!=null&&power.isInteractive();
                // 息屏后可见的锁屏 Surface 不代表亮屏；所有壁纸共用电源状态保护。
                if(!powerInitialized) {
                    POWER_GATE.reset(interactive);
                    powerInitialized=true;
                }
                int mode=POWER_GATE.resolve(requested,at,reason,interactive);
                if(mode!=requested)Log.i(Protocol.TAG,"power gate requested="+requested+" accepted="+mode+" interactive="+interactive+" sleeping="+POWER_GATE.sleeping()+" reason="+reason);
                if (!STATE.accept(mode, at, reason)) return;
                lastReason = reason;
                HISTORY.addLast(snapshot());
                while(HISTORY.size()>32||(HISTORY.size()>1&&at-HISTORY.getFirst().startMs>15000))HISTORY.removeFirst();
                Log.i(Protocol.TAG, "state=" + AliveState.name(mode) + " seq=" + STATE.sequence + " reason=" + reason);
                if (home != null) home.transition();
                if (lock != null) lock.transition();
            }
        });
    }
    static synchronized Snapshot snapshot() { return new Snapshot(STATE.previous, STATE.current, STATE.startedMs, STATE.sequence); }
    static synchronized Snapshot[] history() { return HISTORY.toArray(new Snapshot[0]); }
    static synchronized void reload() {
        MAIN.post(() -> { synchronized (EngineRegistry.class) { if (home != null) home.reload(); if (lock != null) lock.reload(); } });
    }
    static synchronized Bundle status(Context c) {
        Bundle b = Config.read(c);
        b.putInt("mode", STATE.current); b.putString("reason", lastReason);
        b.putBoolean("home_engine", home != null); b.putBoolean("lock_engine", lock != null);
        b.putLong("frames", EglWorker.frames.get()); b.putString("render_error", EglWorker.lastError);
        b.putBoolean("power_sleeping",POWER_GATE.sleeping());
        if(aodClock!=null)b.putBundle("aod_clock_bounds",aodClock.bundle());
        if(lockClock!=null)b.putBundle("lock_clock_bounds",lockClock.bundle());
        return b;
    }
    static final class Snapshot {
        final int from, mode; final long startMs, sequence;
        Snapshot(int from, int mode, long startMs, long sequence) { this.from=from;this.mode=mode;this.startMs=startMs;this.sequence=sequence; }
    }
}
