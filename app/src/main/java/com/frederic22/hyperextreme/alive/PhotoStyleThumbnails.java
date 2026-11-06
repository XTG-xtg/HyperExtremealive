package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

/** Six still AOD shapes from the real photo pipeline; one bounded, sequential EGL job. */
final class PhotoStyleThumbnails implements AutoCloseable {
    interface Callback { void complete(Bitmap[] images,String error); }
    private static final int W=288,H=640;
    private final Context context;
    private final PhotoOptions options;
    private final Callback callback;
    private final HandlerThread thread=new HandlerThread("AlivePhotoTiles",android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler handler;
    private final Bitmap[] images=new Bitmap[6];
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext=EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface=EGL14.EGL_NO_SURFACE;
    private PhotoRenderer renderer;
    private File photo,home,frame;
    private int index;
    private long started;
    private volatile boolean cancelled;
    private boolean finished;
    private final Runnable draw=this::render;
    PhotoStyleThumbnails(Context context,PhotoOptions options,Callback callback){
        this.context=context.getApplicationContext();this.options=options.edit(15,0).edit(8,0).edit(9,0);this.callback=callback;
        thread.start();handler=new Handler(thread.getLooper());handler.post(this::start);
    }
    static String key(Context context,PhotoOptions options){
        Bundle b=options.edit(15,0).edit(8,0).edit(9,0).bundle();StringBuilder key=new StringBuilder("photo-tiles-v1:")
            .append(java.util.Arrays.toString(b.getFloatArray("values"))).append(b.getInt("tint")).append(b.getBoolean("follow"));
        for(int slot=0;slot<3;slot++){File f=PhotoMedia.resolve(context,options,true,slot);key.append(':').append(f.getPath()).append(':').append(f.length()).append(':').append(f.lastModified());}
        return key.toString();
    }
    private void start(){
        try{
            if(cancelled){finish("cancelled");return;}
            photo=PhotoMedia.prepare(context,options,true,0);home=PhotoMedia.prepare(context,options,true,1);frame=PhotoMedia.prepare(context,options,true,2);
            display=EglWorker.acquireDisplay();EGLConfig config=CosmicEgl.choose(display,EGL14.EGL_PBUFFER_BIT,false);
            eglContext=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,eglContext))throw new IOException("Photo tile EGL failed");
            next();
        }catch(Throwable e){finish(e.toString());}
    }
    private void next(){
        try{
            if(finished)return;
            if(cancelled){finish("cancelled");return;}
            if(renderer!=null){renderer.close();renderer=null;}
            if(index==images.length){finish("");return;}
            started=SystemClock.uptimeMillis();
            renderer=new PhotoRenderer(context.getAssets(),photo,home,frame,W,H,0,601+index,options,
                ()->{if(!finished&&!cancelled){handler.removeCallbacks(draw);handler.post(draw);}});
            renderer.continuous(false);renderer.motion().change(0,false);renderer.motion().advance(1_000_000_000L,1);
            handler.post(draw);
        }catch(Throwable e){finish(e.toString());}
    }
    private void render(){
        if(finished)return;
        try{
            if(cancelled){finish("cancelled");return;}
            renderer.render();
            if(!renderer.frameReady()){
                if(SystemClock.uptimeMillis()-started>5000)throw new IOException("Photo tile mask timed out");
                handler.removeCallbacks(draw);handler.postDelayed(draw,16);return;
            }
            ByteBuffer rgba=ByteBuffer.allocateDirect(W*H*4);GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,rgba);
            if(GLES30.glGetError()!=GLES30.GL_NO_ERROR)throw new IOException("Photo tile readback failed");
            int[] pixels=new int[W*H];int left=W,top=H,right=0,bottom=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int i=((H-1-y)*W+x)*4,r=rgba.get(i)&255,g=rgba.get(i+1)&255,b=rgba.get(i+2)&255;
                pixels[y*W+x]=0xff000000|(r<<16)|(g<<8)|b;
                if(Math.max(r,Math.max(g,b))>12){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
            }
            // Crop empty AOD space, not the effect itself. Every tile keeps the original aspect.
            if(left>right){left=0;top=0;right=W-1;bottom=H-1;}
            int pad=10;left=Math.max(0,left-pad);top=Math.max(0,top-pad);right=Math.min(W-1,right+pad);bottom=Math.min(H-1,bottom+pad);
            images[index++]=Bitmap.createBitmap(pixels,top*W+left,W,right-left+1,bottom-top+1,Bitmap.Config.ARGB_8888);
            handler.removeCallbacks(draw);handler.post(this::next);
        }catch(Throwable e){finish(e.toString());}
    }
    private void finish(String error){
        if(finished)return;finished=true;handler.removeCallbacksAndMessages(null);
        try{if(renderer!=null)renderer.close();}catch(RuntimeException ignored){}renderer=null;
        if(display!=EGL14.EGL_NO_DISPLAY){
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,eglContext);
            EglWorker.releaseDisplay();EGL14.eglReleaseThread();display=EGL14.EGL_NO_DISPLAY;
        }
        if(cancelled||!error.isEmpty())for(Bitmap image:images)if(image!=null)image.recycle();
        if(!cancelled)callback.complete(error.isEmpty()?images:new Bitmap[0],error);thread.quitSafely();
    }
    @Override public void close(){cancelled=true;handler.post(()->finish("cancelled"));}
}
