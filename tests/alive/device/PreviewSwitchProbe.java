package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** One preview worker/surface across renderer families. Isolated assets; no install or apply. */
public final class PreviewSwitchProbe {
    private static final int W=288,H=640;
    private static final int[] IDS={601,1,101,201,301,401,501,701,702,703,606,601};
    private static final int[] MODES={0,0,0,0,1,1,2,1,0,2,0,2};
    private final Handler main=new Handler(Looper.getMainLooper());
    private EglWorker worker;
    private ImageReader reader;
    private long received,light,before;
    private long expectedGeneration;
    private int step;
    private static Object field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private void start()throws Exception {
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context context=ProbeContext.vivo(base,"/data/local/tmp/hyperextreme-vivo-preview-switch-test");
        File source=new File(context.getFilesDir(),"alive/photo-draft.png");source.getParentFile().mkdirs();
        Bitmap image=Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);
        canvas.drawColor(0xff448888);Paint paint=new Paint();paint.setColor(0xffdd9944);canvas.drawCircle(150,240,100,paint);
        try(FileOutputStream out=new FileOutputStream(source)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        worker=new EglWorker(context,true);
        reader=ImageReader.newInstance(W,H,PixelFormat.RGBA_8888,3);
        reader.setOnImageAvailableListener(r->{try(Image frame=r.acquireLatestImage()){
            if(frame==null)return;Image.Plane plane=frame.getPlanes()[0];ByteBuffer data=plane.getBuffer();long sum=0;
            for(int y=0;y<H;y+=4)for(int x=0;x<W;x+=4){int i=y*plane.getRowStride()+x*plane.getPixelStride();sum+=(data.get(i)&255)+(data.get(i+1)&255)+(data.get(i+2)&255);}
            light=sum;received++;
        }},main);
        next();main.postDelayed(()->fail("timeout"),150000);
    }
    private void next(){try{
        if(step==IDS.length){
            worker.close();main.postDelayed(()->{try{
                Field users=EglWorker.class.getDeclaredField("displayUsers");users.setAccessible(true);
                if(users.getInt(null)!=0)fail("EGL display leak");
                reader.close();System.out.println("PREVIEW_SWITCH_PASS selections="+IDS.length+" same_surface=true all_modes=true egl_released=true");System.exit(0);
            }catch(Throwable e){fail(e.toString());}},1000);return;
        }
        int id=IDS[step],mode=MODES[step];before=received;
        worker.state(new EngineRegistry.Snapshot(mode,mode,SystemClock.uptimeMillis(),step+1));
        worker.options(new AliveRenderOptions(false,false,false,false,false,false,0,1.3f));
        if(id>=701)worker.vivoOptions(new VivoOptions(id-700));
        if(id>=601&&id<=606)worker.photoOptions(PhotoOptions.defaults().edit(3,1.4f));
        expectedGeneration=worker.attach(reader.getSurface(),W,H,id,false);
        if(worker.renderedGeneration==expectedGeneration)fail("New generation reported before attach rendered");
        worker.visible(true);
        main.postDelayed(this::verify,5000);
    }catch(Throwable e){fail(e.toString());}}
    private void verify(){try{
        if(received<=before||light==0||!worker.error.isEmpty())fail("no frame at "+IDS[step]+": "+worker.error);
        if(worker.renderedGeneration!=expectedGeneration)fail("Stale frame generation at "+IDS[step]);
        ((Handler)field(worker,"handler")).post(()->{try{
            int id=IDS[step];Object renderer=field(worker,"renderer");
            String expected=id>=701?"VivoRenderer":id>=601?"PhotoRenderer":id>=501?"StereoRenderer":id>=401?"FlymeVideoRenderer":id>=301?"GradientRenderer":id>=201?"BubbleRenderer":"CosmicRenderer";
            if(!renderer.getClass().getSimpleName().equals(expected))fail("wrong renderer for "+id);
            if(id>=701&&((VivoOptions)field(renderer,"options")).family!=id-700)fail("Vivo family leaked");
            if(id>=601&&id<=606&&((PhotoOptions)field(renderer,"options")).zoom!=1.4f)fail("photo crop lost");
            if(((EngineRegistry.Snapshot)field(worker,"state")).mode!=MODES[step])fail("preview mode lost");
            System.out.println("PREVIEW_SELECTION id="+id+" mode="+MODES[step]+" renderer="+expected+" generation="+expectedGeneration+" received="+(received-before));
            main.post(()->{step++;next();});
        }catch(Throwable e){fail(e.toString());}});
    }catch(Throwable e){fail(e.toString());}}
    private static void fail(String reason){System.err.println(reason);System.exit(1);}
    public static void main(String[] args)throws Exception{Looper.prepareMainLooper();new PreviewSwitchProbe().start();Looper.loop();}
}
