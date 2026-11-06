package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.opengl.GLES30;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** The Vivo engine is isolated in its own classloader and only hosted in the app's EGL process. */
final class VivoRenderer implements WallpaperRenderer {
    private final VivoEngineScene scene;
    private final Motion motion=new Motion();
    private final float night;
    private VivoOptions options;
    private boolean visible,continuous=true;
    private int mode;
    private long frameNanos=System.nanoTime();
    VivoRenderer(Context c,VivoOptions options,int w,int h,int initial,boolean preview,boolean dark,Runnable invalidate)throws IOException {
        this.options=VivoOptions.parse(options.json());mode=initial;night=dark?.76f:1f;
        try {
            VivoEngineRuntime runtime=VivoEngineRuntime.prepare(c).get(20,TimeUnit.SECONDS);
            try(VivoImages images=VivoImages.load(c,runtime,options)){
                scene=new VivoEngineScene(runtime,options,w,h,images.photos,images.subject,images.paint,preview,invalidate);
            }
            scene.transition(initial,false,night,.5f);
        }catch(Exception e){throw new IOException("Vivo engine initialization failed",e);}
    }
    public WallpaperMotion motion(){return motion;}
    public void render(){
        try{scene.drawAt(frameNanos);}catch(Exception e){throw new IllegalStateException("Vivo render failed",e);}
    }
    public void visible(boolean value){visible=value;syncAnimation();}
    public void continuous(boolean value){continuous=value;syncAnimation();}
    private void syncAnimation(){
        VivoOptions active=VivoOptions.parse(options.json());
        active.aodAnimation=options.aodAnimation&&continuous;
        scene.updateSensitivity(active);
        // Sensors and video decoders must stop in hidden slots, including engine callbacks.
        scene.animationEnabled(mode==1||continuous);
        scene.visible(visible);
    }
    void configure(VivoOptions next){
        if(scene.updateSensitivity(next)){options=VivoOptions.parse(next.json());syncAnimation();}
    }
    void touch(int action,float x,float y){if(visible&&mode!=0)scene.touch(action,x,y);}
    public void close(){scene.close();GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);}
    private final class Motion implements WallpaperMotion {
        private int pending=-1;private boolean animate;
        public void change(int next,boolean animated){pending=next;animate=animated;mode=next;}
        public void advance(long time,float speed){
            frameNanos=time;
            if(pending!=-1){scene.transitionAt(pending,animate,night,.5f,time);pending=-1;syncAnimation();}
        }
        public void follow(float x,float y) { }
        public void pause() { }
        public boolean active(){return scene.transitionActive();}
    }
}
