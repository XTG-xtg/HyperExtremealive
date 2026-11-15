package com.frederic22.hyperextreme.alive;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.function.Consumer;

/** Short CPU-only handoff from preview/home to the first lock surface; never shares GL objects. */
final class PhotoImageCache {
    static final PhotoImageCache SHARED = new PhotoImageCache(32 * 1024 * 1024, 30_000);
    interface Decoder { Bitmap decode() throws IOException; }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int maxBytes;
    private final long retentionMs;
    private String key;
    private Bitmap bitmap;
    private final Runnable expire = this::clear;

    PhotoImageCache(int maxBytes, long retentionMs) {
        this.maxBytes = maxBytes;
        this.retentionMs = retentionMs;
    }

    // The upload holds the lease, so expiry/another slot cannot recycle a bitmap in use.
    synchronized void use(String next, Decoder decoder, Consumer<Bitmap> upload) throws IOException {
        handler.removeCallbacks(expire);
        if (!next.equals(key)) clear();
        Bitmap image = bitmap;
        if (image == null) {
            image = decoder.decode();
            if (image == null) throw new IOException("Photo is missing or cannot be decoded");
            if (image.getAllocationByteCount() <= maxBytes) { bitmap = image; key = next; }
        }
        try { upload.accept(image); }
        finally {
            if (image != bitmap) image.recycle();
            if (bitmap != null) handler.postDelayed(expire, retentionMs);
        }
    }

    synchronized void clear() {
        handler.removeCallbacks(expire);
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        key = null;
    }
}
