package com.frederic22.hyperextreme.alive;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class AodSessionBrightnessTest {
    private void enter(AodSessionBrightness state,Object owner,float level){
        state.wallpaper(owner,Arrays.asList(false,.5f,1,true,null),level,true);
    }
    @Test public void ambientLightCannotChangeTheInitialDozeBrightness(){
        AodSessionBrightness state=new AodSessionBrightness();Object owner=new Object();
        enter(state,owner,5);
        assertEquals(.0135f,state.panel(.0135f,5),0);
        for(int repeat=0;repeat<60;repeat++)for(float requested:new float[]{.002f,.03f,.005f,.02f}){
            enter(state,owner,5); // OEM wallpaper recomputation must not restart the session.
            assertEquals(.0135f,state.panel(requested,5),0);
            assertEquals(0,state.scrim(.6f,5),0);
        }
    }
    @Test public void wakingTimeoutAndOtherWallpapersReleaseTheOverride(){
        AodSessionBrightness state=new AodSessionBrightness();Object owner=new Object();
        enter(state,owner,5);state.panel(.0135f,5);
        state.wallpaper(owner,Arrays.asList(true,.5f,1,true,null),5,true);
        assertEquals(.04f,state.panel(.04f,5),0);assertEquals(.6f,state.scrim(.6f,5),0);
        enter(state,owner,5);state.panel(.01f,5);state.clear();
        assertEquals(.03f,state.panel(.03f,5),0);
        enter(state,owner,5);state.panel(.01f,5);
        state.wallpaper(owner,Arrays.asList(false,1f,0,true,null),5,true);
        assertEquals(.03f,state.panel(.03f,5),0);
        enter(state,owner,5);state.panel(.01f,5);
        state.wallpaper(owner,Arrays.asList(false,.5f,1,true,null),5,false);
        assertEquals(.03f,state.panel(.03f,5),0);
    }
    @Test public void zeroRestoresSystemValuesAndNextSessionCanChooseNewBrightness(){
        AodSessionBrightness state=new AodSessionBrightness();Object owner=new Object();
        enter(state,owner,5);state.panel(.01f,5);
        assertEquals(.03f,state.panel(.03f,0),0);assertEquals(.8f,state.scrim(.8f,0),0);
        enter(state,owner,5);assertEquals(.02f,state.panel(.02f,5),0);
        Object replacement=new Object();enter(state,replacement,5);
        assertEquals(.025f,state.panel(.025f,5),0);
    }
    @Test public void invalidBrightnessAndFullBlackRemainNative(){
        AodSessionBrightness state=new AodSessionBrightness();Object owner=new Object();enter(state,owner,.25f);
        for(float value:new float[]{-1,0,2,Float.POSITIVE_INFINITY})assertEquals(value,state.panel(value,.25f),0);
        assertTrue(Float.isNaN(state.panel(Float.NaN,.25f)));
        assertEquals(.0135f,state.panel(.0135f,.25f),0);
        assertEquals(1,state.scrim(1,.25f),0);assertEquals(0,state.scrim(0,.25f),0);
    }
}
