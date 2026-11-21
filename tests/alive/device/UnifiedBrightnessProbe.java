package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import android.graphics.RuntimeShader;
import android.opengl.*;
import java.lang.reflect.Field;
import java.nio.*;

/** Runs actual GL kernels and compiles both AGSL subjects, without installing or binding. */
public final class UnifiedBrightnessProbe {
    private static int at(int p,String n){return GLES30.glGetUniformLocation(p,n);}
    private static void f(int p,String n,float v){GLES30.glUniform1f(at(p,n),v);}
    private static int shader(int type,String source){
        int s=GLES30.glCreateShader(type);GLES30.glShaderSource(s,source);GLES30.glCompileShader(s);
        int[] ok=new int[1];GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new AssertionError(GLES30.glGetShaderInfoLog(s));return s;
    }
    private static int program(String vert,String frag){
        int v=shader(GLES30.GL_VERTEX_SHADER,vert),s=shader(GLES30.GL_FRAGMENT_SHADER,frag);
        int p=GLES30.glCreateProgram();GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,s);GLES30.glLinkProgram(p);
        int[] ok=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new AssertionError(GLES30.glGetProgramInfoLog(p));
        GLES30.glDeleteShader(v);GLES30.glDeleteShader(s);return p;
    }
    private static String field(String cls,String name)throws Exception {
        Field f=Class.forName("com.frederic22.hyperextreme.hook."+cls).getDeclaredField(name);f.setAccessible(true);return (String)f.get(null);
    }
    private static float tone(float source,float gain){return gain<=1?source*gain:1-(float)Math.pow(1-source,gain);}
    public static void main(String[] args)throws Exception{
        EGLDisplay d=EglWorker.acquireDisplay();EGLConfig cfg=CosmicEgl.choose(d,EGL14.EGL_PBUFFER_BIT,false);
        EGLContext c=EGL14.eglCreateContext(d,cfg,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        EGLSurface s=EGL14.eglCreatePbufferSurface(d,cfg,new int[]{EGL14.EGL_WIDTH,16,EGL14.EGL_HEIGHT,16,EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(d,s,s,c))throw new AssertionError("EGL current");
        System.out.println("GPU="+GLES30.glGetString(GLES30.GL_RENDERER));
        float[] gains={.25f,.5f,1,1.3f,2,3,5,100,1e20f,Float.MAX_VALUE};
        int cases=0;
        for(String family:new String[]{"Spatial","Raster"}){
            new RuntimeShader(field(family+"Overlay","AGSL"));
            Class<?> type=Class.forName("com.frederic22.hyperextreme.hook."+family+"GlCore");
            int p=program(field(family+"GlCore","VERT"),(String)type.getMethod("getFRAG").invoke(type.getField("INSTANCE").get(null)));
            GLES30.glUseProgram(p);GLES30.glViewport(0,0,16,16);GLES30.glDisable(GLES30.GL_BLEND);
            FloatBuffer points=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
            points.put(new float[]{-1,-1,1,-1,-1,1,1,1}).flip();
            int pos=GLES30.glGetAttribLocation(p,"aPos");GLES30.glEnableVertexAttribArray(pos);GLES30.glVertexAttribPointer(pos,2,GLES30.GL_FLOAT,false,0,points);
            int uv=GLES30.glGetAttribLocation(p,"aUv");
            if(uv>=0){GLES30.glDisableVertexAttribArray(uv);GLES30.glVertexAttrib2f(uv,.5f,.5f);}
            GLES30.glUniform2f(at(p,"uK"),.5f,.5f);GLES30.glUniform2f(at(p,"uSpan"),.5f,.5f);GLES30.glUniform2f(at(p,"uUvScale"),1,1);
            GLES30.glUniform4f(at(p,"uFromUv"),1,1,0,0);GLES30.glUniform4f(at(p,"uToUv"),1,1,0,0);
            f(p,"uBand",.2f);f(p,"uDim",.5f);
            int[] tex=new int[1];GLES30.glGenTextures(1,tex,0);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,tex[0]);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
            for(float gain:gains)for(int alpha:new int[]{128,255})for(float source:new float[]{0,.05f,.2f,.6f,1}){
                int rgb=Math.round(source*alpha);
                ByteBuffer pixel=ByteBuffer.allocateDirect(4);pixel.put(new byte[]{(byte)rgb,(byte)rgb,(byte)rgb,(byte)alpha}).flip();
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA,1,1,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixel);
                f(p,"uExposure",gain);GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);
                ByteBuffer read=ByteBuffer.allocateDirect(4);GLES30.glReadPixels(8,8,1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,read);
                int actual=read.get(0)&255,expected=Math.round(tone(rgb/(float)alpha*.5f,gain)*alpha);
                if(Math.abs(actual-expected)>3||(read.get(3)&255)!=alpha)throw new AssertionError(family+" gain="+gain+" expected="+expected+" actual="+actual+" alpha="+(read.get(3)&255));
                if(GLES30.glGetError()!=0)throw new AssertionError("GL error");cases++;
            }
            GLES30.glDeleteTextures(1,tex,0);GLES30.glDeleteProgram(p);
            System.out.println(family+" GL pixel cases passed; AGSL compiled");
        }
        AssetManager assets=AssetManager.class.getConstructor().newInstance();
        AssetManager.class.getMethod("addAssetPath",String.class).invoke(assets,args[0]);
        AodPresentation pass=new AodPresentation(assets,16,16);
        for(float gain:gains)for(float source:new float[]{0,.05f,.2f,.6f,1}){
            GLES30.glClearColor(source,source,source,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            pass.render(gain);ByteBuffer read=ByteBuffer.allocateDirect(4);GLES30.glReadPixels(8,8,1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,read);
            int expected=Math.round(tone(Math.round(source*255)/255f,gain)*255);
            if(Math.abs((read.get(0)&255)-expected)>3)throw new AssertionError("Presentation gain="+gain);
            cases++;
        }
        pass.close();System.out.println("UNIFIED_BRIGHTNESS_PASS cases="+cases);
        System.exit(0);
    }
}
