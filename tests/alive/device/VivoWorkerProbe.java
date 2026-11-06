package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import java.lang.reflect.Field;

/** Real EglWorker window + MSAA presentation, hidden events and surface replacement. */
public final class VivoWorkerProbe {
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Handler main=new Handler(Looper.getMainLooper());
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context context=ProbeContext.vivo(base,"/data/local/tmp/hyperextreme-vivo-worker-test");
        int variant=Integer.parseInt(args[0]);EglWorker worker=new EglWorker(context,false);
        ImageReader[] readers={ImageReader.newInstance(360,800,PixelFormat.RGBA_8888,3),ImageReader.newInstance(480,960,PixelFormat.RGBA_8888,3)};
        long[] light={0},received={0},hidden={0};
        for(ImageReader reader:readers)reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){
            if(image==null)return;Image.Plane p=image.getPlanes()[0];java.nio.ByteBuffer b=p.getBuffer();long sum=0;
            for(int y=0;y<image.getHeight();y+=8)for(int x=0;x<image.getWidth();x+=8){int i=y*p.getRowStride()+x*p.getPixelStride();sum+=(b.get(i)&255)+(b.get(i+1)&255)+(b.get(i+2)&255);}
            light[0]=sum;received[0]++;
        }},main);
        worker.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),1));worker.attach(readers[0].getSurface(),360,800,variant,false);worker.visible(true);
        main.postDelayed(()->{
            if(received[0]<2||light[0]==0||!worker.error.isEmpty())fail("initial window: "+worker.error);
            worker.visible(false);worker.state(new EngineRegistry.Snapshot(1,0,SystemClock.uptimeMillis(),2));
        },4500);
        main.postDelayed(()->{hidden[0]=worker.renderedFrames;worker.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),3));worker.state(new EngineRegistry.Snapshot(1,0,SystemClock.uptimeMillis(),4));},5000);
        main.postDelayed(()->{
            try{((Handler)field(worker,"handler")).post(()->{try{
                if(worker.renderedFrames!=hidden[0])fail("hidden GPU submissions");
                MotionSession session=(MotionSession)field(worker,"motionSession");
                if((Long)field(session,"sequence")!=4L)fail("hidden lifecycle dropped");
                Object scene=field(field(worker,"renderer"),"scene");
                if((Boolean)field(scene,"running"))fail("hidden original controller running");
                worker.visible(true);
            }catch(Exception e){fail(e.toString());}});}catch(Exception e){fail(e.toString());}
        },6800);
        main.postDelayed(()->{
            if(worker.renderedFrames<=hidden[0]||light[0]==0||!worker.error.isEmpty())fail("AOD resume/MSAA: "+worker.error);
            worker.detach();worker.attach(readers[1].getSurface(),480,960,variant,false);worker.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),5));
        },9000);
        main.postDelayed(()->{
            if(worker.renderedFrames<2||light[0]==0||!worker.error.isEmpty())fail("reattach: "+worker.error);
            System.out.println("VIVO_WORKER_PASS variant="+variant+" received="+received[0]+" msaa_aod=ok hidden_events=ok resize=ok");
            worker.close();main.postDelayed(()->{for(ImageReader reader:readers)reader.close();System.exit(0);},500);
        },13000);
        main.postDelayed(()->fail("timeout"),25000);Looper.loop();
    }
    private static void fail(String reason){System.err.println(reason);System.exit(1);}
}
