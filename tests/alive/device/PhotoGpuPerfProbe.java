package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.opengl.*;
import android.os.Looper;
import java.io.File;
import java.util.Arrays;

/** Synchronous GPU work, independent of display-vsync throttling of background app_process. */
public final class PhotoGpuPerfProbe {
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context c=ProbeContext.vivo(base,"/data/local/tmp/hyperextreme-vivo-gpu-test");
        File image=PhotoMedia.prepare(c,PhotoOptions.defaults(),true,0);
        EGLDisplay d=EglWorker.acquireDisplay();EGLConfig config=CosmicEgl.choose(d,EGL14.EGL_PBUFFER_BIT,false);
        EGLContext context=EGL14.eglCreateContext(d,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        EGLSurface surface=EGL14.eglCreatePbufferSurface(d,config,new int[]{EGL14.EGL_WIDTH,1200,EGL14.EGL_HEIGHT,2670,EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(d,surface,surface,context))throw new AssertionError("GPU benchmark EGL");
        for(int mode=0;mode<3;mode++){
            PhotoRenderer renderer=new PhotoRenderer(c.getAssets(),image,image,image,1200,2670,mode,601,PhotoOptions.defaults().edit(0,2).edit(10,3),()->{});
            long[] samples=new long[90];
            for(int i=-20;i<samples.length;i++){
                long start=System.nanoTime();renderer.render();GLES30.glFinish();
                if(i>=0)samples[i]=System.nanoTime()-start;
                if(GLES30.glGetError()!=0)throw new AssertionError("GPU benchmark GL");
            }
            Arrays.sort(samples);
            System.out.println("PHOTO_GPU_PERF mode="+mode+" size=1200x2670 samples="+samples.length+" p50Ms="+samples[45]/1e6+" p95Ms="+samples[85]/1e6);
            renderer.close();
        }
        EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(d,surface);EGL14.eglDestroyContext(d,context);EglWorker.releaseDisplay();System.exit(0);
    }
}
