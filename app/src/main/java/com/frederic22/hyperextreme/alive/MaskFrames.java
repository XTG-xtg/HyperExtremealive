// Derived from OpenAlive 2d54c72a573b2f66e20b558283bef58cdb0ada60; MIT; see docs/alive/OPENALIVE_LICENSE and PROVENANCE.json.
package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.os.Handler;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 120Hz-ready mask frame streamer modeled after HyperAlive's MaskFrames:
 * 1. Pins start/end endpoint masks permanently so steady AOD/lock/home never decode or evict.
 * 2. 2-thread parallel prefetch worker pool with per-thread reusable MaskDecoder (zero GC churn)
 *    so 120Hz transitions stay 100% cache-hit without stalling the GL thread.
 * 3. Wide bidirectional look-ahead (index +/- 1..6) + 24-frame LRU + DirectByteBuffer pool +
 *    IdentityHashMap reference counting.
 * 4. Lock-free GL upload with GL_UNPACK_ALIGNMENT=1 and synchronous fallback decode on rare miss.
 */
final class MaskFrames implements AutoCloseable {
    static final int SIZE = 720, BYTES = SIZE * SIZE * 3, START = 61, END = 62;
    private static final int CACHE_LIMIT = 24;
    private static final int PREFETCH_WINDOW = 6;
    private static final int WORKER_THREADS = 2;

    private final AssetManager assets;
    private final Handler owner;
    private final Runnable ready;
    private final Object lock = new Object();
    private final LinkedHashMap<Integer, ByteBuffer> cache = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true);
    private final ArrayDeque<ByteBuffer> pool = new ArrayDeque<>();
    private final HashSet<Integer> inflight = new HashSet<>();
    private final IdentityHashMap<ByteBuffer, Integer> inUseCounts = new IdentityHashMap<>();
    private final CopyOnWriteArrayList<MaskDecoder> decoders = new CopyOnWriteArrayList<>();
    private final ThreadLocal<MaskDecoder> asyncDecoder = ThreadLocal.withInitial(() -> {
        MaskDecoder d = new MaskDecoder();
        decoders.add(d);
        return d;
    });
    private final MaskDecoder syncDecoder = new MaskDecoder();
    private final ExecutorService workers;
    private final String[] paths;
    private final int size, bytes, start, end, cacheLimit, maxAllocated;
    private volatile boolean closed;
    private ByteBuffer pinnedStart, pinnedEnd;
    private int allocatedCount;
    private int uploaded = -1, failures, decodedFrames, presentedFrames, allocatedSide, uploadedTexture;

    MaskFrames(AssetManager assets, Handler owner, Runnable ready) {
        this(assets, owner, ready, PhotoStyle.get(1));
    }

    MaskFrames(AssetManager assets, Handler owner, Runnable ready, PhotoStyle style) {
        this.assets = assets;
        this.owner = owner;
        this.ready = ready;
        this.size = style.size;
        this.bytes = size * size * 3;
        this.start = style.frames;
        this.end = start + 1;
        this.cacheLimit = Math.max(12, Math.min(CACHE_LIMIT, 24 * BYTES / Math.max(1, bytes)));
        this.maxAllocated = this.cacheLimit + 6;
        this.paths = new String[end + 1];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = "masks/" + style.folder + "/"
                + (i == start ? "start" : i == end ? "end" : String.format(Locale.ROOT, "%03d", i)) + ".rgbz";
        }
        AtomicInteger ids = new AtomicInteger(1);
        this.workers = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread t = new Thread(r, "PhotoMask-" + ids.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);
            return t;
        });
        // Pre-warm both steady endpoints and the first/last transition frames for instant 120Hz start.
        prefetch(start);
        prefetch(end);
        for (int i = 0; i < PREFETCH_WINDOW && i < start; i++) prefetch(i);
        for (int i = 1; i <= PREFETCH_WINDOW && start - i >= 0; i++) prefetch(start - i);
    }

    static int index(long micros) {
        return micros <= 0 ? START : micros >= 483667 ? END : Math.min(60, Math.round(micros * 120f / 1000000f));
    }

    boolean upload(int index, int texture) {
        if (closed || texture == 0) return false;
        if (index < 0 || index > end) throw new IllegalArgumentException("Mask index " + index);

        // Wide bidirectional prefetch to stay ahead of 120Hz expansion and mid-flight reversal.
        if (index == start) {
            for (int i = 0; i < PREFETCH_WINDOW && i < start; i++) prefetch(i);
        } else if (index == end) {
            for (int i = 1; i <= PREFETCH_WINDOW && start - i >= 0; i++) prefetch(start - i);
        } else {
            for (int d = 1; d <= PREFETCH_WINDOW; d++) {
                if (index + d < start) prefetch(index + d);
                if (index - d >= 0) prefetch(index - d);
            }
        }

        synchronized (lock) {
            if (texture != uploadedTexture) {
                uploadedTexture = texture;
                uploaded = -1;
                allocatedSide = 0;
            }
            if (uploaded == index) return true;
        }

        ByteBuffer value = getOrDecodeSync(index);
        if (value == null) return false;

        try {
            int side = index == end ? 720 : size;
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
            synchronized (value) {
                value.position(0);
                if (allocatedSide != side) {
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB, side, side, 0,
                        GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, value);
                    allocatedSide = side;
                } else {
                    GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, side, side,
                        GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, value);
                }
            }
            if (Diagnostics.TRACE) {
                if (index < start) presentedFrames++;
                if (index == end) Log.i("AliveClean", "Frame mask endpoint: decoded=" + decodedFrames + " presented=" + presentedFrames);
            }
            synchronized (lock) {
                uploaded = index;
            }
            return true;
        } finally {
            releaseInUse(value);
        }
    }

    boolean failed() {
        synchronized (lock) {
            return failures >= 3;
        }
    }

    private ByteBuffer getOrDecodeSync(int index) {
        ByteBuffer syncBuf;
        synchronized (lock) {
            if (closed) return null;
            if (index == start && pinnedStart != null) {
                retainBufferLocked(pinnedStart);
                return pinnedStart;
            }
            if (index == end && pinnedEnd != null) {
                retainBufferLocked(pinnedEnd);
                return pinnedEnd;
            }
            ByteBuffer hit = cache.get(index);
            if (hit != null) {
                retainBufferLocked(hit);
                return hit;
            }
            syncBuf = acquireBufferLocked(index);
            if (syncBuf == null) return null;
        }

        boolean ok = decodeInto(syncDecoder, index, syncBuf);
        if (!ok) {
            synchronized (lock) {
                failures++;
                unretainBufferLocked(syncBuf);
                returnToPoolOrDropLocked(syncBuf, index);
            }
            return null;
        }

        synchronized (lock) {
            if (closed) {
                unretainBufferLocked(syncBuf);
                returnToPoolOrDropLocked(syncBuf, index);
                return null;
            }
            decodedFrames++;
            if (index == start) {
                if (pinnedStart == null) {
                    pinnedStart = syncBuf;
                } else if (pinnedStart != syncBuf) {
                    unretainBufferLocked(syncBuf);
                    returnToPoolOrDropLocked(syncBuf, index);
                    retainBufferLocked(pinnedStart);
                }
                return pinnedStart;
            }
            if (index == end) {
                if (pinnedEnd == null) {
                    pinnedEnd = syncBuf;
                } else if (pinnedEnd != syncBuf) {
                    unretainBufferLocked(syncBuf);
                    returnToPoolOrDropLocked(syncBuf, index);
                    retainBufferLocked(pinnedEnd);
                }
                return pinnedEnd;
            }
            ByteBuffer existing = cache.get(index);
            if (existing != null) {
                if (existing != syncBuf) {
                    unretainBufferLocked(syncBuf);
                    returnToPoolOrDropLocked(syncBuf, index);
                    retainBufferLocked(existing);
                }
                return existing;
            }
            while (cache.size() >= cacheLimit) {
                if (!evictEldestLocked()) break;
            }
            if (cache.size() < cacheLimit) {
                cache.put(index, syncBuf);
            }
            return syncBuf;
        }
    }

    private void prefetch(int index) {
        if (index < 0 || index > end || closed) return;
        synchronized (lock) {
            if (closed || failures >= 3) return;
            if (index == start && pinnedStart != null) return;
            if (index == end && pinnedEnd != null) return;
            if (cache.containsKey(index) || !inflight.add(index)) return;
        }
        try {
            workers.execute(() -> decodeAsync(index));
        } catch (RuntimeException rejected) {
            synchronized (lock) {
                inflight.remove(index);
            }
        }
    }

    private void decodeAsync(int index) {
        ByteBuffer buffer;
        synchronized (lock) {
            if (closed) {
                inflight.remove(index);
                return;
            }
            if ((index == start && pinnedStart != null)
                || (index == end && pinnedEnd != null)
                || cache.containsKey(index)) {
                inflight.remove(index);
                return;
            }
            buffer = acquireBufferLocked(index);
            if (buffer == null) {
                inflight.remove(index);
                return;
            }
        }

        boolean ok = decodeInto(asyncDecoder.get(), index, buffer);
        synchronized (lock) {
            inflight.remove(index);
            unretainBufferLocked(buffer);
            if (!ok || closed) {
                if (!ok) failures++;
                returnToPoolOrDropLocked(buffer, index);
                if (!closed && !ok && owner != null && ready != null) owner.post(ready);
                return;
            }
            decodedFrames++;
            if (index == start) {
                if (pinnedStart == null) pinnedStart = buffer;
                else returnToPoolOrDropLocked(buffer, index);
            } else if (index == end) {
                if (pinnedEnd == null) pinnedEnd = buffer;
                else returnToPoolOrDropLocked(buffer, index);
            } else if (!cache.containsKey(index)) {
                while (cache.size() >= cacheLimit) {
                    if (!evictEldestLocked()) break;
                }
                if (cache.size() < cacheLimit) cache.put(index, buffer);
                else returnToPoolOrDropLocked(buffer, index);
            } else {
                returnToPoolOrDropLocked(buffer, index);
            }
        }
    }

    private boolean decodeInto(MaskDecoder decoder, int index, ByteBuffer target) {
        int side = index == end ? 720 : size;
        int expected = side * side * 3;
        try {
            synchronized (decoder) {
                if (closed) return false;
                synchronized (target) {
                    try (InputStream in = assets.open(paths[index])) {
                        decoder.decode(in, target, expected);
                    }
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            if (!closed) Log.e("AliveClean", "Mask decode failed index=" + index, e);
            return false;
        }
    }

    private ByteBuffer acquireBufferLocked(int index) {
        if (closed) return null;
        if (index == end && size != 720) {
            ByteBuffer endBuf = ByteBuffer.allocateDirect(BYTES).order(ByteOrder.nativeOrder());
            retainBufferLocked(endBuf);
            return endBuf;
        }
        ByteBuffer buf = pool.pollFirst();
        if (buf != null) {
            retainBufferLocked(buf);
            return buf;
        }
        if (allocatedCount < maxAllocated) {
            allocatedCount++;
            buf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            retainBufferLocked(buf);
            return buf;
        }
        Iterator<Map.Entry<Integer, ByteBuffer>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ByteBuffer> entry = it.next();
            ByteBuffer candidate = entry.getValue();
            if (!isBufferInUseLocked(candidate)) {
                it.remove();
                retainBufferLocked(candidate);
                return candidate;
            }
        }
        return null;
    }

    private boolean evictEldestLocked() {
        Iterator<Map.Entry<Integer, ByteBuffer>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ByteBuffer> entry = it.next();
            ByteBuffer candidate = entry.getValue();
            if (!isBufferInUseLocked(candidate)) {
                it.remove();
                returnToPoolOrDropLocked(candidate, entry.getKey());
                return true;
            }
        }
        return false;
    }

    private void releaseInUse(ByteBuffer buf) {
        if (buf == null) return;
        synchronized (lock) {
            unretainBufferLocked(buf);
            returnToPoolOrDropLocked(buf, -1);
        }
    }

    private void retainBufferLocked(ByteBuffer buf) {
        Integer cur = inUseCounts.get(buf);
        inUseCounts.put(buf, cur == null ? 1 : cur + 1);
    }

    private void unretainBufferLocked(ByteBuffer buf) {
        Integer cur = inUseCounts.get(buf);
        if (cur == null) return;
        if (cur <= 1) inUseCounts.remove(buf);
        else inUseCounts.put(buf, cur - 1);
    }

    private boolean isBufferInUseLocked(ByteBuffer buf) {
        Integer cur = inUseCounts.get(buf);
        return cur != null && cur > 0;
    }

    private boolean isInCacheIdentityLocked(ByteBuffer buf) {
        for (ByteBuffer v : cache.values()) {
            if (v == buf) return true;
        }
        return false;
    }

    private boolean isInPoolIdentityLocked(ByteBuffer buf) {
        for (ByteBuffer v : pool) {
            if (v == buf) return true;
        }
        return false;
    }

    private void returnToPoolOrDropLocked(ByteBuffer buf, int index) {
        if (buf == null) return;
        if (index == end && size != 720) return;
        if (closed) {
            if (!isBufferInUseLocked(buf)) allocatedCount = inUseCounts.size();
            return;
        }
        if (buf == pinnedStart || buf == pinnedEnd
            || isBufferInUseLocked(buf)
            || isInCacheIdentityLocked(buf)
            || isInPoolIdentityLocked(buf)) {
            return;
        }
        int resident = pool.size() + cache.size()
            + (pinnedStart != null ? 1 : 0)
            + (pinnedEnd != null ? 1 : 0)
            + inUseCounts.size();
        if (resident < maxAllocated && buf.capacity() == bytes) {
            pool.addLast(buf);
        } else {
            allocatedCount = Math.max(inUseCounts.size(), allocatedCount - 1);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            cache.clear();
            pool.clear();
            inflight.clear();
            pinnedStart = null;
            pinnedEnd = null;
        }
        workers.shutdownNow();
        for (MaskDecoder d : decoders) {
            synchronized (d) {
                try { d.close(); } catch (RuntimeException ignored) {}
            }
        }
        decoders.clear();
        synchronized (syncDecoder) {
            syncDecoder.close();
        }
    }
}
