package com.frederic22.hyperextreme.alive.core;

/** Installed identity is separate from the retained upstream Java/JNI namespace. */
public final class ModuleIdentity {
    public static final String PACKAGE = "com.xtg.hyperextremealive";
    public static final String CODE_PACKAGE = "com.frederic22.hyperextreme";
    public static final String MASK_AUTHORITY = PACKAGE + ".provider";
    public static final String CONFIG_AUTHORITY = PACKAGE + ".alive.config";
    public static final String XPOSED_AUTHORITY = PACKAGE + ".XposedService";
    public static final String HOME_SERVICE = CODE_PACKAGE + ".alive.HomeWallpaperService";
    public static final String LOCK_SERVICE = CODE_PACKAGE + ".alive.LockWallpaperService";
    public static final String SPATIAL_SERVICE = CODE_PACKAGE + ".wallpaper.SpatialWallpaperService";
    public static final String RASTER_SERVICE = CODE_PACKAGE + ".wallpaper.RasterWallpaperService";
    public static final String ROOT_MAIN = CODE_PACKAGE + ".alive.root.WallpaperTool";

    private ModuleIdentity() { }
}
