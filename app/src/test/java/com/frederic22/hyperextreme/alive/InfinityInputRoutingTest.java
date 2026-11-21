package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercise the real worker queue without creating a GPU surface or operating a device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class InfinityInputRoutingTest {
    private static final class Fixture implements AutoCloseable {
        final InfinityMotion motion=InfinityMotion.create(812,2);
        final EglWorker worker;
        final HandlerThread thread;
        long time=System.nanoTime();
        Fixture(boolean home)throws Exception {
            worker=new EglWorker(RuntimeEnvironment.getApplication(),false,home,()->{});
            thread=(HandlerThread)get(worker,"thread");shadowOf(thread.getLooper()).pause();
            WallpaperRenderer renderer=new WallpaperRenderer(){
                public WallpaperMotion motion(){return motion;}
                public void render(){fail("No GPU surface should be drawn in a queue test");}
                public void close(){}
            };
            MotionSession session=new MotionSession(motion,true);session.frame(1,2,time,time,()->{});
            set(worker,"renderer",renderer);set(worker,"motionSession",session);
            set(worker,"visible",true);set(worker,"requestedVisible",true);
            set(worker,"displayState",Display.STATE_ON);set(worker,"requestedDisplay",Display.STATE_ON);
            EngineRegistry.Snapshot state=new EngineRegistry.Snapshot(2,2,SystemClock.uptimeMillis(),1);
            set(worker,"state",state);set(worker,"requestedState",state);
        }
        void idle(){shadowOf(thread.getLooper()).idle();assertEquals("",worker.error);}
        void settle(){idle();time=Math.max(time,System.nanoTime())+6_000_000_000L;motion.advance(time,1);}
        void drag(){worker.touch(0,.2f,.2f);worker.touch(2,.6f,.5f);}
        public void close(){worker.close();shadowOf(thread.getLooper()).idle();}
    }
    private static Object get(Object target,String name)throws Exception{Field f=EglWorker.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{Field f=EglWorker.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}

    @Test public void desktopDragDoesNotBecomeAnAbsolutePageOffset()throws Exception {
        try(Fixture f=new Fixture(true)){
            f.worker.launcherOffset(0,0,0,0);f.settle();assertEquals(0,f.motion.desktopX(),0);assertEquals(0,f.motion.desktopY(),0);
            f.drag();f.worker.touchOffset(.1f,0);f.settle();
            assertEquals(.8f,f.motion.desktopX(),.00001f);assertEquals(.6f,f.motion.desktopY(),.00001f);
            f.worker.touch(1,.6f,.5f);f.settle();assertEquals(0,f.motion.desktopX(),0);assertFalse(f.motion.active());
        }
    }
    @Test public void launcherPageWinsAndSinglePageVerticalAxisStaysNeutral()throws Exception {
        try(Fixture f=new Fixture(true)){
            f.drag();f.worker.launcherOffset(.75f,0,.5f,0);f.settle();
            assertEquals(-.5f,f.motion.desktopX(),0);assertEquals(.6f,f.motion.desktopY(),.00001f);
            f.worker.touch(1,.6f,.5f);f.settle();assertEquals(-.5f,f.motion.desktopX(),0);assertEquals(0,f.motion.desktopY(),0);
        }
    }
    @Test public void companionCannotOverwriteTheSharedDesktopMotion()throws Exception {
        try(Fixture f=new Fixture(false)){
            f.drag();f.worker.launcherOffset(1,1,.5f,.5f);f.settle();
            assertEquals(0,f.motion.desktopX(),0);assertEquals(0,f.motion.desktopY(),0);assertFalse(f.motion.active());
        }
    }
    @Test public void oldInputCannotSurviveAHiddenThenVisibleRoundTrip()throws Exception {
        try(Fixture f=new Fixture(true)){
            f.drag();f.worker.visible(false);f.worker.visible(true);f.settle();
            assertEquals(0,f.motion.desktopX(),0);assertFalse(f.motion.active());
            f.drag();f.settle();assertEquals(.8f,f.motion.desktopX(),.00001f);
            f.worker.visible(false);f.settle();assertEquals(0,f.motion.desktopX(),0);
        }
    }
    @Test public void queuedSleepAndUnlockRejectObsoleteTouchButAcceptNewGestures()throws Exception {
        try(Fixture f=new Fixture(true)){
            f.drag();long time=SystemClock.uptimeMillis();
            f.worker.state(new EngineRegistry.Snapshot(2,0,time,2));
            f.worker.state(new EngineRegistry.Snapshot(0,2,time,3));f.settle();
            assertEquals(0,f.motion.desktopX(),0);assertFalse(f.motion.active());
            f.drag();f.settle();assertEquals(.8f,f.motion.desktopX(),.00001f);
        }
    }
    @Test public void aNonInteractiveDisplayCannotAcceptDesktopInput()throws Exception {
        try(Fixture f=new Fixture(true)){
            f.drag();f.worker.display(Display.STATE_DOZE,false);f.settle();
            assertEquals(0,f.motion.desktopX(),0);assertFalse(f.motion.active());
        }
    }
}
