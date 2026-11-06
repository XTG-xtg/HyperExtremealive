package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VivoIntegrationTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun invalidOptionsCannotEscapeOrExceedLimits() {
        assertNull(VivoOptions.parse("{\"family\":4}"))
        val o=VivoOptions.parse("""{"family":2,"style":90,"sensitivity":90,"photos":["/sdcard/a.jpg","../other","vivo-valid.png"],"slots":[{"rotation":-1,"zoom":99}]}""")!!
        assertEquals(4,o.style);assertEquals(50,o.sensitivity)
        assertEquals(listOf("vivo-valid.png"),o.photos);assertEquals(3,o.slots[0].rotation);assertEquals(8f,o.slots[0].zoom,0f)
    }
    @Test fun draftDoesNotOverwriteAppliedOrOtherFamily() {
        Config.prefs(context).edit().putString("vivo_options",VivoOptions(2).apply { style=3 }.json()).commit()
        VivoOptions(1).apply { style=4 }.saveDraft(context)
        assertEquals(4,VivoOptions.load(context,701,true).style)
        assertEquals(1,VivoOptions.load(context,701,false).style)
        assertEquals(3,VivoOptions.load(context,702,false).style)
        assertEquals(3,VivoOptions.load(context,702,true).style)
    }
    @Test fun slotsRetainVideoTransformsAndReferenceOrdering() {
        val original=VivoOptions(2).apply {
            photos.addAll(listOf("vivo-a.png","vivo-b.png","vivo-c.png"));rasterVideos.addAll(listOf("vivo-a.mp4","","vivo-c.mp4"))
            slots[2].x=.8f;slots[2].zoom=3f;slots[2].rotation=2
        }
        val copied=VivoOptions.parse(original.json())!!
        assertEquals(original.json(),copied.json());copied.slots[2].x=.1f
        assertEquals(.8f,original.slots[2].x,0f);assertFalse(original.sameScene(copied))
        val tuning=VivoOptions.parse(original.json())!!.apply { sensitivity=40;aodAnimation=false;wallpaperZoom=false }
        assertTrue(original.sameScene(tuning))
    }
    @Test fun modeSpringsFinishSleepEvenAfterInterruptedWake() {
        val zoom=VivoWallpaperZoom();val dim=VivoWallpaperDim();var t=1_000_000_000L
        zoom.mode(0,false,t);dim.target(1f,.5f,0,false,t)
        repeat(10) {
            t+=1_000_000_000L;zoom.mode(1,true,t);dim.target(1f,.5f,1,true,t)
            t+=80_000_000L;zoom.sample(t);dim.sample(t)
            zoom.mode(0,true,t);dim.target(1f,.5f,0,true,t)
            t+=5_000_000_000L
            assertEquals(1f,zoom.sample(t),.0001f);assertEquals(.5f,dim.sample(t),.0001f)
            assertFalse(zoom.active());assertFalse(dim.active())
        }
    }
    @Test fun bundledRuntimeAndModelMatchPinnedDigests() {
        fun hash(path:String)=MessageDigest.getInstance("SHA-256").digest(context.assets.open(path).use { it.readBytes() }).joinToString("") { "%02x".format(it.toInt() and 255) }
        for(name in listOf("engine.apk","textures.zip","libpag.so","libffavc.so")) {
            assertEquals(context.assets.open("vivo/runtime/$name.sha256").bufferedReader().use { it.readText().trim() },hash("vivo/runtime/$name"))
        }
        assertEquals("309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8",hash("vivo/models/u2netp.onnx"))
    }
    @Test fun cancelledSegmentationNeverLoadsNativeSession() {
        val source=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888)
        try {
            val job=VivoSubjectMask();job.cancel()
            assertThrows(CancellationException::class.java) { job.generate(context,source,android.graphics.RectF(0f,0f,1f,1f)) }
        } finally { source.recycle() }
    }
    @Test fun maskStrokeEraseUndoAndClearStayInPhotoSpace() {
        val source=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val view=VivoMaskCanvas(context,source,null);val parent=FrameLayout(context);parent.addView(view)
        val display=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888)
        try {
            view.layout(0,0,200,400);view.draw(Canvas(display));view.brush=.1f
            fun touch(action:Int,x:Float,y:Float) { MotionEvent.obtain(0,1,action,x,y,0).let { view.onTouchEvent(it);it.recycle() } }
            touch(MotionEvent.ACTION_DOWN,100f,200f);touch(MotionEvent.ACTION_UP,100f,200f)
            assertEquals(Color.WHITE,view.mask.getPixel(50,100));assertEquals(Color.BLACK,view.mask.getPixel(0,0))
            view.erase=true;touch(MotionEvent.ACTION_DOWN,100f,200f);touch(MotionEvent.ACTION_UP,100f,200f)
            assertEquals(Color.BLACK,view.mask.getPixel(50,100));view.undo();assertEquals(Color.WHITE,view.mask.getPixel(50,100))
            view.clear();assertEquals(Color.BLACK,view.mask.getPixel(50,100));view.undo();assertEquals(Color.WHITE,view.mask.getPixel(50,100))
        } finally { view.close();display.recycle() }
    }
}
