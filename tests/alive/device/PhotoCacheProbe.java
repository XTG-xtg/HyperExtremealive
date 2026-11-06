package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import android.os.Looper;
import java.io.*;
import java.nio.ByteBuffer;

/** Compare reused targets with a fresh frame graph after each crop/tint/effect change. */
public final class PhotoCacheProbe {
    private static final int W=180,H=400;
    private static byte[] pixels(PhotoRenderer renderer){
        renderer.render();ByteBuffer bytes=ByteBuffer.allocateDirect(W*H*4);
        GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,bytes);
        if(GLES30.glGetError()!=0)throw new AssertionError("Cache GL error");
        byte[] result=new byte[bytes.capacity()];bytes.get(result);return result;
    }
    private static void same(byte[] a,byte[] b,String message){
        int max=0;for(int i=0;i<a.length;i++)max=Math.max(max,Math.abs((a[i]&255)-(b[i]&255)));
        if(max>2)throw new AssertionError(message+" max difference="+max);
    }
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");
        Context system=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context c=ProbeContext.vivo(system,"/data/local/tmp/hyperextreme-vivo-cache-test");
        File[] sources=new File[3];
        for(int i=0;i<3;i++){
            Bitmap image=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);
            for(int y=0;y<800;y++)for(int x=0;x<360;x++)image.setPixel(x,y,0xff000000|((x*255/360)<<(i*8))|((y*255/800)<<(((i+1)%3)*8)));
            sources[i]=new File(c.getFilesDir(),"source"+i+".png");
            try(FileOutputStream out=new FileOutputStream(sources[i])){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        }
        EGLDisplay d=EglWorker.acquireDisplay();EGLConfig config=CosmicEgl.choose(d,EGL14.EGL_PBUFFER_BIT,false);
        EGLContext context=EGL14.eglCreateContext(d,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        EGLSurface surface=EGL14.eglCreatePbufferSurface(d,config,new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(d,surface,surface,context))throw new AssertionError("Cache EGL");
        int checks=0;
        for(int mode=0;mode<3;mode++)for(int style=0;style<6;style++){
            PhotoOptions base=PhotoOptions.defaults().edit(0,style).edit(10,style%4).edit(14,0);
            PhotoRenderer reused=new PhotoRenderer(c.getAssets(),sources[0],sources[1],sources[2],W,H,mode,601,base,()->{});
            for(int edit=0;edit<4;edit++){
                PhotoOptions value=base.edit(1,.25f+.15f*edit).edit(3,1+edit*.5f).edit(11,.8f-edit*.1f).edit(13,1+edit*.4f).color(0xff264552+edit*0x202010);
                reused.configure(value);byte[] first=pixels(reused);same(first,pixels(reused),"Stable cache");
                PhotoRenderer fresh=new PhotoRenderer(c.getAssets(),sources[0],sources[1],sources[2],W,H,mode,601,value,()->{});
                same(first,pixels(fresh),"Invalidation mode="+mode+" style="+style+" edit="+edit);fresh.close();checks+=2;
            }
            reused.close();
        }
        EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(d,surface);EGL14.eglDestroyContext(d,context);EglWorker.releaseDisplay();
        System.out.println("PHOTO_CACHE_PASS pixel_comparisons="+checks);System.exit(0);
    }
}
