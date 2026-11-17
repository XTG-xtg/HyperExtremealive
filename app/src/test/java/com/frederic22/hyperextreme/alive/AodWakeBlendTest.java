package com.frederic22.hyperextreme.alive;
import org.junit.Test;
import static org.junit.Assert.*;
public class AodWakeBlendTest {
    @Test public void firstWakeUsesRecordedAodWithoutTheTransientPeak(){
        AodWakeBlend b=new AodWakeBlend();b.ambient(.1f);assertTrue(b.begin(100));
        assertEquals(.1f,b.sample(.8f,100,1),0);
        assertEquals(.1f,b.sample(.04f,500,1),0);
        assertEquals(.1f,b.sample(.04f,1499,1),0);
        assertEquals(.1f,b.sample(.04f,1500,1),0);
        float previous=.1f;
        for(long t=1516;t<2500;t+=16){float out=b.sample(.04f,t,1);assertTrue(out<=previous+1e-6);assertTrue(out>=.04f);previous=out;}
        assertEquals(.04f,b.sample(.04f,2600,1),0);
    }
    @Test public void repeatedWakeAndFastReversalUseFreshBaselines(){
        AodWakeBlend b=new AodWakeBlend();
        for(int i=0;i<60;i++){
            b.cancel();float aod=.01f+i*.001f;long now=i*3000L;b.ambient(aod);
            assertTrue(b.begin(now));assertFalse(b.begin(now+20));
            assertEquals(aod,b.sample(.5f,now+30,1),0);
            b.cancel();assertEquals(.02f,b.sample(.02f,now+40,1),0);
        }
    }
    @Test public void blackUnknownCapsAndThermalReductionsTakePrecedence(){
        AodWakeBlend b=new AodWakeBlend();b.ambient(.1f);b.begin(100);
        assertEquals(.03f,b.sample(.2f,120,.03f),0);assertTrue(b.sample(.2f,140,.03f)<=.03f);
        assertEquals(0,b.sample(0,160,1),0);assertFalse(b.active(170));
        b.ambient(.1f);b.begin(200);assertEquals(.2f,b.sample(.2f,220,Float.NaN),0);assertFalse(b.active(240));
    }
    @Test public void targetChangesDuringFadeRemainContinuousAndWindowEnds(){
        AodWakeBlend b=new AodWakeBlend();b.ambient(.02f);b.begin(0);
        b.sample(.1f,0,1);b.sample(.1f,1000,1);float before=b.sample(.1f,1400,1);
        assertEquals(before,b.sample(.08f,1400,1),1e-7);
        assertFalse(b.active(2500));assertEquals(.08f,b.sample(.08f,2500,1),0);
    }
}
