package com.frederic22.hyperextreme.alive;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnlockSignalsTest {
    public static class Callbacks {
        public void notifyKeyguardGoingAway(boolean leaving) { }
        public void notifyKeyguardGoingAway(int flags) { }
        public void startKeyguardExitAnimation(long start,long duration) { }
        public void startKeyguardExitAnimation(boolean attempt) { }
        public void handleCancelKeyguardExitAnimation() { }
        public void finishSurfaceBehindRemoteAnimation(boolean showKeyguard) { }
        public void finishSurfaceBehindRemoteAnimation(int flags) { }
    }
    @Test public void onlyConfirmedBooleanExitTriggersHome() throws Exception {
        Method m=Callbacks.class.getMethod("notifyKeyguardGoingAway",boolean.class);
        assertTrue(UnlockSignals.goingAway(m,Arrays.asList(true)));
        assertFalse(UnlockSignals.goingAway(m,Arrays.asList(false)));
        assertFalse(UnlockSignals.goingAway(m,Collections.emptyList()));
        assertFalse(UnlockSignals.goingAway(Callbacks.class.getMethod("notifyKeyguardGoingAway",int.class),Arrays.asList(1)));
    }
    @Test public void fallbackRequiresKnownExitSignature() throws Exception {
        assertTrue(UnlockSignals.exitStart(Callbacks.class.getMethod("startKeyguardExitAnimation",long.class,long.class)));
        assertFalse(UnlockSignals.exitStart(Callbacks.class.getMethod("startKeyguardExitAnimation",boolean.class)));
        assertFalse(UnlockSignals.exitStart(Callbacks.class.getMethod("handleCancelKeyguardExitAnimation")));
    }
    @Test public void onlyExplicitRelockBranchCancelsHome() throws Exception {
        Method m=Callbacks.class.getMethod("finishSurfaceBehindRemoteAnimation",boolean.class);
        assertTrue(UnlockSignals.cancelled(m,Arrays.asList(true)));
        assertFalse(UnlockSignals.cancelled(m,Arrays.asList(false)));
        assertFalse(UnlockSignals.cancelled(m,Collections.emptyList()));
        assertFalse(UnlockSignals.cancelled(Callbacks.class.getMethod("finishSurfaceBehindRemoteAnimation",int.class),Arrays.asList(1)));
        assertFalse(UnlockSignals.cancelled(Callbacks.class.getMethod("handleCancelKeyguardExitAnimation"),Collections.emptyList()));
    }
}
