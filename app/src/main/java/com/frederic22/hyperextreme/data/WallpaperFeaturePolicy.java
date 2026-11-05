package com.frederic22.hyperextreme.data;

/** Shared identities and exclusion rules, independent of Android and the UI. */
public final class WallpaperFeaturePolicy {
    public enum Feature { ALIVE, SPATIAL, PARALLAX }
    public static final String PREFS = "mask_swap";
    public static final String SPATIAL_ACTIVE = "spatial_active";
    public static final String SPATIAL_STOP_PENDING = "spatial_stop_pending";
    public static final String PARALLAX_ACTIVE = "parallax_bg_active";
    public static final String PARALLAX_STOP_TOKEN = "parallax_stop_token";
    private static final String PACKAGE = "com.frederic22.hyperextreme";

    private WallpaperFeaturePolicy() { }

    public static boolean owns(Feature feature, String component) {
        if (component == null) return false;
        if (feature == Feature.ALIVE) {
            return component.equals(PACKAGE + "/" + PACKAGE + ".alive.HomeWallpaperService") ||
                component.equals(PACKAGE + "/" + PACKAGE + ".alive.LockWallpaperService");
        }
        return feature == Feature.SPATIAL &&
            component.equals(PACKAGE + "/" + PACKAGE + ".wallpaper.SpatialWallpaperService");
    }

    public static int ownedSlots(Feature feature, String home, String lock) {
        return (owns(feature, home) ? 1 : 0) | (owns(feature, lock) ? 2 : 0);
    }

    public static boolean conflicts(Feature target, boolean alive, boolean spatial, boolean parallax) {
        return (target != Feature.ALIVE && alive) || (target != Feature.SPATIAL && spatial) ||
            (target != Feature.PARALLAX && parallax);
    }
}
