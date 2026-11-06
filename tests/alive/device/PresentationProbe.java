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
        pass.close();EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(d,s);EGL14.eglDestroyContext(d,c);EglWorker.releaseDisplay();
        System.out.println("PRESENTATION_OK");System.exit(0);
    }
}
