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
        service.packageName = "com.frederic22.hyperextreme";
        service.name = service.packageName + "." + suffix;
        service.applicationInfo = context.getApplicationInfo();
        service.metaData = new Bundle(); service.metaData.putInt("android.service.wallpaper", R.xml.alive_wallpaper);
        ResolveInfo resolve = new ResolveInfo(); resolve.serviceInfo = service;
        return new WallpaperInfo(context, resolve);
    }

    @Test public void completeMutualExclusionMatrix() {
        for (int bits = 0; bits < 8; bits++) for (Feature target : Feature.values()) {
            boolean alive = (bits & 1) != 0, space = (bits & 2) != 0, parallax = (bits & 4) != 0;
            int targetBit = target == Feature.ALIVE ? 1 : target == Feature.SPATIAL ? 2 : 4;
            assertEquals((bits & ~targetBit) != 0, WallpaperFeaturePolicy.conflicts(target, alive, space, parallax));
        }
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
