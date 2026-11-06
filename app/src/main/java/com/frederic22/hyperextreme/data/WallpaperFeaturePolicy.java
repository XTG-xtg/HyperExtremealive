package com.frederic22.hyperextreme.data;

import com.frederic22.hyperextreme.alive.core.ModuleIdentity;

/** Shared identities and exclusion rules, independent of Android and the UI. */
public final class WallpaperFeaturePolicy {
    public enum Feature { ALIVE, SPATIAL, PARALLAX, RASTER }
    public static final String PREFS = "mask_swap";
    public static final String SPATIAL_ACTIVE = "spatial_active";
    public static final String SPATIAL_STOP_PENDING = "spatial_stop_pending";
    public static final String PARALLAX_ACTIVE = "parallax_bg_active";
    public static final String PARALLAX_STOP_TOKEN = "parallax_stop_token";
    public static final String RASTER_STOP_PENDING = "raster_stop_pending";
    private static final String PACKAGE = ModuleIdentity.PACKAGE;

    private WallpaperFeaturePolicy() { }

    public static boolean owns(Feature feature, String component) {
        if (component == null) return false;
        if (feature == Feature.ALIVE) {
            return component.equals(PACKAGE + "/" + ModuleIdentity.HOME_SERVICE) ||
                component.equals(PACKAGE + "/" + ModuleIdentity.LOCK_SERVICE);
        }
        return (feature == Feature.SPATIAL && component.equals(PACKAGE + "/" + ModuleIdentity.SPATIAL_SERVICE)) ||
            (feature == Feature.RASTER && component.equals(PACKAGE + "/" + ModuleIdentity.RASTER_SERVICE));
    }

    public static int ownedSlots(Feature feature, String home, String lock) {
        return (owns(feature, home) ? 1 : 0) | (owns(feature, lock) ? 2 : 0);
    }

    public static boolean conflicts(Feature target, boolean alive, boolean spatial, boolean parallax) {
        return conflicts(target, alive, spatial, parallax, false);
    }

    public static boolean conflicts(Feature target, boolean alive, boolean spatial, boolean parallax, boolean raster) {
        return (target != Feature.ALIVE && alive) || (target != Feature.SPATIAL && spatial) ||
            (target != Feature.PARALLAX && parallax) || (target != Feature.RASTER && raster);
    }
}
