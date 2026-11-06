package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

/** Consumed GPU surface and synthetic image only; never changes wallpaper or power state. */
public final class PlacementWorkerProbe {
    private static final int W=360,H=800;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final int variant;
    private EglWorker worker;
    private Context context;
    private ImageReader reader;
    private AliveRenderOptions render=new AliveRenderOptions(false,false,false,false,false,false,0,1);
    private PhotoOptions photo=PhotoOptions.defaults();
    private float center;
    private long frames;
    private int step;
    private final float[] centers=new float[6];
    PlacementWorkerProbe(int variant){this.variant=variant;}
    void start()throws Exception{
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context c=ProbeContext.vivo(base,"/data/local/tmp/hyperextreme-vivo-placement-test");
        File source=new File(c.getFilesDir(),"alive/photo-draft.png");source.getParentFile().mkdirs();
        Bitmap bitmap=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawColor(0xffa0b8c0);
        try(FileOutputStream out=new FileOutputStream(source)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        java.nio.file.Files.copy(source.toPath(),new File(source.getParentFile(),"photo.png").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        context=new android.content.ContextWrapper(c){
            public Context getApplicationContext(){return this;}
            public android.content.SharedPreferences getSharedPreferences(String name,int mode){
                android.content.SharedPreferences original=super.getSharedPreferences(name,mode);
                return (android.content.SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
                    android.content.SharedPreferences.class.getClassLoader(),new Class<?>[]{android.content.SharedPreferences.class},(proxy,method,args)->{
                        if(method.getName().equals("getBoolean"))return false;
                        if(method.getName().equals("getFloat")){
                            switch((String)args[0]){
                                case "aod_brightness":return 1f;
                                case "aod_offset_x":return photo.aodOffsetX;
                                case "aod_offset_y":return photo.aodOffsetY;
                                case "dynamic_aod_x":return render.aodOffsetX;
                                case "dynamic_aod_y":return render.aodOffsetY;
                            }
                        }
                        return method.invoke(original,args);
                    });
            }
        };
        worker=new EglWorker(c,true);
        worker.options(render);
        worker.photoOptions(photo);worker.state(new EngineRegistry.Snapshot(0,0,0,0));
        reader=ImageReader.newInstance(W,H,PixelFormat.RGBA_8888,3);
        reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){
            if(image==null)return;Image.Plane plane=image.getPlanes()[0];ByteBuffer data=plane.getBuffer();long total=0,sum=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int i=y*plane.getRowStride()+x*plane.getPixelStride();int light=(data.get(i)&255)+(data.get(i+1)&255)+(data.get(i+2)&255);
                if(light>36){sum+=(long)light*y;total+=light;}
            }
            center=total==0?-1:sum/(float)total/H;frames++;
        }},main);
        worker.attach(reader.getSurface(),W,H,variant,false);worker.visible(true);main.postDelayed(this::sample,2400);
    }
    void sample(){try{
        if(!worker.error.isEmpty()||frames==0||center<0)throw new AssertionError("Missing frame: "+worker.error);
        centers[step]=center;
        System.out.println("PLACEMENT_SAMPLE variant="+variant+" step="+step+" centerY="+center+" frames="+frames);
        if(++step==6){
            if(Boolean.getBoolean("alive.test.placement.assert")){
                if(variant>=601&&variant<=606&&centers[0]>.55f)throw new AssertionError("Cold AOD starts in lower half");
                if(Math.abs(centers[0]-centers[1])>.025f)throw new AssertionError("Reconfigure moved initial AOD");
                if(Math.abs(centers[2]-centers[3])>.025f)throw new AssertionError("Rebind lost placement");
                if(centers[4]-centers[2]<.25f)throw new AssertionError("Upper/lower placement range ineffective");
                if(Math.abs(centers[4]-centers[5])>.025f)throw new AssertionError("Applied live placement differs from preview");
            }
            System.out.println("PLACEMENT_WORKER_PASS variant="+variant+" centers="+java.util.Arrays.toString(centers));
            worker.close();main.postDelayed(()->{reader.close();System.exit(0);},300);return;
        }
        if(step==1){worker.photoOptions(photo);worker.options(render);}
        if(step==2){photo=photo.edit(9,-.04f);render=render.position(0,-.2f);worker.photoOptions(photo);worker.options(render);}
        if(step==3)worker.attach(reader.getSurface(),W,H,variant,false);
        if(step==4){photo=photo.edit(9,.3f);render=render.position(0,.3f);worker.photoOptions(photo);worker.options(render);}
        if(step==5){
            // Drain the old EGL owner before a separate non-preview worker binds this surface.
            java.lang.reflect.Field field=EglWorker.class.getDeclaredField("handler");field.setAccessible(true);
            Handler gl=(Handler)field.get(worker);
            gl.post(()->{worker.close();gl.post(()->main.post(()->{
                frames=0;center=-1;
                worker=new EglWorker(context,false);worker.state(new EngineRegistry.Snapshot(0,0,0,0));
                worker.attach(reader.getSurface(),W,H,variant,false);worker.visible(true);
                main.postDelayed(this::sample,1800);
            }));});return;
        }
        main.postDelayed(this::sample,1300);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
    public static void main(String[] args)throws Exception{Looper.prepareMainLooper();new PlacementWorkerProbe(Integer.parseInt(args[0])).start();Looper.loop();}
}
