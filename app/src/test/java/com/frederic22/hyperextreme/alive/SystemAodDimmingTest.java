package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.ComponentName;
import com.frederic22.hyperextreme.alive.core.ModuleIdentity;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class SystemAodDimmingTest {
    public static class Panel {
        public void doWallpaperBlackAnim(boolean show,float black,int type,boolean animate,Object tracker){}
        public void doWallpaperBlackAnim(boolean show,float black){}
        public void unrelated(boolean show,float black,int type,boolean animate,Object tracker){}
    }
    private List<Object> args(boolean show,float black,int type){return Arrays.asList(show,black,type,true,new Object());}
    private ComponentName own(String service){return new ComponentName(ModuleIdentity.PACKAGE,service);}

    @Test public void higherExposureAlsoLiftsThePostShaderWhiteCeiling(){
        // Even a fully white shader output used to remain at 20% behind an 80% OEM mask.
        assertEquals(.2f,1-AodBrightness.wallpaperBlack(.8f,1),.00001f);
        assertEquals(.6f,1-AodBrightness.wallpaperBlack(.8f,2),.00001f);
        assertEquals(.84f,1-AodBrightness.wallpaperBlack(.8f,5),.00001f);
        assertEquals(.85f,1-AodBrightness.wallpaperBlack(.3f,2),.00001f);
        for(float black:new float[]{.3f,.5f,.57f,.8f}){
            float previous=black;
            for(float gain:new float[]{1,1.3f,2,5,100,1e20f,Float.MAX_VALUE}){
                float next=AodBrightness.wallpaperBlack(black,gain);
                assertTrue(Float.isFinite(next)&&next>=0&&next<=previous);
                previous=next;
            }
        }
    }
    @Test public void onlyFullAodDimmingIsAdjustedAndTheNativeCallIsPreserved()throws Exception{
        Method contract=Panel.class.getMethod("doWallpaperBlackAnim",boolean.class,float.class,int.class,boolean.class,Object.class);
        assertTrue(SystemAodDimming.contract(contract));
        assertFalse(SystemAodDimming.contract(Panel.class.getMethod("doWallpaperBlackAnim",boolean.class,float.class)));
        assertFalse(SystemAodDimming.contract(Panel.class.getMethod("unrelated",boolean.class,float.class,int.class,boolean.class,Object.class)));
        List<Object> original=args(false,.8f,1);
        assertTrue(SystemAodDimming.candidate(original,2));
        Object[] adjusted=SystemAodDimming.arguments(original,2);
        assertEquals(.4f,(Float)adjusted[1],0);
        assertEquals(.8f,(Float)original.get(1),0);
        for(int i:new int[]{0,2,3,4})assertSame(original.get(i),adjusted[i]);
        assertFalse(SystemAodDimming.candidate(args(true,.8f,1),5));
        assertFalse(SystemAodDimming.candidate(args(false,.8f,0),5));
        assertFalse(SystemAodDimming.candidate(args(false,1,1),5));
        assertFalse(SystemAodDimming.candidate(Arrays.asList(false,.8f),5));
        assertFalse(SystemAodDimming.ownsFullAod(new Panel()));
    }
    @Test public void lowGainCustomAodAndInvalidValuesKeepTheSystemTarget(){
        for(float gain:new float[]{.25f,1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){
            assertEquals(.8f,AodBrightness.wallpaperBlack(.8f,gain),0);
            assertFalse(SystemAodDimming.candidate(args(false,.8f,1),gain));
        }
        for(float black:new float[]{0,1,-1,2,Float.POSITIVE_INFINITY}){
            assertEquals(black,AodBrightness.wallpaperBlack(black,Float.MAX_VALUE),0);
            assertFalse(SystemAodDimming.candidate(args(false,black,1),5));
        }
        assertFalse(SystemAodDimming.candidate(args(false,Float.NaN,1),5));
    }
    @Test public void actualLockBindingCoversAllOurServicesButNotAnUnrelatedLock(){
        ComponentName home=own(ModuleIdentity.HOME_SERVICE);
        for(String service:new String[]{ModuleIdentity.HOME_SERVICE,ModuleIdentity.LOCK_SERVICE,
                ModuleIdentity.SPATIAL_SERVICE,ModuleIdentity.RASTER_SERVICE}){
            ComponentName lock=own(service);
            assertTrue(SystemAodDimming.owns(lock));
            assertEquals(lock,SystemAodDimming.lockBinding(home,lock,23));
        }
        assertTrue(SystemAodDimming.owns(SystemAodDimming.lockBinding(home,null,-1)));
        // A static lock with an owned home is not our AOD wallpaper.
        assertFalse(SystemAodDimming.owns(SystemAodDimming.lockBinding(home,null,23)));
        assertFalse(SystemAodDimming.owns(SystemAodDimming.lockBinding(home,null,0)));
        ComponentName other=new ComponentName("other.app",ModuleIdentity.LOCK_SERVICE);
        assertFalse(SystemAodDimming.owns(SystemAodDimming.lockBinding(home,other,23)));
        assertFalse(SystemAodDimming.owns(own("other.Service")));
        assertFalse(SystemAodDimming.owns(null));
    }
    @Test public void hookLayersUseTheSameMaskWithoutChangingTransitionProgress(){
        for(float target:new float[]{.2f,.43f,.5f,.7f}){
            for(float gain:new float[]{.25f,1,2,5,Float.MAX_VALUE}){
                assertEquals(1,AodBrightness.wallpaperDim(1,target,gain),0);
                assertEquals(1-AodBrightness.wallpaperBlack(1-target,gain),
                    AodBrightness.wallpaperDim(target,target,gain),0);
                float mid=(1+target)/2;
                assertEquals(.5f,AodBrightness.dimWeight(mid,target),.00001f);
                assertTrue(AodBrightness.wallpaperDim(mid,target,gain)>=AodBrightness.wallpaperDim(target,target,gain));
            }
        }
        // The entire fade into a custom, black AOD keeps its native values.
        for(float dim:new float[]{0,.25f,.5f,1})assertEquals(dim,AodBrightness.wallpaperDim(dim,0,100),0);
    }
    @Test public void ambientUpdatesCannotFadeExplicitBrightnessButWakeAndTimeoutStillWork(){
        Object panel=new Object();
        assertEquals(.1f,(Float)SystemAodDimming.heldArguments(panel,args(false,.5f,1),5,true)[1],0);
        for(int repeat=0;repeat<60;repeat++)for(float ambient:new float[]{.3f,.8f,.57f})
            assertEquals(.1f,(Float)SystemAodDimming.heldArguments(panel,args(false,ambient,1),5,true)[1],0);
        // The automatic full-black timeout stays native and releases the held entry.
        assertNull(SystemAodDimming.heldArguments(panel,args(false,1,0),5,true));
        assertEquals(.16f,(Float)SystemAodDimming.heldArguments(panel,args(false,.8f,1),5,true)[1],.00001f);
        assertNull(SystemAodDimming.heldArguments(panel,args(true,.8f,1),5,true));
        assertEquals(.06f,(Float)SystemAodDimming.heldArguments(panel,args(false,.3f,1),5,true)[1],.00001f);
        // Zero and unrelated wallpapers restore the system's live target.
        assertNull(SystemAodDimming.heldArguments(panel,args(false,.8f,1),0,true));
        assertNull(SystemAodDimming.heldArguments(panel,args(false,.8f,1),5,false));
        assertEquals(.1f,(Float)SystemAodDimming.heldArguments(new Object(),args(false,.5f,1),5,true)[1],0);
    }
}
