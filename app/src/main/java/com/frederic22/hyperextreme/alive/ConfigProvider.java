package com.frederic22.hyperextreme.alive;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

/** A single bounded IPC channel. No image bytes, arbitrary file paths, or shell commands. */
public final class ConfigProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    private boolean trusted() {
        int uid = Binder.getCallingUid();
        return uid == Process.myUid() || uid == 0 || uid % 100000 == 1000;
    }
    public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        boolean debugShell = uid == 2000 && (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!trusted() && !(debugShell && "status".equals(method))) throw new SecurityException("Untrusted config caller: " + uid);
        if ("getConfig".equals(method)) return Config.read(getContext());
        if ("status".equals(method)) return EngineRegistry.status(getContext());
        if ("clockGeometry".equals(method)) {
            if(extras==null)throw new IllegalArgumentException("Missing clock geometry");
            if(Config.prefs(getContext()).getBoolean("enabled",false))EngineRegistry.clock(new ClockSnapshot(extras));
            return Bundle.EMPTY;
        }
        if ("signal".equals(method)) {
            if (extras == null) throw new IllegalArgumentException("Missing state");
            int mode = extras.getInt("mode", -1);
            if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid mode");
            String reason = extras.getString("reason", "hook");
            if (reason.length() > 80) throw new IllegalArgumentException("Invalid reason");
            long now = SystemClock.uptimeMillis(), time = extras.getLong("at", now);
            if (Math.abs(now - time) > 5000) throw new IllegalArgumentException("Stale signal");
            if (Config.prefs(getContext()).getBoolean("enabled", false)) EngineRegistry.signal(getContext(),mode, time, reason);
            return Bundle.EMPTY;
        }
        throw new IllegalArgumentException("Unknown method: " + method);
    }
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    public String getType(Uri u) { return "vnd.android.cursor.item/vnd.hyperalive.config"; }
    public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
