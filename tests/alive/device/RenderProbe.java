package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.*;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** Standalone app_process probe: pbuffer only, no services, preferences or actual wallpaper changes. */
public final class RenderProbe {
    public static void main(String[] args) throws Exception {
        if (Looper.myLooper() == null) Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context system = (Context) at.getMethod("getSystemContext").invoke(thread);
        Context installed = system.createPackageContext(Protocol.PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
        File directory = new File("/data/local/tmp/hyperextreme-render-probe");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Probe directory");
        Context fixture = new ContextWrapper(installed) {
            @Override public File getFilesDir() { return directory; }
        };
        File photos = new File(directory, "alive");
        photos.mkdirs();
        Bitmap test = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(test);
        canvas.drawColor(Color.rgb(25, 50, 70));
        Paint paint = new Paint(3);
        paint.setColor(Color.rgb(240, 190, 100));
        canvas.drawCircle(800, 450, 250, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRect(790, 100, 810, 800, paint);
        try (FileOutputStream out = new FileOutputStream(new File(photos, "photo.png"))) {
            test.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        test.recycle();
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("eglInitialize");
        EGLConfig config = CosmicEgl.choose(display, EGL14.EGL_PBUFFER_BIT);
        EGLContext gl = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        int width = 360, height = 801;
        EGLSurface surface = EGL14.eglCreatePbufferSurface(display, config,
            new int[]{EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE}, 0);
        if (!EGL14.eglMakeCurrent(display, surface, surface, gl)) throw new IllegalStateException("eglMakeCurrent");
        System.out.println("GPU=" + GLES30.glGetString(GLES30.GL_RENDERER));
        for (int variant : new int[]{1, 201, 301, 501, 601}) {
            WallpaperRenderer renderer = variant >= 601
                ? new PhotoRenderer(fixture, width, height, 0, variant, false, () -> {})
                : WallpaperRenderer.create(fixture.getAssets(), variant, false, width, height, 0, false);
            renderer.visible(true);
            renderer.continuous(true);
            long time = 1000000000L;
            for (int mode = 0; mode <= 2; mode++) {
                renderer.motion().change(mode, true);
                for (int frame = 0; frame <= 180; frame++) {
                    renderer.motion().advance(time, 1);
                    time += 16666667L;
                }
                renderer.render();
                int error = GLES30.glGetError();
                if (error != GLES30.GL_NO_ERROR) throw new IllegalStateException("GL error " + error);
                ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
                GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba);
                int[] pixels = new int[width * height];
                int lit = 0;
                for (int y = height - 1; y >= 0; y--) for (int x = 0; x < width; x++) {
                    int r = rgba.get() & 255, g = rgba.get() & 255, b = rgba.get() & 255;
                    rgba.get();
                    pixels[y * width + x] = Color.rgb(r, g, b);
                    if (r + g + b > 30) lit++;
                }
                Bitmap image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
                File output = new File(directory, variant + "-" + mode + ".png");
                try (FileOutputStream out = new FileOutputStream(output)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
                image.recycle();
                if (lit == 0) throw new AssertionError("Black frame variant=" + variant + " mode=" + mode);
                System.out.println("FRAME variant=" + variant + " mode=" + mode + " lit=" + lit);
            }
            renderer.close();
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display, surface);
        EGL14.eglDestroyContext(display, gl);
        EGL14.eglTerminate(display);
        System.out.println("RENDER_PROBE_OK (not wallpaper-binding or visual-parity verification)");
        System.exit(0);
    }
}
