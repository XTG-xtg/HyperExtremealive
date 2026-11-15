package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class RenderRecoveryTest {
    @Test public void failuresHaveABoundedBackoff() {
        RenderRecovery recovery=new RenderRecovery();
        assertEquals(100,recovery.failed(0));
        assertEquals(350,recovery.failed(100));
        assertEquals(1000,recovery.failed(450));
        assertEquals(-1,recovery.failed(1450));
        assertFalse(recovery.resume(1451));
        assertFalse(recovery.resume(11449));
        assertTrue(recovery.resume(11450));
        assertEquals(100,recovery.failed(11451));
    }
    @Test public void oneGoodFrameCannotCreateAnInfiniteRetryLoop() {
        RenderRecovery recovery=new RenderRecovery();
        for(int i=0;i<3;i++){recovery.drawn(i*1000);assertTrue(recovery.failed(i*1000+1)>=0);}
        recovery.drawn(3000);
        assertEquals(-1,recovery.failed(3001));
    }
    @Test public void sustainedHealthySurfaceAndNewSurfaceResetBudget() {
        RenderRecovery recovery=new RenderRecovery();
        recovery.failed(0);recovery.failed(1);recovery.failed(2);
        recovery.drawn(10);recovery.drawn(5010);
        assertEquals(100,recovery.failed(5011));
        recovery.failed(5012);recovery.reset();
        assertEquals(100,recovery.failed(5013));
    }
}
