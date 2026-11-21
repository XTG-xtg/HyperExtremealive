package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class InfinityDesktopFollowTest {
    private static final long SECOND=1_000_000_000L;
    private static void frames(InfinityMotion m,long from,long to){
        for(long t=from;t<to;t+=16_666_667)m.advance(t,1);
        m.advance(to,1);
    }
    private static float rotation(InfinityMotion m){
        return m instanceof InfinityGeometryMotion?((InfinityGeometryMotion)m).geometry[InfinityGeometryMotion.ROTATE]+((InfinityGeometryMotion)m).rotationY():m.rotation();
    }
    @Test public void allTwentyThemesFollowDragAndReturnWithoutAMouseUpSpin(){
        Object finger=new Object();
        for(int id=801;id<=820;id++){
            InfinityMotion m=InfinityMotion.create(id,2);m.advance(SECOND,1);
            float before=rotation(m);
            m.touch(finger,0,.2f,.2f,SECOND);m.touch(finger,2,.6f,.5f,SECOND);
            assertEquals(0,m.desktopX(),0);assertTrue(m.active());
            m.advance(SECOND+16_000_000,1);
            assertTrue(m.desktopX()>0&&m.desktopX()<.8f);assertTrue(m.desktopY()>0&&m.desktopY()<.6f);
            frames(m,SECOND+16_000_000,2*SECOND);
            assertEquals(.8f,m.desktopX(),.00001f);assertEquals(.6f,m.desktopY(),.00001f);
            assertTrue("foreground rotates "+id,rotation(m)>before+5);
            assertFalse("holding still needs no idle frames",m.active());
            m.touch(finger,1,.6f,.5f,2*SECOND);
            assertEquals(.8f,m.desktopX(),.00001f);
            frames(m,2*SECOND,4*SECOND);
            assertEquals(0,m.desktopX(),0);assertEquals(0,m.desktopY(),0);
            assertEquals(before,rotation(m),.00001f);assertFalse(m.active());
        }
    }
    @Test public void pageCallbacksAndFingerMotionDoNotAddTheSameSwipeTwice(){
        InfinityDesktopFollow m=new InfinityDesktopFollow(true);
        m.page(0,0,SECOND);m.touch(0,.8f,.5f,SECOND);
        m.touch(2,.55f,.5f,SECOND+50_000_000);m.page(.25f,0,SECOND+50_000_000);
        m.advance(2*SECOND);assertEquals(-.5f,m.x(),.00001f);
        m.touch(1,.55f,.5f,2*SECOND);m.advance(3*SECOND);
        assertEquals(-.5f,m.x(),0);assertFalse(m.active());
        // A repeated offset is not a page swipe: unsupported launchers still get direct drag.
        m.touch(0,.2f,.2f,3*SECOND);m.page(.25f,0,3*SECOND);
        m.touch(2,.4f,.3f,3*SECOND);m.advance(4*SECOND);
        assertEquals(-.1f,m.x(),.00001f);assertEquals(.2f,m.y(),.00001f);
        m.touch(3,.4f,.3f,4*SECOND);m.advance(5*SECOND);
        assertEquals(-.5f,m.x(),0);assertEquals(0,m.y(),0);assertFalse(m.active());
    }
    @Test public void firstInputAfterLongIdleStillInterpolatesAndFrameRatesAgree(){
        InfinityDesktopFollow a=new InfinityDesktopFollow(true),b=new InfinityDesktopFollow(true);
        for(InfinityDesktopFollow m:new InfinityDesktopFollow[]{a,b}){
            m.advance(SECOND);m.page(.4f,-.2f,100*SECOND);m.advance(100*SECOND+16_000_000);
            assertTrue(m.x()<0&&m.x()>-.8f);assertTrue(m.y()>0&&m.y()<.4f);
        }
        for(long t=100*SECOND+16_000_000;t<101*SECOND;t+=33_333_333)a.advance(t);
        for(long t=100*SECOND+16_000_000;t<101*SECOND;t+=8_333_333)b.advance(t);
        a.advance(101*SECOND);b.advance(101*SECOND);
        assertEquals(a.x(),b.x(),.00001f);assertEquals(a.y(),b.y(),.00001f);assertFalse(a.active());assertFalse(b.active());
    }
    @Test public void sleepKeepsTheDisplayedFrameThenCancelsDragAcrossEveryVariant(){
        Object finger=new Object();
        for(int id=801;id<=820;id++){
            InfinityMotion m=InfinityMotion.create(id,2);m.advance(SECOND,1);
            m.touch(finger,0,.2f,.3f,SECOND);m.touch(finger,2,.65f,.6f,SECOND);
            frames(m,SECOND,SECOND+200_000_000);
            float x=m.desktopX(),y=m.desktopY(),angle=rotation(m);
            m.change(0,true);m.advance(SECOND+200_000_000,1);
            assertEquals("sleep must not jump "+id,x,m.desktopX(),0);assertEquals(y,m.desktopY(),0);assertEquals(angle,rotation(m),.00001f);
            frames(m,SECOND+200_000_000,7*SECOND);
            assertEquals(0,m.desktopX(),0);assertEquals(0,m.desktopY(),0);assertFalse(m.active());
            m.change(2,true);m.touch(finger,1,.7f,.6f,7*SECOND);frames(m,7*SECOND,12*SECOND);
            assertEquals(0,m.desktopX(),0);assertEquals(0,m.desktopY(),0);assertFalse(m.active());
            float settled=rotation(m);m.touch(finger,2,.9f,.9f,12*SECOND);m.advance(13*SECOND,1);
            assertEquals(settled,rotation(m),0);assertFalse(m.active());
        }
    }
    @Test public void rapidSceneReversalsAlwaysSettleAndIgnoreOldTouchStreams(){
        Object home=new Object(),lock=new Object();
        for(int id=801;id<=820;id++){
            InfinityMotion m=InfinityMotion.create(id,2);long time=SECOND;m.advance(time,1);
            for(int cycle=0;cycle<40;cycle++){
                m.touch(home,0,.5f,.5f,time);m.touch(home,2,cycle%2==0?.8f:.2f,.6f,time);
                m.advance(time+=48_000_000,1);float x=m.desktopX();
                m.change(0,true);m.advance(time,1);assertEquals(x,m.desktopX(),0);
                m.advance(time+=32_000_000,1);m.change(2,true);m.advance(time,1);
                m.touch(home,1,.1f,.1f,time);m.cancelTouch(lock);
                m.advance(time+=32_000_000,1);
            }
            m.change(0,true);frames(m,time,time+6*SECOND);
            assertEquals(0,m.desktopX(),0);assertEquals(0,m.desktopY(),0);assertFalse("variant "+id,m.active());
        }
    }
    @Test public void invalidAndMultitouchInputsStayBoundedAndRecover(){
        InfinityMotion m=new InfinityMotion(801,2);Object owner=new Object();m.advance(SECOND,1);
        m.follow(Float.NaN,0,SECOND);m.follow(0,Float.POSITIVE_INFINITY,SECOND);assertFalse(m.active());
        m.touch(owner,0,.5f,.5f,SECOND);m.touch(owner,2,Float.MAX_VALUE,-Float.MAX_VALUE,SECOND);
        frames(m,SECOND,2*SECOND);assertEquals(1,m.desktopX(),0);assertEquals(-1,m.desktopY(),0);
        m.touch(owner,5,.6f,.5f,2*SECOND);m.touch(owner,2,.9f,.9f,2*SECOND);
        frames(m,2*SECOND,4*SECOND);assertEquals(0,m.desktopX(),0);assertFalse(m.active());
        m.touch(owner,0,.5f,.5f,4*SECOND);m.touch(owner,2,.7f,.5f,4*SECOND);m.touch(owner,2,Float.NaN,.5f,4*SECOND);
        frames(m,4*SECOND,6*SECOND);assertFalse(m.active());
    }
    @Test public void otherSlotCannotCancelHomeDragAndLockRetainsItsSwipe(){
        InfinityMotion m=InfinityMotion.create(811,2);Object home=new Object(),lock=new Object();m.advance(SECOND,1);
        m.touch(home,0,.2f,.2f,SECOND);m.touch(home,2,.6f,.4f,SECOND);m.cancelTouch(lock);
        frames(m,SECOND,2*SECOND);assertEquals(.8f,m.desktopX(),.00001f);
        m.cancelTouch(home);frames(m,2*SECOND,4*SECOND);assertEquals(0,m.desktopX(),0);
        m.change(1,false);float before=rotation(m);
        m.touch(lock,0,.8f,.5f,4*SECOND);m.touch(lock,1,.2f,.5f,4*SECOND+200_000_000);
        frames(m,4*SECOND+200_000_000,6*SECOND);assertEquals(before+20,rotation(m),.00001f);
        m.change(0,false);m.follow(.5f,.5f,6*SECOND);m.advance(7*SECOND,1);assertEquals(0,m.desktopX(),0);assertFalse(m.active());
    }
    @Test public void shiftedRotatedBackgroundAlwaysCoversScreenCorners(){
        InfinityMotion m=new InfinityMotion(812,2);long time=SECOND;m.advance(time,1);
        // Both axes, diagonal drags, and intermediate amplitudes; identity is the least overscanned renderer.
        for(int xi=-20;xi<=20;xi++)for(int yi=-20;yi<=20;yi++){
            m.follow(-xi/40f,-yi/40f,time);m.advance(time+=2*SECOND,1);
            float[] matrix={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};InfinityParallax.background(matrix,m);
            float determinant=matrix[0]*matrix[5]-matrix[1]*matrix[4];
            for(int cx:new int[]{-1,1})for(int cy:new int[]{-1,1}){
                float x=cx-matrix[12],y=cy-matrix[13];
                float localX=(matrix[5]*x-matrix[4]*y)/determinant;
                float localY=(-matrix[1]*x+matrix[0]*y)/determinant;
                assertTrue("horizontal edge "+xi+","+yi,Math.abs(localX)<=1.000001f);
                assertTrue("vertical edge "+xi+","+yi,Math.abs(localY)<=1.000001f);
            }
        }
    }
}
