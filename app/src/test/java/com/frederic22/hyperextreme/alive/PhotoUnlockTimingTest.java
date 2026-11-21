package com.frederic22.hyperextreme.alive;

import android.app.Application;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** CPU parameters submitted by PhotoRenderer; no GPU, wallpaper or phone interaction. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class PhotoUnlockTimingTest {
    private static final long START=10_000_000_000L;

    @Test public void directFingerprintUnlockMovesTheLensOnTheFirstFrame(){
        SceneMotion scene=new SceneMotion(1200,2670,0,0,0,6,false);
        try {
            float scale=scene.values[SceneMotion.LENS+1];
            scene.change(2);scene.advance(START);
            assertEquals(scale,scene.values[SceneMotion.LENS+1],.00001f);
            scene.advance(START+16_000_000L);
            assertTrue("first visible frame must already expand",scene.values[SceneMotion.LENS+1]>scale);
            assertTrue("home composition must start with geometry",scene.values[2]>0);
            scene.advance(START+2_000_000_000L);
            assertEquals(1,scene.values[2],.00001f);
            assertFalse(scene.transitionActive());
        }finally{scene.close();}
    }
    @Test public void homeTextureEffectsStartWithTheMainScene(){
        for(int homeStyle:new int[]{1,2,3}){
            for(int lockStyle:new int[]{0,homeStyle}){
                TextureMotion texture=new TextureMotion(1200,2670,0,lockStyle,homeStyle+6);
                try {
                    int slot=homeStyle==3?16:10; // Home ground-glass/texture channel in the real packet.
                    float before=value(texture.packet(),slot);
                    texture.change(2,true);texture.advance(START);texture.advance(START+32_000_000L);
                    assertNotEquals("home texture must not retain a 150ms hold",before,value(texture.packet(),slot),.000001f);
                }finally{texture.close();}
            }
        }
    }
    @Test public void everyFrameStyleStartsItsMaskTimelineImmediately(){
        for(int id=1;id<=5;id++){
            SceneMotion common=new SceneMotion(1200,2670,0,-1,0,6,false);
            FrameMotion frame=new FrameMotion(1200,2670,0,id==1,PhotoStyle.get(id));
            try {
                frame.change(2,common);common.change(2);
                frame.advance(START);common.advance(START);
                frame.advance(START+16_000_000L);common.advance(START+16_000_000L);
                assertTrue("mask should advance for style "+id,frame.values[6]>0);
                assertTrue("composition should advance for style "+id,frame.ratios[2]>0);
                frame.advance(START+2_000_000_000L);common.advance(START+2_000_000_000L);
                assertTrue(frame.expanded());assertFalse(frame.active());
            }finally{frame.close();common.close();}
        }
    }
    @Test public void delayedDesktopVisibilityCatchesUpInsteadOfRestartingExpansion(){
        Lens early=new Lens(),late=new Lens();
        MotionSession first=new MotionSession(early,true),second=new MotionSession(late,true);
        Object owner=new Object();
        try {
            first.frame(0,0,0,START,()->{});second.frame(0,0,0,START,()->{});
            long unlock=START+2_000_000_000L;
            first.frame(1,2,unlock,unlock,()->{});second.frame(1,2,unlock,unlock,()->{});
            first.visible(owner,true);
            for(int ms=16;ms<=240;ms+=16)first.frame(1,2,unlock,unlock+ms*1_000_000L,()->{});
            second.visible(owner,true);second.frame(1,2,unlock,unlock+240_000_000L,()->{});
            for(int slot:new int[]{0,1,2,3,4,5,6,8,9})
                assertEquals("handoff channel "+slot,early.scene.values[slot],late.scene.values[slot],.0001f);
            assertTrue(late.scene.values[SceneMotion.LENS+1]>.22f);
            float scale=late.scene.values[SceneMotion.LENS+1];
            second.frame(2,0,unlock+240_000_000L,unlock+240_000_000L,()->{});
            assertEquals("sleep reversal stays continuous",scale,late.scene.values[SceneMotion.LENS+1],.0001f);
            second.frame(2,0,unlock+240_000_000L,unlock+2_000_000_000L,()->{});
            assertEquals(.22f,late.scene.values[SceneMotion.LENS+1],.0001f);
        }finally{early.scene.close();late.scene.close();}
    }
    private static float value(ByteBuffer packet,int slot){return packet.order(ByteOrder.nativeOrder()).getFloat(slot*4);}
    private static final class Lens implements WallpaperMotion {
        final SceneMotion scene=new SceneMotion(1200,2670,0,0,0,6,false);
        public void change(int next,boolean animate){scene.change(next);if(!animate)scene.finish();}
        public void advance(long nanos,float speed){scene.advance(nanos,speed);}
        public void follow(float x,float y){}
        public void pause(){scene.pause(true);}
        public boolean active(){return scene.active();}
    }
}
