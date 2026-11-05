package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Field;

/** Drives the actual EGL worker against consumed window buffers, without changing any wallpaper. */
public final class LiveFrameProbe {
    private static long before, at;
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Handler main = new Handler(Looper.getMainLooper());
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Context system = (Context) atClass.getMethod("getSystemContext").invoke(atClass.getMethod("systemMain").invoke(null));
        Context installed = system.createPackageContext(Protocol.PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
        Context context = new ContextWrapper(installed) {
            @Override public Context getApplicationContext() { return this; }
            @Override public java.io.File getFilesDir() { return new java.io.File("/data/local/tmp/hyperextreme-photo-worker"); }
        };
        boolean photo=args.length>0&&args[0].startsWith("photo");
        int variant=photo?Integer.parseInt(args[0].substring(5)):301;
        if(photo){
            java.io.File path=new java.io.File(context.getFilesDir(),"alive/photo.png");path.getParentFile().mkdirs();
            java.nio.file.Files.copy(new java.io.File("/data/local/tmp/hyperextreme-photo-test/portrait.png").toPath(),path.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        boolean dual = args.length > 0 && "dual".equals(args[0]);
        boolean epoch = args.length > 0 && "epoch".equals(args[0]);
        EglWorker a = new EglWorker(context, !dual);
        EglWorker b = dual ? new EglWorker(context, false) : null;
        ImageReader reader = reader(main);
        ImageReader readerB = dual ? reader(main) : null;
        long start = SystemClock.uptimeMillis();
        EngineRegistry.Snapshot snapshot = epoch ? new EngineRegistry.Snapshot(1, 1, 0, 0)
            : new EngineRegistry.Snapshot(0, 0, start, 1);
        a.state(snapshot); a.attach(reader.getSurface(), 360, 801, variant, false); a.visible(true);
        if (b != null) { b.state(snapshot); b.attach(readerB.getSurface(), 360, 801, variant, false); b.visible(true); }
        if(photo){
            main.postDelayed(()->a.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),2)),500);
            main.postDelayed(()->a.state(new EngineRegistry.Snapshot(1,2,SystemClock.uptimeMillis(),3)),800);
            main.postDelayed(()->a.state(new EngineRegistry.Snapshot(2,0,SystemClock.uptimeMillis(),4)),2300);
        }
        // Measure after the old worker's 2.2s AOD cutoff; warmup must not hide the regression.
        main.postDelayed(() -> { before = a.renderedFrames; at = SystemClock.uptimeMillis(); }, 3000);
        main.postDelayed(() -> {
            long now = SystemClock.uptimeMillis(), frames = a.renderedFrames - before;
            double fps = frames * 1000d / (now - at);
            System.out.println("LIVE_FRAMES windowMs=" + (now - at) + " frames=" + frames + " fps=" + fps + " error=" + a.error);
            boolean staticPhoto=variant==602;
            if(staticPhoto&&frames>1)throw new AssertionError("Settled frame keeps redrawing: "+frames);
            if(staticPhoto)System.out.println("STATIC_IDLE_STOPPED=true");
            if(!photo)try {
                Field field = EglWorker.class.getDeclaredField("renderer"); field.setAccessible(true);
                WallpaperRenderer renderer = (WallpaperRenderer) field.get(a);
                java.lang.reflect.Method time = renderer.motion().getClass().getDeclaredMethod("time");
                time.setAccessible(true);
                System.out.println("GRADIENT_SECONDS=" + time.invoke(renderer.motion()));
            } catch (Exception e) { throw new RuntimeException(e); }
            if (b != null) {
                try {
                    Field field = EglWorker.class.getDeclaredField("motionSession"); field.setAccessible(true);
                    Object one = field.get(a), two = field.get(b);
                    if (one == null || one != two) throw new AssertionError("Independent slot motion");
                    System.out.println("SHARED_MOTION=true framesB=" + b.renderedFrames);
                } catch (Exception e) { throw new RuntimeException(e); }
            }
            long resting=a.renderedFrames;
            if(photo)a.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),5));
            main.postDelayed(() -> {
                if(photo&&a.renderedFrames<=resting)throw new AssertionError("Resting photo cannot wake");
                if(photo)System.out.println("IDLE_WAKE_RESUMED=true");
                a.visible(false); if (b != null) b.visible(false);
                main.postDelayed(() -> {
                long stopped = a.renderedFrames;
                main.postDelayed(() -> {
                    if (a.renderedFrames != stopped) throw new AssertionError("Hidden worker continues drawing");
                    System.out.println("HIDDEN_STOPPED=true");
                    a.close(); if (b != null) b.close();
                    reader.close(); if (readerB != null) readerB.close();
                    main.postDelayed(()->System.exit(a.error.isEmpty() && (staticPhoto || fps > 0) ? 0 : 2),300);
                }, 500);
                },150);
            }, 700);
        }, 6000);
        Looper.loop();
    }
    private static ImageReader reader(Handler handler) {
        ImageReader reader = ImageReader.newInstance(360, 801, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(source -> { try (Image image = source.acquireLatestImage()) { } }, handler);
        return reader;
    }
}
