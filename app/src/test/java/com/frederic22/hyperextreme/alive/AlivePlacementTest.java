package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import com.frederic22.hyperextreme.alive.core.AodPlacement;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35,application=Application.class)
public class AlivePlacementTest {
    private Context context(){return RuntimeEnvironment.getApplication();}
    @Test public void photoPositionRoundTripsBothHalvesWithoutChangingCrop(){
        for(float y:new float[]{-.45f,-.2f,0,.2f,.45f}){
            PhotoOptions original=PhotoOptions.defaults().edit(1,.7f).edit(8,-.3f).edit(9,y);
            original.save(context(),true);
            for(PhotoOptions copy:new PhotoOptions[]{PhotoOptions.load(context(),true),PhotoOptions.fromBundle(original.bundle())}){
                assertEquals(y,copy.aodOffsetY,0);assertEquals(-.3f,copy.aodOffsetX,0);assertEquals(.7f,copy.x,0);
            }
        }
    }
    @Test public void renderDraftPersistsManualModeButDoesNotPublish(){
        AliveRenderOptions active=new AliveRenderOptions(true,true,false,true,true,false,17,1.8f);
        active.write(Config.prefs(context()).edit()).commit();
        assertTrue(AliveRenderOptions.load(context(),true).followClock);
        active.position(.3f,-.3f).saveDraft(context());
        AliveRenderOptions draft=AliveRenderOptions.load(context(),true);
        assertFalse(draft.followClock);assertTrue(draft.burnIn);assertEquals(1.8f,draft.brightness,0);
        assertEquals(.3f,draft.aodOffsetX,0);assertEquals(-.3f,draft.aodOffsetY,0);
        assertTrue(AliveRenderOptions.load(context()).followClock);assertEquals(0,AliveRenderOptions.load(context()).aodOffsetY,0);
        draft.write(Config.prefs(context()).edit()).commit();
        assertEquals(-.3f,AliveRenderOptions.load(context()).aodOffsetY,0);
    }
    @Test public void badOffsetsAreSanitized(){
        AliveRenderOptions options=AliveRenderOptions.load(context()).position(Float.NaN,Float.POSITIVE_INFINITY);
        assertEquals(0,options.aodOffsetX,0);assertEquals(0,options.aodOffsetY,0);
        assertEquals(-.45f,options.position(-10,10).aodOffsetX,0);assertEquals(.45f,options.position(-10,10).aodOffsetY,0);
    }
    @Test public void whitespaceClockHostsAreNotContent(){
        assertFalse(new AodPlacement(1080,2400,0,0,1080,1100).isClockContent());
        assertFalse(new AodPlacement(1080,2400,200,100,700,1300).isClockContent());
        assertTrue(new AodPlacement(1080,2400,200,100,700,500).isClockContent());
    }
    @Test public void clockSnapshotsExpireAndNeverCrossDisplayOrientation(){
        android.os.Bundle b=new android.os.Bundle();
        b.putInt("width",1080);b.putInt("height",2400);b.putInt("left",200);b.putInt("top",100);b.putInt("right",700);b.putInt("bottom",500);
        b.putLong("at",android.os.SystemClock.uptimeMillis());b.putBoolean("aod",true);
        ClockSnapshot snapshot=new ClockSnapshot(b);
        assertTrue(snapshot.usable(270,600));assertFalse(snapshot.usable(600,270));
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(3));
        assertFalse(snapshot.usable(270,600));
    }
    @Test public void defaultPhotoIsSharedByAllSlotsAndFrozenOnlyOnApply()throws Exception{
        Context c=context();PhotoOptions options=PhotoOptions.defaults();
        File file=PhotoMedia.resolve(c,options,true,0);
        assertFalse(file.exists());assertTrue(PhotoMedia.available(c,options,true,0));
        File prepared=PhotoMedia.prepare(c,options,true,0);assertEquals(file,prepared);
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getPath());
        assertNotNull(bitmap);assertTrue(bitmap.getWidth()>0);bitmap.recycle();
        for(int slot=0;slot<3;slot++)assertEquals(file,PhotoMedia.prepare(c,options,true,slot));
        assertEquals(file,PhotoMedia.resolve(c,options,false,0));
        assertEquals("",PhotoOptions.load(c,false).lockPhoto);
        PhotoOptions frozen=PhotoMedia.freezeLegacy(c,options,true);
        assertArrayEquals(Files.readAllBytes(file.toPath()),Files.readAllBytes(PhotoMedia.resolve(c,frozen,false,0).toPath()));
    }
    @Test public void unavailableCustomPhotoNeverSilentlyBecomesDefault()throws Exception{
        Context c=context();PhotoOptions options=PhotoOptions.defaults().media(0,"photo-00000000000000000000000000000001.png");
        assertFalse(PhotoMedia.available(c,options,true,0));
        assertFalse(PhotoMedia.prepare(c,options,true,0).exists());
        assertThrows(java.io.IOException.class,()->PhotoMedia.validate(c,options,true,601));
    }
}
