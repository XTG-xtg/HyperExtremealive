package com.frederic22.hyperextreme.data;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy.Feature;

/** Activation is serialized across Activities; failed/unknown state never permits another effect. */
public final class WallpaperFeatures {
    private static final ReentrantLock TRANSITION = new ReentrantLock();
    private WallpaperFeatures() { }

    public static <T> T activate(Context context, Feature feature, Callable<T> action) throws Exception {
        if (!TRANSITION.tryLock()) throw new IllegalStateException("正在切换壁纸，请等待当前操作完成");
        try {
            requireAvailable(context, feature);
            return action.call();
        } finally { TRANSITION.unlock(); }
    }

    public static void requireAvailable(Context context, Feature target) {
        SharedPreferences prefs = context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0);
        String[] slots = components(context);
        boolean alive = WallpaperFeaturePolicy.ownedSlots(Feature.ALIVE, slots[0], slots[1]) != 0;
        boolean spatialStopping = prefs.getBoolean(WallpaperFeaturePolicy.SPATIAL_STOP_PENDING, false);
        boolean spatial = spatialStopping || prefs.getBoolean(WallpaperFeaturePolicy.SPATIAL_ACTIVE, false) ||
            WallpaperFeaturePolicy.ownedSlots(Feature.SPATIAL, slots[0], slots[1]) != 0;
        boolean stopping = prefs.getLong(WallpaperFeaturePolicy.PARALLAX_STOP_TOKEN, 0) >
            prefs.getLong("parallax_ack", 0);
        boolean parallax = prefs.getBoolean(WallpaperFeaturePolicy.PARALLAX_ACTIVE, false) || stopping;
        boolean rasterStopping = prefs.getBoolean(WallpaperFeaturePolicy.RASTER_STOP_PENDING, false);
        boolean raster = rasterStopping || (prefs.getInt("raster_mount_mode", 0) == 0 &&
            !prefs.getString("raster_active_id", "").isEmpty()) ||
            WallpaperFeaturePolicy.ownedSlots(Feature.RASTER, slots[0], slots[1]) != 0;
        List<String> conflicts = new ArrayList<>();
        if (target != Feature.ALIVE && alive) conflicts.add("Alive 壁纸");
        if (target != Feature.SPATIAL && spatial) conflicts.add("空间壁纸");
        if (target != Feature.PARALLAX && parallax) conflicts.add("视差壁纸");
        if (target != Feature.RASTER && raster) conflicts.add("光栅壁纸");
        if (WallpaperFeaturePolicy.conflicts(target, alive, spatial, parallax, raster)) {
            throw new IllegalStateException("请先关闭" + String.join("、", conflicts) + "的启用开关，再切换壁纸功能");
        }
        if (target == Feature.PARALLAX && stopping) {
            throw new IllegalStateException("视差壁纸尚未确认关闭，请先重试关闭开关");
        }
        if (target == Feature.SPATIAL && spatialStopping) {
            throw new IllegalStateException("空间壁纸尚未确认关闭，请先重试关闭开关");
        }
        if (target == Feature.RASTER && rasterStopping) {
            throw new IllegalStateException("光栅壁纸尚未确认关闭，请先重试取消应用");
        }
        boolean protecting = prefs.contains("protecting") ? prefs.getBoolean("protecting", false) :
            !prefs.getString("injected_wallpaper_md5", "").isEmpty();
        if (target == Feature.ALIVE && protecting) {
            throw new IllegalStateException("请等待锁屏遮罩替换完成，再开启 Alive 壁纸");
        }
    }

    public static boolean isBound(Context context, Feature feature) {
        String[] slots = components(context);
        return WallpaperFeaturePolicy.ownedSlots(feature, slots[0], slots[1]) != 0;
    }

    /** Recheck under the activation lock: another Activity may have applied while stop was pending. */
    public static <T> T removeInactiveCaches(Context context, Feature feature, Callable<T> action) throws Exception {
        if (feature != Feature.SPATIAL && feature != Feature.RASTER) throw new IllegalArgumentException("Unsupported cache owner");
        if (!TRANSITION.tryLock()) throw new IllegalStateException("正在切换壁纸，图片已保留");
        try {
            SharedPreferences prefs = context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0);
            boolean active = feature == Feature.SPATIAL
                ? prefs.getBoolean(WallpaperFeaturePolicy.SPATIAL_ACTIVE, false) || prefs.getBoolean(WallpaperFeaturePolicy.SPATIAL_STOP_PENDING, false)
                : prefs.getBoolean(WallpaperFeaturePolicy.RASTER_STOP_PENDING, false) || (prefs.getInt("raster_mount_mode", 0) == 0 && !prefs.getString("raster_active_id", "").isEmpty());
            if (active || isBound(context, feature)) throw new IllegalStateException("壁纸仍在使用，图片已保留");
            return action.call();
        } finally { TRANSITION.unlock(); }
    }

    private static String component(WallpaperInfo info) {
        return info == null ? null : info.getComponent().flattenToString();
    }

    private static String[] components(Context context) {
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            // Independent live lock wallpapers were introduced in Android 14.
            if (Build.VERSION.SDK_INT < 34) return new String[]{component(manager.getWallpaperInfo()), null};
            return new String[]{component(manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)),
                component(manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK))};
        } catch (RuntimeException failure) {
            throw new IllegalStateException("无法读取系统壁纸状态，本次未切换，请稍后重试", failure);
        }
    }

    /** Clear only this feature's bound slots, then verify. Never erase another feature's wallpaper. */
    public static void clearOwned(Context context, Feature feature) throws IOException {
        if (!TRANSITION.tryLock()) throw new IOException("正在切换壁纸，请等待当前操作完成");
        try { clearAndVerify(context, feature); }
        finally { TRANSITION.unlock(); }
    }

    private static void clearAndVerify(Context context, Feature feature) throws IOException {
        if (feature == Feature.PARALLAX) throw new IllegalArgumentException("Parallax is an overlay");
        WallpaperManager manager = WallpaperManager.getInstance(context);
        long deadline = SystemClock.uptimeMillis() + 2500;
        int cleared = 0;
        do {
            String[] slots = components(context);
            int which = WallpaperFeaturePolicy.ownedSlots(feature, slots[0], slots[1]);
            if (which == 0) return;
            // Clearing HOME can move its old live component to LOCK. Requery before clearing that slot.
            if ((which & ~cleared) != 0) {
                manager.clear(which);
                cleared |= which;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new IOException("系统未确认壁纸已关闭；开关保持开启，请重试。照片与设置已保留。");
    }
}
