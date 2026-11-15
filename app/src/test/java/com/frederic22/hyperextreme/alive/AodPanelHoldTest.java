package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class AodPanelHoldTest {
    @Test public void holdsEntryInsteadOfAlreadyDimmedSensorBrightness(){
        AodPanelHold hold=new AodPanelHold();
        assertEquals(.104163945f,hold.brightness(.013708094f,.104163945f,5,true),0);
        for(int cycle=0;cycle<60;cycle++)for(float sensor:new float[]{.002f,.013f,.03f,.006f})
            assertEquals(.104163945f,hold.brightness(sensor,.229f,5,true),0);
    }
    @Test public void brightnessGainIsNotAppliedTwiceToThePanel(){
        for(float level:new float[]{.25f,1,2.5f,5,10}){
            AodPanelHold hold=new AodPanelHold();
            assertEquals(.1f,hold.brightness(.013f,.1f,level,true),0);
        }
    }
    @Test public void zeroAndWakeRestoreNativeAndReleaseThePreviousEntry(){
        AodPanelHold hold=new AodPanelHold();
        hold.brightness(.013f,.1f,5,true);
        assertEquals(.02f,hold.brightness(.02f,.1f,0,true),0);
        assertEquals(.2f,hold.brightness(.01f,.2f,5,true),0);
        assertEquals(.03f,hold.brightness(.03f,.2f,5,false),0);
        assertEquals(.15f,hold.brightness(.01f,.15f,5,true),0);
        hold.clear(); // Automatic OFF ends the session even without a wake broadcast.
        assertEquals(.08f,hold.brightness(.01f,.08f,5,true),0);
    }
    @Test public void unknownEntryFallsBackOnceAndInvalidRequestsRemainNative(){
        AodPanelHold hold=new AodPanelHold();
        for(float invalid:new float[]{0,-1,2,Float.POSITIVE_INFINITY,Float.NaN})
            assertEquals(invalid,hold.brightness(invalid,.1f,5,true),0);
        assertEquals(.013f,hold.brightness(.013f,Float.NaN,5,true),0);
        assertEquals(.013f,hold.brightness(.03f,.1f,5,true),0);
    }
    @Test public void scopeIncludesAnimatedAodButExcludesWakeOffOtherDisplaysAndWallpapers(){
        for(int state:new int[]{2,3,4})assertTrue(AodPanelHold.eligible(true,true,false,0,1,state));
        assertFalse(AodPanelHold.eligible(false,true,false,0,1,2));
        assertFalse(AodPanelHold.eligible(true,false,false,0,1,2));
        assertFalse(AodPanelHold.eligible(true,true,true,0,1,3));
        assertFalse(AodPanelHold.eligible(true,true,false,1,1,2));
        for(int policy:new int[]{0,2,3})assertFalse(AodPanelHold.eligible(true,true,false,0,policy,2));
        for(int state:new int[]{0,1,5,6})assertFalse(AodPanelHold.eligible(true,true,false,0,1,state));
    }
}
