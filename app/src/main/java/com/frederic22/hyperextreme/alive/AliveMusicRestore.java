package com.frederic22.hyperextreme.alive;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import com.frederic22.hyperextreme.data.WallpaperFeatures;
import com.frederic22.hyperextreme.alive.core.MusicLockRecovery;
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy;
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy.Feature;

/**
 * Recover a lost lock binding after media UI leaves, preserving the running HOME engine.
 */
public final class AliveMusicRestore {
    private static final Handler WORK;
    private static volatile boolean musicScreen;
    private static volatile Context app;
    private static long lastActionAt = -5000, lastErrorAt = -30000;
    private static final Runnable APPLY = () -> apply(app);
    static {
        HandlerThread thread = new HandlerThread("AliveMusicRestore");
        thread.start();
        WORK = new Handler(thread.getLooper());
    }
    private AliveMusicRestore() { }

    public static synchronized void onMusic(Context context, boolean shown) {
        musicScreen = shown;
        request(context, 0);
    }

    public static synchronized void heal(Context context) {
        request(context, 0);
    }

    public static synchronized void scheduleHeal(Context context, String reason) {
        request(context, 1200);
    }

    private static void request(Context context, long delay) {
        app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        // A root operation can take seconds. Keep one pending check using the latest state.
        WORK.removeCallbacks(APPLY);
        WORK.postDelayed(APPLY, delay);
    }

    private static void apply(Context context) {
        if (context == null) return;
        try {
            boolean enabled = Config.prefs(context).getBoolean("enabled", false);
            PowerManager power = context.getSystemService(PowerManager.class);
            boolean interactive = power == null || power.isInteractive();
            if (!enabled || !interactive || musicScreen) return;
            // API 33 has a shared live wallpaper; there is no independent live LOCK to repair.
            if (android.os.Build.VERSION.SDK_INT < 34) return;
            WallpaperManager manager = WallpaperManager.getInstance(context);
            android.app.WallpaperInfo home = manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM);
            android.app.WallpaperInfo lock = manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
            String homeName = home == null ? "STATIC" : home.getComponent().flattenToString();
            String lockName = lock == null ? "STATIC" : lock.getComponent().flattenToString();
            boolean lockOurs = WallpaperFeaturePolicy.owns(Feature.ALIVE, lockName);
            boolean homeOurs = WallpaperFeaturePolicy.owns(Feature.ALIVE, homeName);
            if (AliveMusicPolicy.decide(enabled, interactive, musicScreen, lockOurs, homeOurs)
                    == AliveMusicPolicy.Action.NONE) return;
            if (!MusicLockRecovery.canRestore(homeName, lockName)) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastActionAt < 5000) return;
            lastActionAt = now;
            WallpaperFeatures.activate(context, Feature.ALIVE, () -> {
                if (musicScreen || !Config.prefs(context).getBoolean("enabled", false)) return null;
                RootBridge.Result result = RootBridge.wallpaper(context, "restore-lock");
                if (!result.ok()) lastActionAt = SystemClock.uptimeMillis() + 25000;
                return result;
            });
        } catch (Exception failure) {
            // Query failures used to escape this Runnable and kill the shared app process.
            long now = SystemClock.uptimeMillis();
            if (now - lastErrorAt >= 30000) {
                lastErrorAt = now;
                Log.w(Protocol.TAG, "music restore deferred: " + failure);
            }
        }
    }
}
