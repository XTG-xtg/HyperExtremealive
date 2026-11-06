package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import com.frederic22.hyperextreme.alive.core.PhotoGeometry;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35,application=Application.class)
public class PhotoMediaTest {
    private static final String LOCK="photo-00000000000000000000000000000001.png",HOME="photo-00000000000000000000000000000002.png",FRAME="photo-00000000000000000000000000000003.png";
    private Context context(){return RuntimeEnvironment.getApplication();}
    @Test public void allEditorChangesKeepIndependentMediaAndUnrelatedCrops(){
        PhotoOptions o=PhotoOptions.defaults().media(0,LOCK).media(1,HOME).media(2,FRAME).edit(11,.8f).edit(13,3).color(0xffeeddcc);
        for(int key=0;key<=15;key++){
            PhotoOptions n=o.edit(key,1);
            assertEquals(LOCK,n.lockPhoto);assertEquals(HOME,n.homePhoto);assertEquals(FRAME,n.framePhoto);
            if(key!=11)assertEquals(.8f,n.homeX,0);
            if(key!=13)assertEquals(3,n.homeZoom,0);
            assertEquals(0xffeeddcc,n.tint);
        }
    }
    @Test public void bundleAndPreferencesPreserveEverySetting(){
        PhotoOptions o=PhotoOptions.defaults().media(0,LOCK).media(1,HOME).media(2,FRAME).edit(0,5).edit(10,3).edit(11,.2f).edit(12,.3f).edit(13,2).edit(15,1).color(0xffaaccee);
        o.save(context(),true);PhotoOptions loaded=PhotoOptions.load(context(),true);
        PhotoOptions restored=PhotoOptions.fromBundle(o.bundle());
        for(PhotoOptions n:new PhotoOptions[]{loaded,restored}){
            assertEquals(LOCK,n.lockPhoto);assertEquals(HOME,n.homePhoto);assertEquals(FRAME,n.framePhoto);assertFalse(n.followLock);
            assertEquals(.2f,n.homeX,0);assertEquals(.3f,n.homeY,0);assertEquals(2,n.homeZoom,0);
            assertEquals(5,n.lockStyle);assertEquals(3,n.homeStyle);assertEquals(101,n.aod(601));assertEquals(0xffaaccee,n.tint);
        }
    }
    @Test public void imageNamesCannotEscapePrivateStore(){
        for(String name:new String[]{"../secret.png","/sdcard/photo.png","photo-../bad.png","photo-FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF.png",""}){
            assertEquals("",PhotoOptions.mediaName(name));
            assertThrows(IllegalArgumentException.class,()->PhotoOptions.defaults().media(0,name));
        }
        assertEquals(LOCK,PhotoOptions.mediaName(LOCK));
    }
    @Test public void followingUsesSamePhotoButDoesNotForgetIndependentSelection(){
        PhotoOptions o=PhotoOptions.defaults().media(0,LOCK).media(1,HOME).media(2,FRAME);
        assertEquals(HOME,PhotoMedia.resolve(context(),o,true,1).getName());
        assertEquals(LOCK,PhotoMedia.resolve(context(),o.edit(14,1),true,1).getName());
        assertEquals(HOME,o.edit(14,1).homePhoto);
        assertEquals(FRAME,PhotoMedia.resolve(context(),o,true,2).getName());
    }
    @Test public void firstHomeCropEditStartsFromVisibleLockImageAndCrop(){
        PhotoOptions o=PhotoOptions.defaults().media(0,LOCK).media(1,HOME).edit(1,.65f).edit(2,.35f).edit(3,2).edit(14,1);
        assertSame(o,o.editableCrop(0));assertSame(o,o.editableCrop(2));
        PhotoOptions n=o.editableCrop(1).edit(13,3);
        assertFalse(n.followLock);assertEquals(LOCK,n.homePhoto);
        assertEquals(.65f,n.homeX,0);assertEquals(.35f,n.homeY,0);assertEquals(3,n.homeZoom,0);
        assertEquals(HOME,o.homePhoto);assertTrue(o.followLock);
        assertSame(n,n.editableCrop(1));
    }
    @Test public void followToggleRoundTripPreservesHomeCropTextureAndAppliedState(){
        PhotoOptions independent=PhotoOptions.defaults().media(0,LOCK).media(1,HOME).media(2,FRAME)
            .edit(0,2).edit(10,3).edit(1,.65f).edit(3,2).edit(11,.3f).edit(12,.7f).edit(13,3);
        independent.save(context(),false);
        PhotoOptions following=independent.edit(14,1);
        following.save(context(),true);
        PhotoOptions restored=PhotoOptions.fromBundle(PhotoOptions.load(context(),true).bundle());
        assertTrue(restored.followLock);
        assertEquals(LOCK,PhotoMedia.resolve(context(),restored,true,1).getName());
        PhotoOptions unlocked=restored.edit(14,0);
        assertEquals(HOME,PhotoMedia.resolve(context(),unlocked,true,1).getName());
        assertEquals(.3f,unlocked.homeX,0);assertEquals(.7f,unlocked.homeY,0);assertEquals(3,unlocked.homeZoom,0);
        assertEquals(.65f,unlocked.x,0);assertEquals(2,unlocked.zoom,0);
        assertEquals(2,unlocked.lockStyle);assertEquals(3,unlocked.homeStyle);
        assertEquals(FRAME,unlocked.framePhoto);
        assertFalse(restored.media(1,FRAME).followLock);
        assertTrue(restored.followLock);
        assertFalse(PhotoOptions.load(context(),false).followLock);
        assertEquals(HOME,PhotoOptions.load(context(),false).homePhoto);
    }
    @Test public void legacyPublishCopiesOnceWithoutResettingCropOrDraft()throws Exception {
        File draft=AliveSettings.draft(context());draft.getParentFile().mkdirs();Files.write(draft.toPath(),new byte[]{1,2,3});
        PhotoOptions o=PhotoOptions.defaults().edit(1,.7f).edit(3,2).edit(10,3);
        PhotoOptions frozen=PhotoMedia.freezeLegacy(context(),o,true);
        assertEquals(.7f,frozen.x,0);assertEquals(2,frozen.zoom,0);assertEquals(3,frozen.homeStyle);
        Files.write(draft.toPath(),new byte[]{9});
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(PhotoMedia.resolve(context(),frozen,false,0).toPath()));
        assertSame(frozen,PhotoMedia.freezeLegacy(context(),frozen,true));
    }
    @Test public void killedApplyRecoversDurablePreferencesAndRetainsDraft()throws Exception {
        Config.prefs(context()).edit().clear().putInt("variant",201).putStringSet("test",new HashSet<>(Arrays.asList("a","b"))).commit();
        PhotoOptions.defaults().media(0,LOCK).save(context(),false);
        PhotoOptions.defaults().media(0,FRAME).save(context(),true);
        new AliveApplyBackup(context()); // Deliberately no close: simulate process loss after publication.
        Config.prefs(context()).edit().putInt("variant",606).putFloat("bad",2).commit();
        PhotoOptions.defaults().media(0,HOME).save(context(),false);
        AliveApplyBackup.recover(context());AliveApplyBackup.recover(context());
        assertEquals(201,Config.variant(context()));assertFalse(Config.prefs(context()).contains("bad"));
        assertEquals(new HashSet<>(Arrays.asList("a","b")),Config.prefs(context()).getStringSet("test",null));
        assertEquals(LOCK,PhotoOptions.load(context(),false).lockPhoto);assertEquals(FRAME,PhotoOptions.load(context(),true).lockPhoto);
    }
    @Test public void acceptedJournalNeverRollsBackOnNextProcessStart()throws Exception {
        Config.prefs(context()).edit().putInt("variant",1).commit();
        AliveApplyBackup backup=new AliveApplyBackup(context());
        Config.prefs(context()).edit().putInt("variant",606).commit();backup.accept();
        AliveApplyBackup.recover(context());assertEquals(606,Config.variant(context()));
    }
    @Test public void pinchRetainsFocusAndNeverSamplesOutsideImage(){
        float[] v=PhotoGeometry.gesture(.5f,.5f,1,2,.5f,0,0,.8f,.2f,2);
        assertEquals(.5375f,v[0],.00001f);assertEquals(.35f,v[1],.00001f);
        for(int i=0;i<500;i++){
            v=PhotoGeometry.gesture(v[0],v[1],v[2],2,.5f,i%2==0?3:-3,-2,.9f,.1f,1.3f);
            float dx=PhotoGeometry.cropX(.5f,2)/v[2]/2,dy=PhotoGeometry.cropY(.5f,2)/v[2]/2;
            assertTrue(v[0]>=dx&&v[0]<=1-dx);assertTrue(v[1]>=dy&&v[1]<=1-dy);assertTrue(v[2]<=4);
        }
        assertArrayEquals(v,PhotoGeometry.gesture(v[0],v[1],v[2],2,.5f,0,0,0,0,Float.NaN),0);
    }
}
