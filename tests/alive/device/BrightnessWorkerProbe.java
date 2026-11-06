package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

/** Settled real window: option changes alone must repaint; no system brightness or wallpaper writes. */
public final class BrightnessWorkerProbe {
    private static final float[] GAINS={.25f,1,3,.25f,.25f,3};
    private static final int W=288,H=640;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final long[] sums=new long[GAINS.length];
    private final int variant;
    private EglWorker worker;
    private ImageReader reader;
    private long sum,count,before;
    private int step;
    BrightnessWorkerProbe(int variant){this.variant=variant;}
    private AliveRenderOptions options(float gain){return new AliveRenderOptions(false,false,false,false,false,false,0,gain);}
    void start()throws Exception{
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context c=ProbeContext.vivo(base,"/data/local/tmp/hyperextreme-vivo-brightness-test");
        File source=new File(c.getFilesDir(),"alive/photo-draft.png");source.getParentFile().mkdirs();
        Bitmap bitmap=Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff508080);
        Paint paint=new Paint();paint.setColor(0xffb07040);canvas.drawCircle(160,260,130,paint);
        try(FileOutputStream out=new FileOutputStream(source)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        worker=new EglWorker(c,true);worker.options(options(GAINS[0]));worker.state(new EngineRegistry.Snapshot(0,0,0,0));
        reader=ImageReader.newInstance(W,H,PixelFormat.RGBA_8888,3);
        reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){
            if(image==null)return;Image.Plane plane=image.getPlanes()[0];ByteBuffer data=plane.getBuffer();long light=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=y*plane.getRowStride()+x*plane.getPixelStride();light+=(data.get(i)&255)+(data.get(i+1)&255)+(data.get(i+2)&255);}
            sum=light;count++;
        }},main);
        worker.attach(reader.getSurface(),W,H,variant,false);worker.visible(true);main.postDelayed(this::sample,2400);
    }
    void sample(){try{
        if(!worker.error.isEmpty()||count<=before||sum==0)throw new AssertionError("No valid repaint "+worker.error+" step="+step);
        sums[step]=sum;before=count;
        System.out.println("BRIGHTNESS_SAMPLE variant="+variant+" step="+step+" gain="+GAINS[step]+" sum="+sum+" frames="+count);
        if(++step==GAINS.length){
            if(!(sums[1]>sums[0]*2.5&&sums[2]>sums[1]*1.15))throw new AssertionError("Brightness not effective "+java.util.Arrays.toString(sums));
            if(Math.abs(sums[3]-sums[0])>sums[0]*.02)throw new AssertionError("Compounded exposure "+java.util.Arrays.toString(sums));
            if(Math.abs(sums[4]-sums[5])>sums[4]*.005)throw new AssertionError("AOD setting changed lock image "+java.util.Arrays.toString(sums));
            System.out.println("BRIGHTNESS_WORKER_PASS variant="+variant+" luminance="+java.util.Arrays.toString(sums)+" lock_unchanged=true");
            worker.close();main.postDelayed(()->{reader.close();System.exit(0);},300);return;
        }
        if(step==4){
            if(variant>=602)delayMaskDecoder();
            worker.state(new EngineRegistry.Snapshot(0,1,SystemClock.uptimeMillis(),1));
        }
        // Generic scenes keep their transition draw window open for 2500 ms.
        worker.options(options(GAINS[step]));main.postDelayed(this::sample,step==4?6500:1800);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
    private void delayMaskDecoder()throws Exception{
        java.lang.reflect.Field field=EglWorker.class.getDeclaredField("handler");field.setAccessible(true);
        ((Handler)field.get(worker)).post(()->{try{
            java.lang.reflect.Field r=EglWorker.class.getDeclaredField("renderer");r.setAccessible(true);
            java.lang.reflect.Field m=PhotoRenderer.class.getDeclaredField("masks");m.setAccessible(true);
            java.lang.reflect.Field h=MaskFrames.class.getDeclaredField("worker");h.setAccessible(true);
            // Isolated test thread only: simulate a slow mask decode beyond the transition duration.
            ((Handler)h.get(m.get(r.get(worker)))).post(()->{try{Thread.sleep(3200);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            System.out.println("DELAYED_MASK_TEST variant="+variant);
        }catch(Throwable e){e.printStackTrace();System.exit(1);}});
    }
    public static void main(String[] args)throws Exception{Looper.prepareMainLooper();new BrightnessWorkerProbe(Integer.parseInt(args[0])).start();Looper.loop();}
}
