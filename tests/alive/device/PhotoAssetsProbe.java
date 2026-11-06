package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

/** Pixel assertions for three distinct input files, including the asynchronous paper mask. */
public final class PhotoAssetsProbe {
    private static final int W=360,H=800;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Context app;
    private File lock,home,frame;
    private PhotoRenderer renderer;
    private int step,checks;
    private long started;
    private final ByteBuffer pixels=ByteBuffer.allocateDirect(W*H*4);
    private void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private File image(String name,int color)throws IOException {
        File f=new File("/data/local/tmp/hyperextreme-photo-test/"+name+".png");
        Bitmap b=Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);b.eraseColor(color);
        try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}return f;
    }
    void start()throws Exception {
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context system=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        app=ProbeContext.wrap(system.createPackageContext(Protocol.PACKAGE,Context.CONTEXT_IGNORE_SECURITY));
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);check(EGL14.eglInitialize(display,new int[2],0,new int[2],0),"initialize");
        EGLConfig config=CosmicEgl.choose(display,EGL14.EGL_PBUFFER_BIT);
        EGLContext context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        EGLSurface surface=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
        check(EGL14.eglMakeCurrent(display,surface,surface,context),"current");
        lock=image("slot-lock",0xffe02020);home=image("slot-home",0xff20e020);frame=image("slot-frame",0xff2020e0);next();
    }
    void next()throws Exception {
        if(renderer!=null){renderer.close();renderer=null;}
        if(step==5){System.out.println("PHOTO_ASSETS_PASS checks="+checks+" lock=red home=green frame=blue follow=red");System.exit(0);return;}
        int mode=step==0?1:step==2?0:2;
        PhotoOptions options=PhotoOptions.defaults().edit(14,step==3?1:0);
        if(step==4)options=options.edit(15,2);
        renderer=new PhotoRenderer(app.getAssets(),lock,home,frame,W,H,mode,602,options,()->handler.post(this::render));
        started=SystemClock.uptimeMillis();handler.post(this::render);
    }
    void render(){
        try {
            renderer.render();check(GLES30.glGetError()==0,"GL step "+step);
            if(!renderer.frameReady()){
                check(SystemClock.uptimeMillis()-started<5000,"mask timeout");handler.removeCallbacksAndMessages(null);handler.postDelayed(this::render,8);return;
            }
            pixels.clear();GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
            long[] sum=new long[3];int[] dominant=new int[3];
            for(int i=0;i<W*H;i++){
                int r=pixels.get(i*4)&255,g=pixels.get(i*4+1)&255,b=pixels.get(i*4+2)&255;
                sum[0]+=r;sum[1]+=g;sum[2]+=b;
                if(r>g+60&&r>b+60)dominant[0]++;
                if(g>r+60&&g>b+60)dominant[1]++;
                if(b>r+60&&b>g+60)dominant[2]++;
            }
            int expected=step==0||step==3?0:step==2?2:1;
            check(dominant[expected]>(step==2?500:W*H*.9),"independent source step="+step+" dominant="+java.util.Arrays.toString(dominant));
            if(step==2)check(dominant[0]<100&&dominant[1]<100,"AOD paper must not show lock or home image");
            step++;handler.removeCallbacksAndMessages(null);next();
        }catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
    public static void main(String[] args)throws Exception{Looper.prepareMainLooper();new PhotoAssetsProbe().start();Looper.loop();}
}
