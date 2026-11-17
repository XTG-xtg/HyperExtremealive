package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class AodPanelHoldTest {
    @Test public void subsequentAmbientRequestsAreFollowedRatherThanFrozen(){
        AodPanelHold hold=new AodPanelHold();
        assertEquals(.013708094f*5,hold.brightness(.013708094f,5),1e-7);
        assertTrue(hold.brightness(.002f,5)<hold.brightness(.013f,5));
        assertTrue(hold.brightness(.03f,5)>hold.brightness(.013f,5));
    }
    @Test public void brightnessGainIsNotAppliedTwiceToThePanel(){
        for(float level:new float[]{.25f,1,2.5f,5}){
            AodPanelHold hold=new AodPanelHold();
            assertEquals(.013f*level,hold.brightness(.013f,level),1e-7);
        }
    }
    @Test public void zeroRestoresNativeAndCurvesStayBoundedAndMonotonic(){
        AodPanelHold hold=new AodPanelHold();
        assertEquals(.02f,hold.brightness(.02f,0),0);
        assertEquals(.75f,hold.brightness(.3f,5),1e-6);
        assertEquals(hold.brightness(.013f,5),hold.brightness(.013f,100),0);
        for(float gain=.25f;gain<=5;gain+=.25f){
            float last=0;
            for(int i=1;i<=1000;i++){
                float input=i/1000f,out=hold.brightness(input,gain);
                assertTrue(out>=last-1e-6);assertTrue(out<=1);
                assertTrue(out<=input*Math.max(1,gain)+1e-6);last=out;
            }
        }
    }
    @Test public void unknownEntryFallsBackOnceAndInvalidRequestsRemainNative(){
        AodPanelHold hold=new AodPanelHold();
        for(float invalid:new float[]{0,-1,2,Float.POSITIVE_INFINITY,Float.NaN})
            assertEquals(invalid,hold.brightness(invalid,5),0);
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
