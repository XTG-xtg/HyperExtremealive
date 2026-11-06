package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.opengl.*;
import android.os.Looper;
import java.nio.ByteBuffer;

/** MSAA resolve and black-preserving AOD brightness, using synthetic pixels only. */
public final class PresentationProbe {
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");
        Context system=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context context=ProbeContext.wrap(system);
        EGLDisplay d=EglWorker.acquireDisplay();EGLConfig config=CosmicEgl.choose(d,EGL14.EGL_PBUFFER_BIT);
        EGLContext c=EGL14.eglCreateContext(d,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        EGLSurface s=EGL14.eglCreatePbufferSurface(d,config,new int[]{EGL14.EGL_WIDTH,64,EGL14.EGL_HEIGHT,96,EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(d,s,s,c))throw new AssertionError("EGL current");
        AodPresentation pass=new AodPresentation(context.getAssets(),64,96);
        for(float gain:new float[]{.25f,.5f,1,1.3f,2,3})for(float source:new float[]{0,.05f,.2f,.6f,1}){
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST);GLES30.glColorMask(true,true,true,true);
            GLES30.glClearColor(source,source,source,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            if(gain!=1){GLES30.glEnable(GLES30.GL_SCISSOR_TEST);GLES30.glScissor(0,0,1,1);GLES30.glColorMask(false,false,false,false);}
            pass.render(gain);ByteBuffer pixel=ByteBuffer.allocateDirect(4);
            GLES30.glReadPixels(30,40,1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixel);
            int error=GLES30.glGetError();if(error!=GLES30.GL_NO_ERROR)throw new AssertionError("GL "+error);
            int actual=pixel.get(0)&255,expected=Math.round((gain<=1?source*gain:1-(float)Math.pow(1-source,gain))*255);
            if(Math.abs(actual-expected)>3)throw new AssertionError("tone "+source+" expected="+expected+" actual="+actual);
            if(source==0&&actual!=0)throw new AssertionError("Black must stay black");
            System.out.println("TONE source="+source+" gain="+gain+" expected="+expected+" actual="+actual);
        }
        for(float y:new float[]{-.25f,0,.25f}){
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST);GLES30.glColorMask(true,true,true,true);
            GLES30.glClearColor(0,0,0,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST);GLES30.glScissor(24,40,16,16);
            GLES30.glClearColor(.5f,.5f,.5f,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST);pass.render(1,0,y);
            ByteBuffer pixels=ByteBuffer.allocateDirect(64*96*4);
            GLES30.glReadPixels(0,0,64,96,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
            float sum=0;int count=0;
            for(int row=0;row<96;row++)for(int x=0;x<64;x++)if((pixels.get((row*64+x)*4)&255)>50){sum+=row;count++;}
            if(count!=256||Math.abs(sum/count-(47.5f-y*96))>.6f)throw new AssertionError("Position sign or extent: "+y);
        }
        if(GLES30.glGetError()!=GLES30.GL_NO_ERROR)throw new AssertionError("Position GL error");
        System.out.println("PRESENTATION_POSITION_OK");
        pass.close();EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(d,s);EGL14.eglDestroyContext(d,c);EglWorker.releaseDisplay();
        System.out.println("PRESENTATION_OK");System.exit(0);
    }
}
