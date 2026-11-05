package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

/** Native-resolution consumed-window benchmark; no wallpaper registration or power commands. */
public final class PhotoPerfProbe {
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();Handler main=new Handler(Looper.getMainLooper());
        Class<?> type=Class.forName("android.app.ActivityThread");
        Context system=(Context)type.getMethod("getSystemContext").invoke(type.getMethod("systemMain").invoke(null));
        Context installed=system.createPackageContext(Protocol.PACKAGE,Context.CONTEXT_IGNORE_SECURITY);
        Context context=new ContextWrapper(installed){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){return new File("/data/local/tmp/hyperextreme-photo-worker");}
        };
        File photo=new File(context.getFilesDir(),"alive/photo.png");photo.getParentFile().mkdirs();
        java.nio.file.Files.copy(new File("/data/local/tmp/hyperextreme-photo-test/portrait.png").toPath(),photo.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        int variant=Integer.parseInt(args[0]);
        android.hardware.display.DisplayManager displays=system.getSystemService(android.hardware.display.DisplayManager.class);
        float startHz=displays.getDisplay(android.view.Display.DEFAULT_DISPLAY).getRefreshRate();
        EglWorker worker=new EglWorker(context,true);
        ImageReader reader=ImageReader.newInstance(1200,2670,PixelFormat.RGBA_8888,3);
        ArrayList<Long> gaps=new ArrayList<>();long[] last={0},idle={0},first={0};
        long epoch=SystemClock.uptimeMillis();
        reader.setOnImageAvailableListener(source->{try(Image image=source.acquireLatestImage()){
            if(image==null)return;long now=SystemClock.uptimeMillis(),t=now-epoch;
            if(first[0]==0)first[0]=t;
            if(t>=1350&&t<1750||t>=4950&&t<5350){if(last[0]!=0)gaps.add(now-last[0]);last[0]=now;}else last[0]=0;
            if(t>=8000)idle[0]++;
        }},main);
        worker.state(new EngineRegistry.Snapshot(0,0,epoch,1));
        worker.attach(reader.getSurface(),1200,2670,variant,false);worker.visible(true);
        main.postDelayed(()->worker.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),2)),1200);
        main.postDelayed(()->worker.state(new EngineRegistry.Snapshot(1,2,SystemClock.uptimeMillis(),3)),3000);
        main.postDelayed(()->worker.state(new EngineRegistry.Snapshot(2,0,SystemClock.uptimeMillis(),4)),4900);
        main.postDelayed(()->{
            Collections.sort(gaps);if(gaps.isEmpty()||!worker.error.isEmpty())throw new AssertionError(worker.error);
            System.out.println("PHOTO_PERF variant="+variant+" size=1200x2670 firstMs="+first[0]+" samples="+gaps.size()+
                " gapP50Ms="+gaps.get(gaps.size()/2)+" gapP95Ms="+gaps.get((int)(gaps.size()*.95))+" gapMaxMs="+gaps.get(gaps.size()-1)+
                " idleWindowMs=2000 idleFrames="+idle[0]+" total="+worker.renderedFrames+
                " startHz="+startHz+" endHz="+displays.getDisplay(android.view.Display.DEFAULT_DISPLAY).getRefreshRate());
            worker.close();reader.close();main.postDelayed(()->System.exit(0),300);
        },10000);
        Looper.loop();
    }
}
