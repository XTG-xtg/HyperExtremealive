package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Real Looper test of interrupted/repeated photo transitions; never touches display power or wallpaper. */
public final class PhotoInterruptionProbe {
    private static final class Photo implements WallpaperMotion {
        final SceneMotion scene;
        final FrameMotion paper;
        Photo(boolean framed){scene=new SceneMotion(1200,2670,2,framed?-1:0,0,6,false);paper=framed?new FrameMotion(1200,2670,2,true):null;}
        public void change(int mode,boolean animate){if(paper!=null){paper.change(mode,scene);if(!animate)paper.finish();}scene.change(mode);if(!animate)scene.finish();}
        public void advance(long time,float speed){if(paper!=null)paper.advance(time);scene.advance(time,speed);}
        public void pause(){scene.pause(true);}
        public void follow(float x,float y){}
        public boolean active(){return scene.active()||paper!=null&&paper.active();}
        void close(){scene.close();if(paper!=null)paper.close();}
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Photo[] photos={new Photo(false),new Photo(true)};
    private final MotionSession[] sessions={new MotionSession(photos[0]),new MotionSession(photos[1])};
    private final Object owner=new Object();
    private long epoch,sequence,at;
    private int mode=2,checks,events;
    private boolean visible=true,closed;

    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private void event(int next) {
        mode=next;at=SystemClock.uptimeMillis()*1000000L;sequence++;events++;
        for(MotionSession session:sessions)session.frame(sequence,mode,at,at,()->{});
    }
    private void visible(boolean value){visible=value;for(MotionSession session:sessions)session.visible(owner,value);}
    private void assertAod(String label) {
        Photo lens=photos[0],paper=photos[1];
        check(Math.abs(lens.scene.values[4]-.22f)<.0001f,label+" lens scale="+lens.scene.values[4]);
        check(Math.abs(lens.scene.values[5]-9)<.0001f,label+" distortion");
        check(Math.abs(lens.scene.values[6]-1)<.0001f,label+" decorator");
        check(Math.abs(lens.scene.values[0]-1)<.0001f,label+" lens AOD ratio");
        check(Math.abs(paper.paper.values[2]-paper.paper.style.scale)<.0001f,label+" frame scale="+paper.paper.values[2]);
        check(paper.paper.values[6]==0,label+" start mask="+paper.paper.values[6]);
        check(Math.abs(paper.paper.ratios[0]-1)<.0001f,label+" paper AOD ratio");
    }
    private void tick() {
        if(closed)return;
        try {
            long now=SystemClock.uptimeMillis();
            if(visible)for(MotionSession session:sessions)session.frame(sequence,mode,at,now*1000000L,()->{});
            for(Photo photo:photos)for(float value:photo.scene.values)check(!Float.isNaN(value)&&!Float.isInfinite(value),"nonfinite scene");
            long elapsed=now-epoch;
            if(elapsed>=3200&&elapsed<4000||elapsed>=7100&&elapsed<8000)assertAod("settled "+elapsed);
            handler.postDelayed(this::tick,16);
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private void later(long delay,Runnable action){handler.postDelayed(()->{try{action.run();}catch(Throwable failure){failure.printStackTrace();System.exit(1);}},delay);}
    private void start() {
        epoch=SystemClock.uptimeMillis();visible(true);event(2);tick();
        later(100,()->event(0));
        later(1000,()->event(1));
        later(1100,()->event(0)); // Power pressed during expansion, not at an endpoint.
        later(1700,()->event(1));
        later(1900,()->event(0));
        later(1920,()->visible(false));
        later(2000,()->event(0)); // Duplicate sleep while the engine is hidden.
        later(3000,()->visible(true));
        later(4300,()->event(1));
        later(4800,()->event(2));
        later(5000,()->event(0));
        later(5500,()->event(1));
        later(5510,()->event(0));
        later(5530,()->event(1));
        later(5550,()->event(0));
        later(8100,()->{assertAod("final");closed=true;for(Photo p:photos)p.close();System.out.println("PHOTO_INTERRUPTION_PASS events="+events+" checks="+checks+" finalLensScale="+photos[0].scene.values[4]+" finalMask="+photos[1].paper.values[6]);System.exit(0);});
    }
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");
        Context context=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        new PhotoInterruptionProbe().start();Looper.loop();
    }
}
