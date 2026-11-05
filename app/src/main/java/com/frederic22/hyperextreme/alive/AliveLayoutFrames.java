package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import android.os.*;
import android.os.Process;
import java.nio.ByteBuffer;

/** Three stills from the real renderers, with no wallpaper registration or sustained render loop. */
final class AliveLayoutFrames implements AutoCloseable {
    interface Callback { void complete(Bitmap[] images, String error); }
    private final Context context;
    private final int variant, width, height;
    private final boolean dark;
    private final Callback callback;
    private final HandlerThread thread = new HandlerThread("AliveLayout", Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler handler;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface = EGL14.EGL_NO_SURFACE;
    private WallpaperRenderer renderer;
    private final Bitmap[] images = new Bitmap[3];
    private int mode;
    private long started;
    private volatile boolean cancelled;
    private boolean finished;
    private final Runnable draw = this::render;

    AliveLayoutFrames(Context context, int variant, boolean dark, int width, int height, Callback callback) {
        this.context = context.getApplicationContext(); this.variant = variant; this.dark = dark;
        this.width = width; this.height = height; this.callback = callback;
        thread.start(); handler = new Handler(thread.getLooper()); handler.post(this::start);
    }

    private void start() {
        try {
            if (cancelled) { finish("cancelled"); return; }
            display = EglWorker.acquireDisplay();
            EGLConfig config = CosmicEgl.choose(display, EGL14.EGL_PBUFFER_BIT);
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            surface = EGL14.eglCreatePbufferSurface(display, config,
                new int[]{EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE}, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE ||
                !EGL14.eglMakeCurrent(display, surface, surface, eglContext)) throw new IllegalStateException("Layout EGL setup failed");
            next();
        } catch (Throwable error) { finish(error.toString()); }
    }

    private void next() {
        if (finished) return;
        try {
            if (cancelled) { finish("cancelled"); return; }
            if (renderer != null) { renderer.close(); renderer = null; }
            if (mode == 3) { finish(""); return; }
            started = SystemClock.uptimeMillis();
            Runnable invalidate = () -> { if (!finished && !cancelled) { handler.removeCallbacks(draw); handler.post(draw); } };
            renderer = variant >= 601
                ? new PhotoRenderer(context, width, height, mode, variant, false, invalidate)
                : WallpaperRenderer.create(context.getAssets(), variant, dark, width, height, mode, false, invalidate);
            renderer.continuous(false);
            renderer.motion().change(mode, false);
            renderer.motion().advance(1_000_000_000L, 1f);
            handler.post(draw);
        } catch (Throwable error) { finish(error.toString()); }
    }

    private void render() {
        if (finished) return;
        try {
            if (cancelled) { finish("cancelled"); return; }
            renderer.render();
            if (!renderer.frameReady()) {
                if (SystemClock.uptimeMillis() - started > 4000) throw new IllegalStateException("Layout frame timed out");
                handler.removeCallbacks(draw); handler.postDelayed(draw, 16); return;
            }
            ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba);
            if (GLES30.glGetError() != GLES30.GL_NO_ERROR) throw new IllegalStateException("Layout readback failed");
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int i = ((height - 1 - y) * width + x) * 4;
                pixels[y * width + x] = 0xff000000 | ((rgba.get(i) & 255) << 16) |
                    ((rgba.get(i + 1) & 255) << 8) | (rgba.get(i + 2) & 255);
            }
            images[mode++] = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            handler.removeCallbacks(draw);
            handler.post(this::next);
        } catch (Throwable error) { finish(error.toString()); }
    }

    private void finish(String error) {
        if (finished) return;
        finished = true; handler.removeCallbacksAndMessages(null);
        try { if (renderer != null) renderer.close(); } catch (RuntimeException ignored) { }
        renderer = null;
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
            EglWorker.releaseDisplay(); EGL14.eglReleaseThread(); display = EGL14.EGL_NO_DISPLAY;
        }
        if (cancelled || !error.isEmpty()) for (Bitmap image : images) if (image != null) image.recycle();
        if (!cancelled) callback.complete(error.isEmpty() ? images : new Bitmap[0], error);
        thread.quitSafely();
    }

    @Override public void close() { cancelled = true; handler.post(() -> finish("cancelled")); }
}
