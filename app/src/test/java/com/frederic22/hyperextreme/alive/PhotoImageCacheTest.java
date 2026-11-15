package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Looper;
import java.io.IOException;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class PhotoImageCacheTest {
    private Bitmap image() { return Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); }
    @Test public void firstLockUploadReusesHomeDecodeAndExpiryReleasesIt() throws Exception {
        PhotoImageCache cache=new PhotoImageCache(1024,1000);
        Bitmap image=image();int[] decodes={0};
        PhotoImageCache.Decoder decoder=()->{decodes[0]++;return image;};
        cache.use("immutable:1",decoder,b->assertSame(image,b));
        cache.use("immutable:1",decoder,b->assertFalse(b.isRecycled()));
        assertEquals(1,decodes[0]);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(image.isRecycled());
    }
    @Test public void newPhotoOrSamplingKeyNeverUsesTheOldImage() throws Exception {
        PhotoImageCache cache=new PhotoImageCache(1024,1000);Bitmap old=image(),next=image();
        cache.use("old:1",()->old,b->{});
        cache.use("new:2",()->next,b->assertSame(next,b));
        assertTrue(old.isRecycled());assertFalse(next.isRecycled());cache.clear();
        assertTrue(next.isRecycled());
    }
    @Test public void oversizeNotRetainedAndUploadFailureDoesNotLeak() throws Exception {
        PhotoImageCache cache=new PhotoImageCache(1,1000);Bitmap image=image();
        try {cache.use("large",()->image,b->{throw new IllegalStateException("upload");});fail();}
        catch(IllegalStateException expected) { assertTrue(image.isRecycled()); }
    }
    @Test public void failedDecodeCanBeRetried() throws Exception {
        PhotoImageCache cache=new PhotoImageCache(1024,1000);
        try {cache.use("a",()->null,b->fail());fail();}catch(IOException expected) { }
        Bitmap image=image();cache.use("a",()->image,b->assertSame(image,b));cache.clear();
    }
}
