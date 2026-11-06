package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import com.frederic22.hyperextreme.data.WallpaperFeatures;
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy.Feature;

public final class AliveSettings {
    private AliveSettings() { }

    public static boolean isRunning(Context c) {
        try { return WallpaperFeatures.isBound(c, Feature.ALIVE); }
        catch (RuntimeException ignored) { return EngineRegistry.hasEngine(); }
    }

    static File draft(Context c) { return new File(c.getFilesDir(), "alive/photo-draft.png"); }

    static void importPhoto(Context c, Uri uri) throws IOException {
        importPhoto(c,uri,0,PhotoOptions.load(c,true));
    }
    static PhotoOptions importPhoto(Context c,Uri uri,int slot,PhotoOptions current)throws IOException {
        PhotoOptions next=current.media(slot,PhotoMedia.importImage(c,uri));
        next.save(c,true);return next;
    }
    public static synchronized void recoverPendingApply(Context c)throws IOException {
        AliveApplyBackup.recover(c);
    }

    static RootBridge.Result apply(Context c, int variant, boolean dark, boolean clock) throws Exception {
        return apply(c,variant,dark,clock,AliveRenderOptions.load(c));
    }
    static synchronized RootBridge.Result apply(Context c,int variant,boolean dark,boolean clock,AliveRenderOptions options)throws Exception {
        recoverPendingApply(c);
        return WallpaperFeatures.activate(c, Feature.ALIVE, () -> applyChecked(c, variant, dark, clock,options));
    }

    public static synchronized void disable(Context c) throws Exception { setEnabled(c, false); }

    static synchronized RootBridge.Result setEnabled(Context c, boolean enabled) throws Exception {
        recoverPendingApply(c);
        if (!enabled) {
            WallpaperFeatures.clearOwned(c, Feature.ALIVE);
            Config.prefs(c).edit().putBoolean("enabled", false).commit();
            Config.changed(c);
            RootBridge.Result result = new RootBridge.Result(0, "ALIVE_STOP_VERIFIED: 已关闭 Alive，照片与设置已保留");
            RootBridge.record(c, "disable", result);
            return result;
        }
        return WallpaperFeatures.activate(c, Feature.ALIVE, () -> {
            int variant = Config.variant(c);
            if (variant >= 601 && variant <= 606) PhotoMedia.validate(c,PhotoOptions.load(c,false),false,variant);
            if (variant >= 701 && variant <= 703) VivoOptions.load(c,variant,false).validateFiles(c);
            // Re-enable the last applied settings, never publish an unfinished editor draft.
            return RootBridge.wallpaper(c, true);
        });
    }

    private static RootBridge.Result applyChecked(Context c, int variant, boolean dark, boolean clock,AliveRenderOptions options) throws Exception {
        boolean supported = false;
        for (AliveCatalog.Scene scene : AliveCatalog.load(c)) if (scene.id == variant) supported = true;
        if (!supported) throw new IllegalArgumentException("不支持的场景");
        RootBridge.Result root = RootBridge.run("id -u");
        if (!root.ok() || !root.text.trim().equals("0")) return new RootBridge.Result(1, "未获得本应用的 Root 授权\n" + root.text);
        // Reject missing framework support before touching the active photo or renderer settings.
        RootBridge.Result check = RootBridge.wallpaper(c, "check");
        if (!check.ok()) return check;
        try(AliveApplyBackup backup=new AliveApplyBackup(c)) {
            if (variant >= 601 && variant <= 606) {
                PhotoOptions photo=PhotoOptions.load(c,true);
                PhotoMedia.validate(c,photo,true,variant);
                PhotoMedia.freezeLegacy(c,photo,true).save(c,false);
            }
            android.content.SharedPreferences.Editor editor=Config.prefs(c).edit();
            if(variant>=701&&variant<=703){
                VivoOptions vivo=VivoOptions.load(c,variant,true);vivo.validateFiles(c);editor.putString("vivo_options",vivo.json());
            }
            if (!options.write(editor).putInt("variant", variant).putBoolean("dark", dark)
                    .putBoolean("clock", clock).commit()) throw new IOException("无法保存 Alive 配置");
            RootBridge.Result result=RootBridge.wallpaper(c,true);
            if(result.ok())backup.accept();
            return result;
        }
    }
}
