package com.frederic22.hyperextreme.alive;

import com.frederic22.hyperextreme.alive.core.ExtensionPulse;
import com.frederic22.hyperextreme.alive.core.FingerprintVisualState;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExtensionStateTest {
    @Test public void notificationIsBoundedAndStaleTimeoutCannotCancelNewEvent() {
        ExtensionPulse pulse=new ExtensionPulse();
        long first=pulse.begin(0,15,true);
        assertEquals(15000,pulse.remaining(0));
        for(int now=1000;now<30000;now+=1000)pulse.begin(now,15,true);
        assertEquals(1,pulse.remaining(29999));assertFalse(pulse.active(30000));
        long next=pulse.begin(31000,5,true);
        assertFalse(pulse.expire(first,40000));assertTrue(pulse.expire(next,40000));
        assertFalse(pulse.active(40000));
    }
    @Test public void forbiddenPulseCancelsExistingEffectAndInvalidDurationIsClamped() {
        ExtensionPulse pulse=new ExtensionPulse();pulse.begin(20,Integer.MAX_VALUE,true);
        assertEquals(10000,pulse.remaining(20));assertEquals(-1,pulse.begin(30,5,false));
        assertFalse(pulse.active(31));
    }
    @Test public void fingerReleaseAndTimeoutRequireCurrentTokenAndNativeRelease() {
        FingerprintVisualState state=new FingerprintVisualState();long old=state.press(),current=state.press();
        assertFalse(state.release(old,false));assertTrue(state.suppressed());
        assertFalse(state.release(current,true));assertTrue(state.suppressed());
        assertTrue(state.release(current,false));assertFalse(state.suppressed());
        assertFalse(state.release(current,false));
    }
    @Test public void detachInvalidatesPendingFingerUp() {
        FingerprintVisualState state=new FingerprintVisualState();long token=state.press();state.clear();
        assertFalse(state.release(token,false));assertFalse(state.suppressed());
    }
}
