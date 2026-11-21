package com.frederic22.hyperextreme.alive;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class MotionSessionStabilityTest {
    private static final class StubMotion implements WallpaperMotion {
        int lastMode = -1;
        public void change(int next, boolean animate) { lastMode = next; }
        public void advance(long now, float speed) {}
        public void follow(float x, float y) {}
        public void pause() {}
        public boolean active() { return false; }
    }

    @Test
    public void staleSequenceSlotIsRejectedWithoutRendering() {
        StubMotion motion = new StubMotion();
        MotionSession session = new MotionSession(motion, true);
        AtomicInteger renders = new AtomicInteger();

        assertTrue(session.frame(10, 0, 1_000_000_000L, 1_000_000_000L, renders::incrementAndGet));
        assertTrue(session.frame(11, 2, 2_000_000_000L, 2_000_000_000L, renders::incrementAndGet));
        assertEquals(2, renders.get());
        assertEquals(2, motion.lastMode);

        // Stale AOD slot must not render the newer HOME CPU state under old AOD presentation.
        assertFalse(session.frame(10, 0, 1_000_000_000L, 2_016_000_000L, renders::incrementAndGet));
        assertEquals(2, renders.get());
        assertEquals(2, motion.lastMode);
    }

    @Test
    public void nearestRotationPreservesFractionalDegrees() {
        assertEquals(-359.25f, SceneMotion.nearestRotation(0.75f, -360f), 0.0001f);
        assertEquals(-360.75f, SceneMotion.nearestRotation(-0.75f, -360f), 0.0001f);
        assertEquals(-360f, SceneMotion.nearestRotation(-720f, -360f), 0.0001f);
    }

    @Test
    public void infinityFingerprintUnlockSuppressesTransientLockFlash() {
        InfinityMotion n8 = InfinityMotion.create(808, 0);
        n8.advance(1_000_000_000L, 1f);
        n8.change(1, true);
        n8.advance(1_006_000_000L, 1f);
        n8.change(2, true);
        n8.advance(1_016_000_000L, 1f);
        assertEquals(0f, n8.values[InfinityMotion.LOCK], 0.0001f);
        assertEquals(1f, n8.values[InfinityMotion.EXIT], 0.0001f);

        InfinityGeometryMotion geo = (InfinityGeometryMotion) InfinityMotion.create(814, 0);
        geo.advance(1_000_000_000L, 1f);
        geo.change(1, true);
        geo.advance(1_006_000_000L, 1f);
        geo.change(2, true);
        geo.advance(1_016_000_000L, 1f);
        assertEquals(0f, geo.geometry[InfinityGeometryMotion.LOCK_BG], 0.0001f);
        assertEquals(0f, geo.geometry[InfinityGeometryMotion.FACES], 0.0001f);
    }
}
