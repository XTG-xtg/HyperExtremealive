package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class InfinityMotionTest {
    private static void frames(InfinityMotion m,long start,long end,int fps){
        for(long t=start;t<end;t+=1_000_000_000L/fps)m.advance(t,1);
        m.advance(end,1);
    }
    @Test public void homeSleepRevealsStarsBeforeDesktopDisappears(){
        for(int variant=801;variant<=820;variant++){
            InfinityMotion m=InfinityMotion.create(variant,2);
            m.advance(1_000_000_000L,1);float before=m.starAlpha(),angle=m.rotation();
            m.change(0,true);m.advance(1_000_000_000L,1);
            assertEquals(before,m.starAlpha(),0);assertEquals(angle,m.rotation(),0);
            frames(m,1_000_000_000L,1_550_000_000L,60);
            assertTrue("Stars must stand out during sleep: "+variant,m.starAlpha()>1);
            assertTrue("Keep desktop visible during the star reveal",m.values[InfinityMotion.HOME]>.3f);
            assertEquals(1,m.values[InfinityMotion.HOME]+m.values[InfinityMotion.AOD],.00001f);
            assertTrue(Math.abs(m.rotation()-angle)>10);
            frames(m,1_550_000_000L,6_000_000_000L,60);
            assertEquals(1.28f,m.starAlpha(),.00001f);assertEquals(0,m.starEmphasis(),0);
            assertEquals(0,m.values[InfinityMotion.HOME],0);assertFalse(m.active());
        }
    }
    @Test public void sleepStarRevealSurvivesSlotHandoffAndReversesWithoutAFlash(){
        for(int variant=801;variant<=820;variant++){
            InfinityMotion motion=InfinityMotion.create(variant,2);
            MotionSession session=new MotionSession(motion,true);
            Object home=new Object(),lock=new Object();long start=1_000_000_000L;
            session.visible(home,true);session.frame(0,2,0,start,()->{});
            session.frame(1,0,start,start,()->{});
            session.frame(1,0,start,start+550_000_000L,()->{});
            float alpha=motion.starAlpha(),emphasis=motion.starEmphasis(),angle=motion.rotation();
            session.visible(lock,true);session.visible(home,false);
            session.frame(1,0,start,start+550_000_000L,()->{});
            assertEquals(alpha,motion.starAlpha(),0);assertEquals(angle,motion.rotation(),0);
            session.frame(2,2,start+550_000_000L,start+550_000_000L,()->{});
            assertEquals(alpha,motion.starAlpha(),0);assertEquals(emphasis,motion.starEmphasis(),0);
            session.frame(2,2,start+550_000_000L,start+800_000_000L,()->{});
            assertTrue(motion.starEmphasis()<emphasis);
            session.frame(3,0,start+800_000_000L,start+800_000_000L,()->{});
            session.visible(lock,false);
            session.frame(3,0,start+800_000_000L,start+15_000_000_000L,()->{});
            assertEquals(1.28f,motion.starAlpha(),.00001f);assertFalse(motion.active());
        }
    }
    @Test public void noAnimationAndLockSleepDoNotPulseTheStars(){
        for(int variant=801;variant<=820;variant++){
            InfinityMotion m=InfinityMotion.create(variant,2);
            m.advance(1_000_000_000L,1);m.change(0,false);
            assertEquals(1.28f,m.starAlpha(),.00001f);assertEquals(0,m.starEmphasis(),0);
            m.change(1,false);m.change(0,true);frames(m,1_000_000_000L,1_550_000_000L,60);
            assertTrue(m.starAlpha()>=.8f);assertEquals(0,m.starEmphasis(),0);
        }
    }
    @Test public void onlyAodGetsTheSustainedStarBrightnessBoost(){
        for(int variant=801;variant<=820;variant++){
            InfinityMotion m=InfinityMotion.create(variant,0);
            assertEquals(1.6f,m.aodStarGain(),0);assertEquals(1.28f,m.starAlpha(),.00001f);
            m.change(1,false);assertEquals(1,m.aodStarGain(),0);assertEquals(.8f,m.starAlpha(),0);
            m.change(2,false);assertEquals(1,m.aodStarGain(),0);assertEquals(.5f,m.starAlpha(),0);
        }
    }
    @Test public void everyThemeKeepsVisibleAodAndSettlesWithoutIdleFrames(){
        for(int variant=801;variant<=806;variant++){
            InfinityMotion m=new InfinityMotion(variant,2);m.advance(1_000_000_000L,1);m.change(0,true);
            frames(m,1_000_000_000L,4_000_000_000L,60);
            assertEquals(1,m.values[InfinityMotion.AOD],0);assertTrue(m.values[InfinityMotion.STAR]>.5f);
            assertEquals(0,m.values[InfinityMotion.LOCK],0);assertFalse(m.active());
            m.change(1,true);frames(m,4_000_000_000L,7_000_000_000L,60);
            assertEquals(1,m.values[InfinityMotion.LOCK],0);assertEquals(0,m.values[InfinityMotion.EXIT],0);
            for(float r:m.reveal)assertEquals(1,r,0);assertFalse(m.active());
            m.change(2,true);frames(m,7_000_000_000L,10_000_000_000L,60);
            assertEquals(1,m.values[InfinityMotion.HOME],0);assertEquals(0,m.values[InfinityMotion.PLUS],0);assertFalse(m.active());
        }
    }
    @Test public void reversingMidTransitionStartsFromTheEvaluatedFrame(){
        InfinityMotion m=new InfinityMotion(801,2);m.advance(1_000_000_000L,1);m.change(0,true);
        frames(m,1_000_000_000L,1_270_000_000L,60);float[] before=m.values.clone();float angle=m.rotation();
        m.change(1,true);m.advance(1_270_000_000L,1);
        assertArrayEquals(before,m.values,0);assertEquals(angle,m.rotation(),0);
        frames(m,1_270_000_000L,4_270_000_000L,60);assertEquals(1,m.values[InfinityMotion.LOCK],0);assertFalse(m.active());
    }
    @Test public void hiddenDisplayGapCompletesInsteadOfReplayingAnOldTransition(){
        InfinityMotion m=new InfinityMotion(804,2);m.advance(1_000_000_000L,1);m.change(0,true);
        m.advance(15_000_000_000L,1);assertFalse(m.active());assertEquals(1,m.values[InfinityMotion.AOD],0);
        m.change(1,true);m.advance(15_000_000_000L,1);assertEquals(1,m.values[InfinityMotion.AOD],0);
        frames(m,15_000_000_000L,18_000_000_000L,60);assertEquals(1,m.values[InfinityMotion.LOCK],0);
    }
    @Test public void epochReconstructionAndFrameRatesReachIdenticalEndpoints(){
        InfinityMotion a=new InfinityMotion(806,1),b=new InfinityMotion(806,1);
        for(InfinityMotion m:new InfinityMotion[]{a,b}){m.advance(1_000_000_000L,1);m.change(2,true);}
        frames(a,1_000_000_000L,4_000_000_000L,30);frames(b,1_000_000_000L,4_000_000_000L,120);
        assertArrayEquals(a.values,b.values,0);assertArrayEquals(a.reveal,b.reveal,0);assertEquals(a.rotation(),b.rotation(),0);
    }
    @Test public void gyroRejectsHiddenGapsDuplicateSamplesAndAod(){
        InfinityMotion m=new InfinityMotion(801,1);m.advance(1_000_000_000L,1);
        m.gyroscope(0,1,1_000_000_000L,false,0);m.gyroscope(0,1,1_100_000_000L,false,0);
        frames(m,1_000_000_000L,4_000_000_000L,60);float angle=m.rotation();assertTrue(angle>1);
        m.gyroscope(0,1,1_100_000_000L,false,0);m.gyroscope(0,1,20_000_000_000L,false,0);
        m.advance(5_000_000_000L,1);assertEquals(angle,m.rotation(),.005f);
        m.change(0,false);m.gyroscope(0,10,20_100_000_000L,false,50);m.advance(6_000_000_000L,1);assertEquals(angle,m.rotation(),.005f);
    }
    @Test public void malformedSignalsNeverPoisonGeometry(){
        InfinityMotion m=new InfinityMotion(802,1);m.advance(1_000_000_000L,1);
        m.follow(Float.NaN,0);m.gyroscope(Float.NaN,Float.POSITIVE_INFINITY,2_000_000_000L,false,0);m.advance(2_000_000_000L,1);
        assertTrue(Float.isFinite(m.rotation()));for(float x:m.values)assertTrue(Float.isFinite(x));
    }
    @Test public void seededParticlesCoverTheWholeRingAndAgreeBetweenSlots(){
        float[] first=InfinityRenderer.particles();assertEquals(14004*9,first.length);assertArrayEquals(first,InfinityRenderer.particles(),0);
        float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
        for(int i=0;i<4000;i++){min=Math.min(min,first[i*9]);max=Math.max(max,first[i*9]);}
        assertTrue("Float division must distribute particles around the full ring",min<-1&&max>1);
    }
}
