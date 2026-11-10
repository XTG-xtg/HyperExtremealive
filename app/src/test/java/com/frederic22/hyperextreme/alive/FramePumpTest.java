package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class FramePumpTest {
    private static class Driver implements FramePump.Driver {
        Runnable vsync,timer;
        long timerDelay;
        public void vsync(Runnable run,long delay){vsync=run;}
        public void cancelVsync(){vsync=null;}
        public void timer(Runnable run,long delay){timer=run;timerDelay=delay;}
        public void cancelTimer(){timer=null;}
    }
    @Test public void vsyncAndFallbackCannotSubmitTheSameFrameTwice() {
        for(boolean timerWins:new boolean[]{true,false}) {
            Driver driver=new Driver();int[] frames={0};FramePump pump=new FramePump(driver,()->frames[0]++);
            pump.request(0,100);Runnable vsync=driver.vsync,timer=driver.timer;
            if(timerWins){timer.run();vsync.run();}else{vsync.run();timer.run();}
            assertEquals(1,frames[0]);assertNull(driver.vsync);assertNull(driver.timer);
        }
    }
    @Test public void detachInvalidatesAlreadyQueuedCallbacks() {
        Driver driver=new Driver();int[] frames={0};FramePump pump=new FramePump(driver,()->frames[0]++);
        pump.request(0,100);Runnable old=driver.timer;pump.cancel();pump.request(0,100);
        old.run();assertEquals(0,frames[0]);driver.vsync.run();assertEquals(1,frames[0]);
    }
    @Test public void firstFrameDoesNotWaitForVsync() {
        Driver driver=new Driver();int[] frames={0};FramePump pump=new FramePump(driver,()->frames[0]++);
        pump.request(0,0);assertEquals(0,driver.timerDelay);driver.timer.run();assertEquals(1,frames[0]);
    }
    @Test public void expiredFallbackCanBeRearmedByAnExternalEvent() {
        Driver driver=new Driver();FramePump pump=new FramePump(driver,()->{});
        pump.request(0,-1);assertNull(driver.timer);Runnable stale=driver.vsync;
        pump.request(0,100);assertNotNull(driver.timer);stale.run();assertNotNull(driver.timer);
    }
    @Test public void duplicateRequestsCoalesceAndUrgentOnesReplaceDelay() {
        Driver driver=new Driver();FramePump pump=new FramePump(driver,()->{});
        pump.request(25,100);Runnable first=driver.vsync;pump.request(25,100);assertSame(first,driver.vsync);
        pump.request(0,100);assertNotSame(first,driver.vsync);
        first.run();assertNotNull(driver.vsync);
    }
}
