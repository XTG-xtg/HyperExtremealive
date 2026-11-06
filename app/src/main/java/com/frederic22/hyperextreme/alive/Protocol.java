package com.frederic22.hyperextreme.alive;

import android.net.Uri;
import com.frederic22.hyperextreme.alive.core.ModuleIdentity;

public final class Protocol {
    public static final String PACKAGE = ModuleIdentity.PACKAGE;
    public static final String HOME = ModuleIdentity.HOME_SERVICE;
    public static final String LOCK = ModuleIdentity.LOCK_SERVICE;
    public static final String ROOT_MAIN = ModuleIdentity.ROOT_MAIN;
    public static final Uri CONFIG = Uri.parse("content://" + ModuleIdentity.CONFIG_AUTHORITY);
    public static final String TAG = "HyperExtremeAlive";
    private Protocol() {}
}
