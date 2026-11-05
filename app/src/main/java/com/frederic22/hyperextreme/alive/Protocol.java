package com.frederic22.hyperextreme.alive;

import android.net.Uri;

public final class Protocol {
    public static final String PACKAGE = "com.frederic22.hyperextreme";
    public static final String HOME = PACKAGE + ".alive.HomeWallpaperService";
    public static final String LOCK = PACKAGE + ".alive.LockWallpaperService";
    public static final String ROOT_MAIN = PACKAGE + ".alive.root.WallpaperTool";
    public static final Uri CONFIG = Uri.parse("content://" + PACKAGE + ".alive.config");
    public static final String TAG = "HyperExtremeAlive";
    private Protocol() {}
}
