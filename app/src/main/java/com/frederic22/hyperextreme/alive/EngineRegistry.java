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
    private static final PhotoPowerGate PHOTO_POWER=new PhotoPowerGate();
    private static boolean photoGated;
    private static AliveWallpaperService.AliveEngine home, lock;
    private static String lastReason = "initial";
    static synchronized boolean hasEngine() { return home != null || lock != null; }
    static synchronized void register(int slot, AliveWallpaperService.AliveEngine engine) {
        if(home==null&&lock==null)photoGated=false;
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
                int mode=requested,variant=Config.variant(context);
                boolean photo=variant==601||variant==602;
                if(photo) {
                    android.os.PowerManager power=context.getSystemService(android.os.PowerManager.class);
                    boolean interactive=power!=null&&power.isInteractive();
                    if(!photoGated)PHOTO_POWER.reset(interactive);
                    mode=PHOTO_POWER.resolve(requested,at,reason,interactive);
                    if(mode!=requested)Log.i(Protocol.TAG,"photo power gate requested="+requested+" accepted="+mode+" interactive="+interactive+" sleeping="+PHOTO_POWER.sleeping()+" reason="+reason);
                }
                photoGated=photo;
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
        return b;
    }
    static final class Snapshot {
        final int from, mode; final long startMs, sequence;
        Snapshot(int from, int mode, long startMs, long sequence) { this.from=from;this.mode=mode;this.startMs=startMs;this.sequence=sequence; }
    }
}
