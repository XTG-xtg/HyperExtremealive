package com.frederic22.hyperextreme.data;

import android.app.Application;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import com.frederic22.hyperextreme.R;
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy.Feature;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, shadows = WallpaperFeaturesTest.FakeWallpapers.class)
public class WallpaperFeaturesTest {
    private Context context;
    private WallpaperInfo aliveHome, aliveLock, spatial;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().clear().commit();
        aliveHome = info("alive.HomeWallpaperService"); aliveLock = info("alive.LockWallpaperService");
        spatial = info("wallpaper.SpatialWallpaperService");
        FakeWallpapers.home = null; FakeWallpapers.lock = null;
        FakeWallpapers.rejectQuery = false; FakeWallpapers.ignoreClear = false;
        FakeWallpapers.moveToLock = false; FakeWallpapers.cleared.clear();
    }

    private WallpaperInfo info(String suffix) throws Exception {
        ServiceInfo service = new ServiceInfo();
        service.packageName = "com.xtg.hyperextremealive";
        service.name = "com.frederic22.hyperextreme." + suffix;
        service.applicationInfo = context.getApplicationInfo();
        service.metaData = new Bundle(); service.metaData.putInt("android.service.wallpaper", R.xml.alive_wallpaper);
        ResolveInfo resolve = new ResolveInfo(); resolve.serviceInfo = service;
        return new WallpaperInfo(context, resolve);
    }

    @Test public void completeMutualExclusionMatrix() {
        for (int bits = 0; bits < 16; bits++) for (Feature target : Feature.values()) {
            boolean alive = (bits & 1) != 0, space = (bits & 2) != 0, parallax = (bits & 4) != 0;
            int targetBit = 1 << target.ordinal();
            assertEquals((bits & ~targetBit) != 0, WallpaperFeaturePolicy.conflicts(target, alive, space, parallax, (bits & 8) != 0));
        }
    }

    @Test public void pendingSpatialAndRasterStopsBlockEveryFeature() {
        for (String key : new String[]{WallpaperFeaturePolicy.SPATIAL_STOP_PENDING, WallpaperFeaturePolicy.RASTER_STOP_PENDING}) {
            context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().clear().putBoolean(key, true).commit();
            for (Feature feature : Feature.values())
                assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, feature));
        }
    }

    @Test public void rasterBindingAndHookStateParticipateInExclusion() throws Exception {
        FakeWallpapers.lock = info("wallpaper.RasterWallpaperService");
        for (Feature feature : new Feature[]{Feature.ALIVE, Feature.SPATIAL, Feature.PARALLAX})
            assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, feature));
        WallpaperFeatures.requireAvailable(context, Feature.RASTER);
        FakeWallpapers.lock = null;
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().putString("raster_active_id", "123").commit();
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, Feature.ALIVE));
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().putInt("raster_mount_mode", 1).commit();
        WallpaperFeatures.requireAvailable(context, Feature.ALIVE);
    }

    @Test public void clearingRasterNeverClearsAliveOrSpatial() throws Exception {
        FakeWallpapers.home = aliveHome; FakeWallpapers.lock = spatial;
        WallpaperFeatures.clearOwned(context, Feature.RASTER);
        assertTrue(FakeWallpapers.cleared.isEmpty());
        FakeWallpapers.lock = info("wallpaper.RasterWallpaperService");
        WallpaperFeatures.clearOwned(context, Feature.RASTER);
        assertSame(aliveHome, FakeWallpapers.home);
        assertEquals(java.util.Arrays.asList(2), FakeWallpapers.cleared);
    }

    @Test public void upstreamPackageIsNeverOwned() {
        String code = "com.frederic22.hyperextreme";
        for (Feature feature : Feature.values()) {
            for (String service : new String[]{"alive.HomeWallpaperService", "alive.LockWallpaperService", "wallpaper.SpatialWallpaperService", "wallpaper.RasterWallpaperService"}) {
                assertFalse(WallpaperFeaturePolicy.owns(feature, code + "/" + code + "." + service));
                assertFalse(WallpaperFeaturePolicy.owns(feature, code + "/." + service));
            }
        }
    }

    @Test public void cacheRemovalRechecksActivationAndPendingStops() throws Exception {
        boolean[] removed = {false};
        FakeWallpapers.home = spatial;
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.removeInactiveCaches(context, Feature.SPATIAL, () -> { removed[0] = true; return 1; }));
        assertFalse(removed[0]);
        FakeWallpapers.home = null;
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().putBoolean(WallpaperFeaturePolicy.RASTER_STOP_PENDING, true).commit();
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.removeInactiveCaches(context, Feature.RASTER, () -> { removed[0] = true; return 1; }));
        assertFalse(removed[0]);
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().clear().commit();
        assertEquals(Integer.valueOf(1), WallpaperFeatures.removeInactiveCaches(context, Feature.RASTER, () -> 1));
    }

    @Test public void aliveInEitherSlotBlocksBothOtherFeatures() {
        FakeWallpapers.lock = aliveLock;
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, Feature.SPATIAL));
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, Feature.PARALLAX));
        WallpaperFeatures.requireAvailable(context, Feature.ALIVE);
    }

    @Test public void spatialServiceCountsWithoutSpatialPreference() {
        FakeWallpapers.home = spatial;
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, Feature.ALIVE));
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, Feature.PARALLAX));
    }

    @Test public void overlayPreferencesAndUnacknowledgedStopBlockActivation() {
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit()
            .putBoolean(WallpaperFeaturePolicy.SPATIAL_ACTIVE, true).commit();
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, Feature.ALIVE));
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().clear()
            .putLong(WallpaperFeaturePolicy.PARALLAX_STOP_TOKEN, 8).putLong("parallax_ack", 7).commit();
        for (Feature feature : Feature.values())
            assertThrows(IllegalStateException.class, () -> WallpaperFeatures.requireAvailable(context, feature));
        context.getSharedPreferences(WallpaperFeaturePolicy.PREFS, 0).edit().putLong("parallax_ack", 8).commit();
        WallpaperFeatures.requireAvailable(context, Feature.ALIVE);
    }

    @Test public void clearBothActuallyUnbindsAndVerifies() throws Exception {
        FakeWallpapers.home = aliveHome; FakeWallpapers.lock = aliveLock;
        WallpaperFeatures.clearOwned(context, Feature.ALIVE);
        assertEquals(java.util.Arrays.asList(3), FakeWallpapers.cleared);
        assertFalse(WallpaperFeatures.isBound(context, Feature.ALIVE));
    }

    @Test public void clearingAliveNeverClearsUnrelatedHomeSlot() throws Exception {
        FakeWallpapers.home = spatial; FakeWallpapers.lock = aliveLock;
        WallpaperFeatures.clearOwned(context, Feature.ALIVE);
        assertSame(spatial, FakeWallpapers.home);
        assertEquals(java.util.Arrays.asList(2), FakeWallpapers.cleared);
    }

    @Test public void clearAccountsForHomeMovingToLock() throws Exception {
        FakeWallpapers.home = aliveHome; FakeWallpapers.moveToLock = true;
        WallpaperFeatures.clearOwned(context, Feature.ALIVE);
        assertEquals(java.util.Arrays.asList(1, 2), FakeWallpapers.cleared);
        assertNull(FakeWallpapers.lock);
    }

    @Test public void rejectedClearCannotReportSuccess() {
        FakeWallpapers.home = aliveHome; FakeWallpapers.ignoreClear = true;
        assertThrows(IOException.class, () -> WallpaperFeatures.clearOwned(context, Feature.ALIVE));
        assertTrue(WallpaperFeatures.isBound(context, Feature.ALIVE));
    }

    @Test public void queryFailureIsFailClosedAndDoesNotRunActivation() {
        FakeWallpapers.rejectQuery = true;
        boolean[] ran = {false};
        assertThrows(IllegalStateException.class, () -> WallpaperFeatures.activate(context, Feature.ALIVE, () -> { ran[0] = true; return null; }));
        assertFalse(ran[0]); assertTrue(FakeWallpapers.cleared.isEmpty());
    }

    @Implements(WallpaperManager.class)
    public static class FakeWallpapers {
        static WallpaperInfo home, lock;
        static boolean rejectQuery, ignoreClear, moveToLock;
        static final List<Integer> cleared = new ArrayList<>();
        @Implementation protected WallpaperInfo getWallpaperInfo(int which) {
            if (rejectQuery) throw new SecurityException("Synthetic query failure");
            return which == 1 ? home : lock;
        }
        @Implementation protected void clear(int which) {
            cleared.add(which);
            if (ignoreClear) return;
            if ((which & 1) != 0) {
                if (moveToLock && lock == null) lock = home;
                home = null;
            }
            if ((which & 2) != 0) lock = null;
        }
    }
}
