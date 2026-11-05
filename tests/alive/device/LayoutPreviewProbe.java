package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.Collections;

/** Offscreen only: uses a bundled texture, read-only fake preferences and an isolated temp directory. */
public final class LayoutPreviewProbe {
    private static final File OUT = new File("/data/local/tmp/hyperextreme-layout-review");
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        int variant = Integer.parseInt(args[0]);
        Class<?> at = Class.forName("android.app.ActivityThread");
        Context system = (Context) at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context installed = system.createPackageContext(Protocol.PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (proxy, method, values) -> {
                if (method.getName().equals("getAll")) return Collections.emptyMap();
                if (method.getName().equals("contains")) return false;
                if (method.getName().startsWith("get") && values.length == 2) return values[1];
                throw new AssertionError("Unexpected preference operation: " + method.getName());
            });
        Context sandbox = new ContextWrapper(installed) {
            public Context getApplicationContext() { return this; }
            public File getFilesDir() { return OUT; }
            public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
        };
        File photo = new File(OUT, "alive/photo.png"); photo.getParentFile().mkdirs();
        Bitmap sample;
        try (java.io.InputStream in = installed.getAssets().open("stereo/preview_v6.png")) { sample = BitmapFactory.decodeStream(in); }
        try (FileOutputStream stream = new FileOutputStream(photo)) { sample.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        sample.recycle();
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> { System.err.println("Layout preview timeout"); System.exit(3); }, 20_000);
        new AliveLayoutFrames(sandbox, variant, false, 288, 634, (images, error) -> {
            try {
                if (!error.isEmpty() || images.length != 3) throw new AssertionError(error);
                for (int i = 0; i < 3; i++) {
                    if (images[i].getWidth() != 288 || images[i].getHeight() != 634) throw new AssertionError("Wrong layout dimensions");
                    try (FileOutputStream stream = new FileOutputStream(new File(OUT, "layout-" + variant + "-" + i + ".png"))) {
                        images[i].compress(Bitmap.CompressFormat.PNG, 100, stream);
                    }
                }
                if (EngineRegistry.hasEngine() || EglWorker.frames.get() != 0) throw new AssertionError("Preview touched the wallpaper registry");
                System.out.println("LAYOUT_OK variant=" + variant + " frames=3 registry_untouched=true");
                main.postDelayed(() -> System.exit(0), 500);
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(2); }
        });
        Looper.loop();
    }
}
