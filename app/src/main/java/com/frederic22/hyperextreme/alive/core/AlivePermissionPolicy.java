package com.frederic22.hyperextreme.alive.core;

/** Never grants caller identity, component-setting or arbitrary package permissions. */
public final class AlivePermissionPolicy {
    private AlivePermissionPolicy() { }

    public static boolean grants(String permission, String packageName) {
        return "android.permission.AMBIENT_WALLPAPER".equals(permission)
            && ModuleIdentity.PACKAGE.equals(packageName);
    }
}
