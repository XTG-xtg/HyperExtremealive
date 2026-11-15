package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class PhotoAnimationStressTest {
    private static final class Photo implements WallpaperMotion,AutoCloseable {
        final SceneMotion scene;
        final TextureMotion texture;
        final FrameMotion frame;
        Photo(int style){
            scene=new SceneMotion(1200,2670,0,style==0?0:-1,0,6,false);
            texture=new TextureMotion(1200,2670,0,0,6);
            frame=style==0?null:new FrameMotion(1200,2670,0,style==1,PhotoStyle.get(style));
        }
        public void change(int next,boolean animate){
            texture.change(next,animate);
            if(frame!=null){frame.change(next,scene);if(!animate)frame.finish();}
            scene.change(next);if(!animate)scene.finish();
        }
        public void advance(long now,float speed){texture.advance(now);if(frame!=null)frame.advance(now);scene.advance(now,speed);}
        public void follow(float x,float y){}
        public void pause(){scene.pause(true);}
        public boolean active(){return scene.active()||texture.active()||frame!=null&&frame.active();}
        public void close(){scene.close();texture.close();if(frame!=null)frame.close();}
    }
    @Test public void repeatedInterruptionsWithRealLooperPulsesAlwaysFinishCollapse(){
        for(int style=0;style<=5;style++)try(Photo photo=new Photo(style)){
            MotionSession session=new MotionSession(photo,true);
            Object owner=new Object();session.visible(owner,true);
            long sequence=0,event=now();session.frame(sequence,0,event,event,()->{});
            for(int cycle=0;cycle<40;cycle++){
                event=now();session.frame(++sequence,cycle%3==0?1:2,event,event,()->{});
                int duration=new int[]{16,48,120,240,450,720}[cycle%6];
                for(int ms=0;ms<duration;ms+=16){idle(16);session.frame(sequence,cycle%3==0?1:2,event,now(),()->{});}
                event=now();session.frame(++sequence,0,event,event,()->{});
                session.visible(owner,false);idle(24);session.visible(owner,true);
                for(int ms=0;ms<1400;ms+=16){idle(16);session.frame(sequence,0,event,now(),()->{});}
                assertEquals("AOD weight style="+style+" cycle="+cycle,1,photo.scene.values[0],.0001f);
                assertFalse("transition must finish",photo.scene.transitionActive());
                if(style==0){
                    assertEquals(.22f,photo.scene.values[SceneMotion.LENS+1],.0001f);
                    assertEquals(9,photo.scene.values[SceneMotion.LENS+2],.0001f);
                    assertEquals(1,photo.scene.values[SceneMotion.LENS+7],.0001f);
                }else{
                    assertEquals(1,photo.frame.ratios[0],.0001f);
                    assertEquals(photo.frame.style.scale,photo.frame.values[2],.0001f);
                    assertEquals(0,photo.frame.values[6],.0001f);
                }
            }
        }
    }
    private static long now(){return SystemClock.uptimeMillis()*1_000_000L;}
    private static void idle(long millis){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));}
}
