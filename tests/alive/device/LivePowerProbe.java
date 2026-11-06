package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Field;

/** Real worker/Surface lifecycle with injected scene events, without changing device power. */
public final class LivePowerProbe {
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Handler main=new Handler(Looper.getMainLooper());
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context system=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context context=ProbeContext.wrap(system);int variant=Integer.parseInt(args[0]);
        EglWorker worker=new EglWorker(context,false);
        ImageReader reader=ImageReader.newInstance(360,800,PixelFormat.RGBA_8888,3);
        reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){}},main);
        long epoch=SystemClock.uptimeMillis();
        worker.state(new EngineRegistry.Snapshot(2,2,epoch,1));worker.attach(reader.getSurface(),360,800,variant,false);worker.visible(true);
        main.postDelayed(()->{worker.visible(false);worker.state(new EngineRegistry.Snapshot(2,0,SystemClock.uptimeMillis(),2));},600);
        main.postDelayed(()->worker.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),3)),1000);
        main.postDelayed(()->worker.state(new EngineRegistry.Snapshot(1,0,SystemClock.uptimeMillis(),4)),1100);
        long[] hiddenFrames={-1};
        main.postDelayed(()->hiddenFrames[0]=worker.renderedFrames,1300);
        main.postDelayed(()->{
            try{
                Handler gl=(Handler)field(worker,"handler");
                gl.post(()->{try{
                    if(!worker.error.isEmpty())throw new AssertionError(worker.error);
                    MotionSession motion=(MotionSession)field(worker,"motionSession");
                    if((Long)field(motion,"sequence")!=4L)throw new AssertionError("Hidden events were dropped");
                    if(worker.renderedFrames!=hiddenFrames[0])throw new AssertionError("Invisible worker submitted GPU frames");
                    System.out.println("HIDDEN_EVENTS_OK variant="+variant+" sequence=4 frames="+worker.renderedFrames);
                    worker.visible(true);
                }catch(Throwable t){t.printStackTrace();System.exit(2);}});
            }catch(Exception e){throw new RuntimeException(e);}
        },2600);
        main.postDelayed(()->{
            try{Handler gl=(Handler)field(worker,"handler");gl.post(()->{try{
                if(!worker.error.isEmpty()||worker.renderedFrames<=hiddenFrames[0])throw new AssertionError("No resumed frames: "+worker.error);
                WallpaperMotion motion=((MotionSession)field(worker,"motionSession")).motion;
                if(motion.active())throw new AssertionError("Collapse did not settle");
                if(motion instanceof CosmicMotion){float[] v=((CosmicMotion)motion).values;if(Math.abs(v[2]-.22f)>.0001f)throw new AssertionError("Expanded orb: "+v[2]);}
                System.out.println("LIVE_POWER_OK variant="+variant+" frames="+worker.renderedFrames);
                worker.close();main.postDelayed(()->{reader.close();System.exit(0);},300);
            }catch(Throwable t){t.printStackTrace();System.exit(2);}});}catch(Exception e){throw new RuntimeException(e);}
        },4800);
        main.postDelayed(()->{System.err.println("Power probe timeout");System.exit(3);},15000);
        Looper.loop();
    }
}
