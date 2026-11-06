package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import com.frederic22.hyperextreme.alive.core.AodPlacement;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=Application.class)
public class AliveIntegrationTest {
    @Test public void hiddenOrbTimelinesConsumeSleepAndSettleBeforeNextWake()throws Exception{
        for(int variant:new int[]{1,101,201}){
            WallpaperMotion motion=orb(variant,1),collapsed=orb(variant,0);
            MotionSession session=new MotionSession(motion,true);Object owner=new Object();
            session.visible(owner,true);
            frame(session,0,1,1000,1000);
            frame(session,1,2,1100,1100);frame(session,1,2,1100,1800);
            session.visible(owner,false);
            frame(session,2,0,1900,1900);
            // Both real slots can be hidden for the entire sleep transition.
            frame(session,2,0,1900,10000);
            assertArrayEquals("hidden AOD "+variant,values(collapsed),values(motion),.0001f);
            session.visible(owner,true);
            frame(session,3,1,10100,10100);frame(session,3,1,10100,10200);
            assertTrue(motion.active());
            session.visible(owner,false);
            frame(session,4,0,10201,10201);frame(session,4,0,10201,15000);
            assertArrayEquals("interrupted opening "+variant,values(collapsed),values(motion),.0001f);
        }
    }
    @Test public void sharedHiddenSlotCannotRewindAnotherSlot()throws Exception{
        MotionSession a=MotionSession.acquire("integration-hidden",()->orb(1,1),true);
        MotionSession b=MotionSession.acquire("integration-hidden",()->{throw new AssertionError();},true);
        try{
            frame(a,0,1,1000,1000);frame(a,1,0,1100,1100);frame(b,1,0,1100,9000);
            frame(a,0,1,1000,1500);
            assertArrayEquals(values(orb(1,0)),values(a.motion),.0001f);
        }finally{MotionSession.release("integration-hidden",a,this);MotionSession.release("integration-hidden",b,this);}
    }
    @Test public void placementKeepsCoordinateSignsAndRejectsRotation(){
        AodPlacement a=new AodPlacement(1080,2400,400,180,680,420);
        AodPlacement moved=new AodPlacement(1080,2400,412,196,692,436);
        assertEquals(12/1080f,moved.x()-a.x(),.00001f);
        assertEquals(-16/2400f,moved.y()-a.y(),.00001f);
        assertTrue(a.fits(270,600));assertFalse(a.fits(600,270));
        for(long i=0;i<1000;i++){
            assertTrue(Math.abs(AodPlacement.driftX(i*60000))<=.00601f);
            assertTrue(Math.abs(AodPlacement.driftY(i*60000))<=.00401f);
        }
        assertNotEquals(AodPlacement.driftX(0),AodPlacement.driftX(60000));
    }
    @Test public void malformedClockAndBrightnessAreBounded(){
        assertThrows(IllegalArgumentException.class,()->new AodPlacement(0,2400,1,2,3,4));
        assertThrows(IllegalArgumentException.class,()->new AodPlacement(1080,2400,-1,2,3,4));
        assertThrows(IllegalArgumentException.class,()->new AodPlacement(1080,2400,1,2,1081,4));
        Bundle b=new Bundle();b.putInt("width",1080);b.putInt("height",2400);b.putInt("right",200);b.putInt("bottom",200);
        b.putLong("at",SystemClock.uptimeMillis()-6000);
        assertThrows(IllegalArgumentException.class,()->new ClockSnapshot(b));
        assertEquals(1.3f,new AliveRenderOptions(false,true,true,true,true,true,0,Float.NaN).brightness,0);
        assertEquals(3f,new AliveRenderOptions(false,true,true,true,true,true,100,100).brightness,0);
        assertEquals(.25f,new AliveRenderOptions(false,true,true,true,true,true,0,-1).brightness,0);
        assertEquals(50,new AliveRenderOptions(false,true,true,true,true,true,100,1).sensitivity);
    }
    @Test public void everyPhotoStyleHasFullMaskEndpoints()throws Exception{
        android.content.res.AssetManager assets=RuntimeEnvironment.getApplication().getAssets();
        for(int id=1;id<=5;id++){
            PhotoStyle style=PhotoStyle.get(id);
            for(String endpoint:new String[]{"start","end"})try(java.io.InputStream in=assets.open("masks/"+style.folder+"/"+endpoint+".rgbz")){assertTrue(in.read()!=-1);}
            assertEquals(style.frames,style.index(0));assertEquals(style.frames+1,style.index(style.endMicros));
        }
        assertEquals(6,AliveCatalog.load(RuntimeEnvironment.getApplication()).stream().filter(s->s.group==5).count());
    }
    @Test public void failedApplicationRestoresPhotoAndOptionsButPreservesDraft()throws Exception{
        android.content.Context c=RuntimeEnvironment.getApplication();
        java.io.File active=new java.io.File(c.getFilesDir(),"alive/photo.png");active.getParentFile().mkdirs();
        java.nio.file.Files.write(active.toPath(),new byte[]{1,2,3});
        java.nio.file.Files.write(AliveSettings.draft(c).toPath(),new byte[]{9,8});
        com.frederic22.hyperextreme.alive.Config.prefs(c).edit().clear().putInt("variant",1).putBoolean("enabled",false).commit();
        try(AliveApplyBackup ignored=new AliveApplyBackup(c)){
            java.nio.file.Files.write(active.toPath(),new byte[]{4,5});
            com.frederic22.hyperextreme.alive.Config.prefs(c).edit().putInt("variant",606).putFloat("aod_brightness",2).commit();
        }
        assertArrayEquals(new byte[]{1,2,3},java.nio.file.Files.readAllBytes(active.toPath()));
        assertArrayEquals(new byte[]{9,8},java.nio.file.Files.readAllBytes(AliveSettings.draft(c).toPath()));
        assertEquals(1,com.frederic22.hyperextreme.alive.Config.variant(c));
        assertFalse(com.frederic22.hyperextreme.alive.Config.prefs(c).contains("aod_brightness"));
    }
    private WallpaperMotion orb(int variant,int mode)throws java.io.IOException{
        return variant<201?new CosmicMotion(RuntimeEnvironment.getApplication().getAssets(),variant,false,mode)
            :new BubbleMotion(RuntimeEnvironment.getApplication().getAssets(),variant,false,mode,false);
    }
    private float[] values(WallpaperMotion motion){return motion instanceof CosmicMotion?((CosmicMotion)motion).values:((BubbleMotion)motion).values;}
    private void frame(MotionSession s,long sequence,int mode,long at,long now){s.frame(sequence,mode,at*1000000,now*1000000,()->{});}
}
