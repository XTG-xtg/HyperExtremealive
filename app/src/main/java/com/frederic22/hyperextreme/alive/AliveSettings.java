package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        File directory = new File(c.getFilesDir(), "alive");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建照片目录");
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(), uri),
            (decoder, info, source) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                float ratio = Math.min(1, 2560f / Math.max(info.getSize().getWidth(), info.getSize().getHeight()));
                decoder.setTargetSize(Math.max(1, (int)(info.getSize().getWidth() * ratio)),
                    Math.max(1, (int)(info.getSize().getHeight() * ratio)));
            });
        File temp = new File(directory, "photo.next.png");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("照片编码失败");
            }
            Files.move(temp.toPath(), draft(c).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { bitmap.recycle(); Files.deleteIfExists(temp.toPath()); }
        // A draft is private to the preview. Importing never reloads the active wallpaper.
        PhotoOptions.defaults().save(c,true);
    }

    static RootBridge.Result apply(Context c, int variant, boolean dark, boolean clock) throws Exception {
        return WallpaperFeatures.activate(c, Feature.ALIVE, () -> applyChecked(c, variant, dark, clock));
    }

    static RootBridge.Result setEnabled(Context c, boolean enabled) throws Exception {
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
            if (variant >= 601 && !new File(c.getFilesDir(), "alive/photo.png").isFile())
                throw new IOException("请先进入圆景或留光，选择照片并应用");
            // Re-enable the last applied settings, never publish an unfinished editor draft.
            return RootBridge.wallpaper(c, true);
        });
    }

    private static RootBridge.Result applyChecked(Context c, int variant, boolean dark, boolean clock) throws Exception {
        boolean supported = false;
        for (AliveCatalog.Scene scene : AliveCatalog.load(c)) if (scene.id == variant) supported = true;
        if (!supported) throw new IllegalArgumentException("不支持的场景");
        RootBridge.Result root = RootBridge.run("id -u");
        if (!root.ok() || !root.text.trim().equals("0")) return new RootBridge.Result(1, "未获得本应用的 Root 授权\n" + root.text);
        // Reject missing framework support before touching the active photo or renderer settings.
        RootBridge.Result check = RootBridge.wallpaper(c, "check");
        if (!check.ok()) return check;
        if (variant >= 601) {
            File draft = draft(c), active = new File(c.getFilesDir(), "alive/photo.png");
            if (!draft.isFile() && !active.isFile()) throw new IOException("请先选择照片");
            if (draft.isFile()) {
                File temp = new File(c.getFilesDir(), "alive/photo.apply.png");
                Files.copy(draft.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp.toPath(), active.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            PhotoOptions.load(c,true).save(c,false);
        }
        if (!Config.prefs(c).edit().putInt("variant", variant).putBoolean("dark", dark)
                .putBoolean("clock", clock).commit()) throw new IOException("无法保存 Alive 配置");
        return RootBridge.wallpaper(c, true);
    }
}
