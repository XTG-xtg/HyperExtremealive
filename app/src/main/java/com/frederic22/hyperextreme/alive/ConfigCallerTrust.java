package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/** Some HyperOS builds give SystemUI a dedicated UID rather than android.uid.system. */
final class ConfigCallerTrust {
    private ConfigCallerTrust() { }

    static boolean trusted(Context context, int uid, int ownUid) {
        if (uid == ownUid || uid == 0 || uid >= 0 && uid % 100000 == 1000) return true;
        if (uid < 0 || context == null) return false;
        try {
            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            if (packages == null) return false;
            for (String name : packages) {
                if (!"com.android.systemui".equals(name) && !"com.miui.aod".equals(name)) continue;
                ApplicationInfo info = pm.getApplicationInfo(name, 0);
                if (info.uid % 100000 == uid % 100000 &&
                        (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0)
                    return true;
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            // Unknown caller or unavailable package service: fail closed.
        }
        return false;
    }
}
