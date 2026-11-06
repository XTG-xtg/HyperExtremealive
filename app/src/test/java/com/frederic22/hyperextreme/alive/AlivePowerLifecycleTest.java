package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.os.PowerManager;
import com.frederic22.hyperextreme.alive.core.AliveState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercise the actual registry, not just a power gate disconnected from preset scenes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class AlivePowerLifecycleTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final PowerManager power = context.getSystemService(PowerManager.class);
    private long at = 1000;

    @Before public void startAfterPreviousTrace() {
        // Robolectric can reuse the registry's static state across test methods.
        AliveState state = ReflectionHelpers.getStaticField(EngineRegistry.class, "STATE");
        at = Math.max(at, state.lastEventMs + 100_000);
    }

    private EngineRegistry.Snapshot event(int requested, String reason, boolean interactive) {
        return event(requested, reason, interactive, 1000);
    }

    private EngineRegistry.Snapshot event(int requested, String reason, boolean interactive, long delayMs) {
        shadowOf(power).setIsInteractive(interactive);
        EngineRegistry.signal(context, requested, at += delayMs, reason);
        shadowOf(Looper.getMainLooper()).idle();
        return EngineRegistry.snapshot();
    }

    private void variant(int id) {
        com.frederic22.hyperextreme.alive.Config.prefs(context).edit().putInt("variant", id).commit();
    }

    @Test public void allFamiliesHoldAodUntilRealWakeAndReturnOnSecondPowerPress() {
        // Cosmic, Phoenix, Bubble, Gradient, Video, Stereo, and both photo families.
        for (int id : new int[]{1, 101, 201, 301, 401, 501, 601, 602}) {
            variant(id);
            assertEquals(AliveState.HOME, event(2, "user-present", true).mode);
            EngineRegistry.Snapshot sleep = event(0, "screen-off", true);
            assertEquals("sleep prefeed variant=" + id, AliveState.AOD, sleep.mode);
            assertEquals("visibility must not undo sleep variant=" + id,
                AliveState.AOD, event(1, "visible-slot-2", true).mode);

            for (int i = 0; i < 12; i++) {
                EngineRegistry.Snapshot held = event(i % 2 == 0 ? 1 : 2,
                    i % 2 == 0 ? "scrim-keyguard" : "visible-slot-1", false);
                assertEquals("AOD remains collapsed variant=" + id, AliveState.AOD, held.mode);
                assertEquals("hints must not restart the collapse", sleep.sequence, held.sequence);
                assertEquals(sleep.startMs, held.startMs);
            }
            assertEquals(AliveState.AOD, event(1, "power-wake", false).mode);
            EngineRegistry.Snapshot wake = event(1, "screen-on", true);
            assertEquals(AliveState.AOD, wake.from);
            assertEquals(AliveState.LOCK, wake.mode);
            assertEquals("late doze must not undo wake", wake.sequence,
                event(0, "scrim-aod", true, 10).sequence);

            // Press power again during the opening, then receive more lock/home hints.
            EngineRegistry.Snapshot secondSleep = event(0, "screen-off", true, 100);
            assertEquals(AliveState.LOCK, secondSleep.from);
            assertEquals(AliveState.AOD, secondSleep.mode);
            assertEquals(secondSleep.sequence, event(1, "scrim-keyguard", true).sequence);
            assertEquals(secondSleep.sequence, event(2, "visible-slot-1", false).sequence);
            assertEquals(AliveState.HOME, event(2, "biometric", true).mode);
        }
    }

    @Test public void noninteractiveStartupAndVariantChangesDoNotExpand() {
        for (int id : new int[]{1, 601, 101, 201, 602, 301, 401, 501}) {
            variant(id);
            assertEquals(AliveState.AOD, event(1, "visible-slot-2", false).mode);
            assertEquals(AliveState.AOD, event(2, "scrim-unlocked", false).mode);
        }
        assertEquals(AliveState.LOCK, event(1, "display-awake", true).mode);
    }

    @Test public void staleSleepCannotRearmFenceAfterWake() {
        variant(1);
        event(0, "screen-off", false);
        EngineRegistry.Snapshot wake = event(1, "screen-on", true);
        EngineRegistry.signal(context, 0, at - 100, "screen-off");
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(wake.sequence, EngineRegistry.snapshot().sequence);
        assertEquals(AliveState.LOCK, event(1, "visible-slot-2", true).mode);
    }

    @Test public void orbTracksActuallySettleToAodAndCanReverseDuringOpening() throws Exception {
        for (int id : new int[]{1, 101, 201}) {
            variant(id);
            WallpaperMotion motion = orb(id, AliveState.LOCK);
            WallpaperMotion aodReference = orb(id, AliveState.AOD);
            MotionSession session = new MotionSession(motion);
            draw(session, event(2, "user-present", true), at);
            settle(session);
            draw(session, event(0, "screen-off", true), at);
            draw(session, event(1, "visible-slot-2", true), at);
            draw(session, event(1, "scrim-keyguard", false), at);
            settle(session);
            assertArrayEquals("collapsed AOD variant=" + id, values(aodReference), values(motion), .0001f);
            assertFalse(motion.active());

            EngineRegistry.Snapshot wake = event(1, "screen-on", true);
            draw(session, wake, at);
            at += 100;
            draw(session, wake, at);
            assertTrue("real wake starts expansion variant=" + id, motion.active());
            assertFalse(java.util.Arrays.equals(values(aodReference), values(motion)));
            draw(session, event(0, "screen-off", true, 100), at);
            draw(session, event(1, "scrim-keyguard", false), at);
            settle(session);
            assertArrayEquals("second power press restores AOD variant=" + id,
                values(aodReference), values(motion), .0001f);
            assertFalse(motion.active());
        }
    }

    private WallpaperMotion orb(int id, int mode) throws Exception {
        return id < 201 ? new CosmicMotion(context.getAssets(), id, false, mode)
            : new BubbleMotion(context.getAssets(), id, false, mode, false);
    }

    private float[] values(WallpaperMotion motion) {
        return motion instanceof CosmicMotion ? ((CosmicMotion) motion).values : ((BubbleMotion) motion).values;
    }

    private void settle(MotionSession session) {
        at += 4000;
        draw(session, EngineRegistry.snapshot(), at);
    }

    private void draw(MotionSession session, EngineRegistry.Snapshot state, long timeMs) {
        // CPU geometry/material parameters only; this does not submit GL frames.
        session.frame(state.sequence, state.mode, state.startMs * 1000000L, timeMs * 1000000L, () -> {});
    }
}
