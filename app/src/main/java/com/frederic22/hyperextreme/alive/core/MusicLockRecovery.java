package com.frederic22.hyperextreme.alive.core;

/** Allowlist checked again in the privileged helper immediately before changing LOCK. */
public final class MusicLockRecovery {
    private MusicLockRecovery() { }
    public static boolean canRestore(String home, String lock) {
        if (!(ModuleIdentity.PACKAGE + "/" + ModuleIdentity.HOME_SERVICE).equals(home)) return false;
        return "STATIC".equals(lock) ||
            "com.miui.miwallpaper/com.miui.miwallpaper.wallpaperservice.ImageWallpaper".equals(lock) ||
            "com.miui.miwallpaper/com.miui.miwallpaper.wallpaperservice.MiuiKeyguardPictorialWallpaper".equals(lock);
    }
}
