package com.frederic22.hyperextreme.alive;
import org.junit.Test;
import static org.junit.Assert.*;
public class AodPanelRenderingTest {
    @Test public void managedPanelDoesNotMultiplyPixelsAgainAndPreviewStillResponds(){
        for(float level:new float[]{0,.25f,1,2.5f,5}){
            assertEquals(1,AodBrightness.renderGain(level,1,true,false,true),0);
            assertEquals(level==0?1:level,AodBrightness.renderGain(level,1,true,true,true),0);
            assertEquals(level==0?1:level,AodBrightness.renderGain(level,1,true,false,false),0);
            assertTrue(AodBrightness.renderGain(level,1,false,false,false)<=1);
        }
    }
}
