package com.frederic22.hyperextreme.alive;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Shared CPU animation only. Each slot retains its own EGL context and GL resources. */
final class MotionSession {
    interface Factory { WallpaperMotion create() throws IOException; }
    private static final Map<String, MotionSession> SHARED = new HashMap<>();
    final WallpaperMotion motion;
    private final Set<Object> visible = Collections.newSetFromMap(new IdentityHashMap<>());
    private int references;
    private long sequence = -1, lastFrame = -1;
    private boolean initialized;
    private final boolean liveTimeline;

    MotionSession(WallpaperMotion motion) { this(motion, false); }
    MotionSession(WallpaperMotion motion, boolean liveTimeline) { this.motion = motion; this.liveTimeline = liveTimeline; }

    static synchronized MotionSession acquire(String key, Factory factory) throws IOException {
        return acquire(key, factory, false);
    }
    static synchronized MotionSession acquire(String key, Factory factory, boolean liveTimeline) throws IOException {
        MotionSession session = SHARED.get(key);
        if (session == null) {
            session = new MotionSession(factory.create(), liveTimeline);
            SHARED.put(key, session);
        }
        session.references++;
        return session;
    }

    static synchronized void release(String key, MotionSession session, Object owner) {
        session.visible(owner, false);
        if (--session.references == 0) SHARED.remove(key, session);
    }

    synchronized void visible(Object owner, boolean value) {
        boolean wasActive = !visible.isEmpty();
        if (value) visible.add(owner); else visible.remove(owner);
        // A real display's power transition keeps its event-time epoch while hidden.
        // No GPU work runs there; the next event/frame evaluates the elapsed transition.
        if (wasActive && visible.isEmpty() && !liveTimeline) {
            motion.pause();
            lastFrame = -1;
        }
    }

    synchronized void frame(long eventSequence, int mode, long eventNanos, long frameNanos, Runnable render) {
        long now = Math.max(frameNanos, lastFrame);
        if (!initialized) {
            // Uptime zero is not a scene start: seeding from it jumps gradient color time by hours.
            boolean recent = eventSequence > 0 && eventNanos > 0 && now - eventNanos < 5_000_000_000L;
            motion.change(mode, recent);
            motion.advance(recent ? Math.min(now, eventNanos) : now, 1f);
            initialized = true;
            sequence = eventSequence;
        } else if (eventSequence > sequence) {
            long at = lastFrame < 0 ? now : Math.max(lastFrame, Math.min(now, eventNanos));
            motion.advance(at, 1f);
            motion.change(mode, true);
            motion.advance(at, 1f);
            sequence = eventSequence;
        }
        if (now != lastFrame) motion.advance(now, 1f);
        lastFrame = now;
        // Hold the CPU snapshot stable while this slot uploads uniforms and submits draw calls.
        render.run();
    }
}
