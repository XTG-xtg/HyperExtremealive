package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=Application.class)
public class ConfigCallerTrustTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private static final int OWN = 10500;
    private void install(String name, int uid, int flags) {
        PackageInfo p = new PackageInfo(); p.packageName=name;
        p.applicationInfo = new ApplicationInfo(); p.applicationInfo.packageName=name;
        p.applicationInfo.uid=uid; p.applicationInfo.flags=flags;
        shadowOf(context.getPackageManager()).installPackage(p);
    }
    private boolean trusted(int uid) { return ConfigCallerTrust.trusted(context, uid, OWN); }
    @Test public void dedicatedSystemUiUidFromUserLogIsAccepted() {
        install("com.android.systemui",10231,ApplicationInfo.FLAG_SYSTEM);
        assertTrue(trusted(10231));
    }
    @Test public void updatedSystemAodIsAccepted() {
        install("com.miui.aod",10200,ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
        assertTrue(trusted(10200));
    }
    @Test public void nameAloneDoesNotTrustAnOrdinaryApp() {
        install("com.android.systemui",10231,0);
        assertFalse(trusted(10231));
    }
    @Test public void unrelatedSystemAppAndUnknownUidAreRejected() {
        install("com.example.systemapp",10231,ApplicationInfo.FLAG_SYSTEM);
        assertFalse(trusted(10231)); assertFalse(trusted(10300)); assertFalse(trusted(-1));
    }
    @Test public void cannotBorrowAnotherPackagesUid() {
        install("com.android.systemui",10231,ApplicationInfo.FLAG_SYSTEM);
        install("com.example.other",10232,0);
        assertFalse(trusted(10232));
    }
    @Test public void ownRootAndSystemUidsRemainTrusted() {
        assertTrue(trusted(OWN));assertTrue(trusted(0));assertTrue(trusted(1000));assertTrue(trusted(101000));
    }
    @Test public void dedicatedSystemUiUidInAnotherUserUsesVerifiedPackageMembership() {
        install("com.android.systemui",10231,ApplicationInfo.FLAG_SYSTEM);
        shadowOf(context.getPackageManager()).setPackagesForUid(110231,"com.android.systemui");
        assertTrue(trusted(110231));
        assertFalse(trusted(110232));
    }
}
