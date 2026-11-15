package com.frederic22.hyperextreme.alive;

import android.view.Display;
import org.junit.Test;
import static org.junit.Assert.*;

/** Surface lifecycle can change on main while the GL thread is inside render(). */
public class FrameSubmissionTest {
    @Test public void mainThreadHideAndOffRevokeAnAlreadyRenderingFrame(){
        assertTrue(canSubmit(true,true,Display.STATE_ON,Display.STATE_ON,10,10));
        assertFalse("hide not yet consumed by GL",canSubmit(true,false,Display.STATE_ON,Display.STATE_ON,10,10));
        assertFalse("OFF not yet consumed by GL",canSubmit(true,true,Display.STATE_ON,Display.STATE_OFF,10,10));
        assertFalse("GL must catch up to new visibility",canSubmit(false,true,Display.STATE_ON,Display.STATE_ON,10,10));
        assertFalse("GL must catch up to new display",canSubmit(true,true,Display.STATE_OFF,Display.STATE_ON,10,10));
    }
    @Test public void rapidAodUnlockReversalRejectsOldFramesEvenWhenBothEndsAreVisible(){
        for(int cycle=0;cycle<500;cycle++){
            long aod=cycle*3L,home=aod+1,nextSleep=aod+2;
            assertTrue(canSubmit(true,true,Display.STATE_DOZE,Display.STATE_DOZE,aod,aod));
            assertFalse("old AOD cannot flash over home",canSubmit(true,true,Display.STATE_DOZE,Display.STATE_ON,aod,home));
            assertTrue(canSubmit(true,true,Display.STATE_ON,Display.STATE_ON,home,home));
            assertFalse("old home cannot flash into AOD",canSubmit(true,true,Display.STATE_ON,Display.STATE_DOZE,home,nextSleep));
            assertTrue(canSubmit(true,true,Display.STATE_DOZE_SUSPEND,Display.STATE_DOZE_SUSPEND,nextSleep,nextSleep));
        }
    }
    @Test public void discardedFrameCanBeReplacedWithoutAnotherUserAction(){
        Driver driver=new Driver();long[] sequence={1,1};boolean[] visible={true,true};int[] submitted={0};
        FramePump pump=new FramePump(driver,()->{
            // This is the same final eligibility check used immediately before eglSwapBuffers.
            if(canSubmit(visible[0],visible[1],Display.STATE_ON,Display.STATE_ON,sequence[0],sequence[1]))submitted[0]++;
        });
        for(int cycle=0;cycle<100;cycle++){
            pump.request(0,16);Runnable obsoleteTimer=driver.timer;
            sequence[1]++;visible[1]=false;driver.vsync.run();
            assertEquals(cycle,submitted[0]);
            // Already queued lifecycle work catches up and asks for a new frame.
            sequence[0]=sequence[1];visible[1]=true;pump.request(0,16);
            Runnable latestVsync=driver.vsync;
            obsoleteTimer.run();assertNotNull(driver.timer);
            driver.timer.run();latestVsync.run();assertEquals(cycle+1,submitted[0]);
            assertNull(driver.vsync);assertNull(driver.timer);
        }
    }
    private static boolean canSubmit(boolean visible,boolean requestedVisible,int display,int requestedDisplay,long sequence,long requestedSequence){
        return DisplayFramePolicy.canSubmit(false,visible,requestedVisible,display,requestedDisplay,sequence,requestedSequence);
    }
    private static final class Driver implements FramePump.Driver {
        Runnable vsync,timer;
        public void vsync(Runnable callback,long delay){vsync=callback;}
        public void timer(Runnable callback,long delay){timer=callback;}
        public void cancelVsync(){vsync=null;}
        public void cancelTimer(){timer=null;}
    }
}
