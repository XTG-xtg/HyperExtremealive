package com.frederic22.hyperextreme.alive;

import android.view.Display;
import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayFramePolicyTest {
    @Test public void visibleDozeKeepsFallbackAfterThreeSecondsIncludingSuspend() {
        for (int display : new int[]{Display.STATE_ON, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND}) {
            assertTrue(DisplayFramePolicy.canRender(false, display));
            assertEquals(33, DisplayFramePolicy.fallback(false, display, false, false, 25, 10_000, 3000));
            assertEquals(16, DisplayFramePolicy.fallback(false, display, false, false, 0, 10_000, 3000));
        }
    }
    @Test public void trueOffStopsEvenBootstrapAndExternalInvalidation() {
        assertFalse(DisplayFramePolicy.canRender(false, Display.STATE_OFF));
        assertEquals(-1, DisplayFramePolicy.fallback(false, Display.STATE_OFF, false, true, 0, 0, 3000));
    }
    @Test public void unknownAwakeAndPreviewDoNotGainUnboundedTimer() {
        assertEquals(-1, DisplayFramePolicy.fallback(false, Display.STATE_UNKNOWN, false, false, 0, 4000, 3000));
        assertEquals(-1, DisplayFramePolicy.fallback(false, Display.STATE_ON, true, false, 0, 4000, 3000));
        assertEquals(-1, DisplayFramePolicy.fallback(true, Display.STATE_DOZE, false, false, 0, 4000, 3000));
        assertEquals(100, DisplayFramePolicy.fallback(false, Display.STATE_ON, true, false, 0, 2000, 3000));
        assertEquals(0, DisplayFramePolicy.fallback(false, Display.STATE_ON, true, true, 0, 4000, 3000));
    }
    @Test public void sceneMatchesRejectsMismatchedInteractiveAndMode() {
        assertTrue(DisplayFramePolicy.sceneMatches(true, Display.STATE_ON, true, 0));
        assertTrue(DisplayFramePolicy.sceneMatches(false, Display.STATE_UNKNOWN, true, 0));
        assertTrue(DisplayFramePolicy.sceneMatches(false, Display.STATE_ON, true, 1));
        assertTrue(DisplayFramePolicy.sceneMatches(false, Display.STATE_ON, true, 2));
        assertFalse(DisplayFramePolicy.sceneMatches(false, Display.STATE_ON, true, 0));
        assertTrue(DisplayFramePolicy.sceneMatches(false, Display.STATE_DOZE, false, 0));
        assertFalse(DisplayFramePolicy.sceneMatches(false, Display.STATE_DOZE, false, 1));
        assertFalse(DisplayFramePolicy.sceneMatches(false, Display.STATE_DOZE, false, 2));
    }
}
