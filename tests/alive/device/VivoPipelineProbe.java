package com.frederic22.hyperextreme.alive;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

/** Tests the unchanged Vivo runtime without installing it or writing to the installed app's storage. */
public final class VivoPipelineProbe {
    private static final File OUT=new File(System.getProperty("alive.test.out","/data/local/tmp/hyperextreme-vivo-test"));
    private static final int W=360,H=800;
    private final int family,style;
    private final String fixture;
    private final HandlerThread thread=new HandlerThread("VivoProbeGL");
    private Handler gl;
    private VivoRenderer renderer;
    private int phase,frames,checks;
    private long videoTimestamp=-1;private int videoFrames,videoLoops;
    private long start;
    private boolean ended;
    private final int[] modes={0,1,2,0,1,0};
    private final long[] lights=new long[modes.length];
    private final long[] hashes=new long[modes.length];
    private android.hardware.SensorEvent simulated;
    private volatile android.hardware.SensorEventListener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Object animatorClock;
    private java.lang.reflect.Method animatorFrame;
    private Object springClock;
    private java.lang.reflect.Method springFrame;
    private static Object field(Object object,String name)throws Exception{
        java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private final ByteBuffer pixels=ByteBuffer.allocateDirect(W*H*4);
    VivoPipelineProbe(int family,int style,String fixture){this.family=family;this.style=style;this.fixture=fixture;}
    void check(boolean test,String reason){checks++;if(!test)throw new AssertionError(reason);}
    File dir(String name){File f=new File(OUT,name);f.mkdirs();return f;}
    void start()throws Exception {
        // The phone may remain in Doze. Drive only this probe process's animator clock;
        // never wake the physical display just to exercise an offscreen lockscreen scene.
        Class<?> animation=Class.forName("android.animation.AnimationHandler");
        animatorClock=animation.getDeclaredMethod("getInstance").invoke(null);
        animatorFrame=animation.getDeclaredMethod("doAnimationFrame",long.class);animatorFrame.setAccessible(true);
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context assets=ProbeContext.wrap(base.createPackageContext(Protocol.PACKAGE,Context.CONTEXT_IGNORE_SECURITY));
        Context isolated=new ContextWrapper(assets){
            public Context getApplicationContext(){return this;}
            public File getFilesDir(){return dir("files");}
            public File getCacheDir(){return dir("cache");}
            public File getCodeCacheDir(){return dir("code-cache");}
            public File getDir(String name,int mode){if(!name.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException();return dir("dir-"+name);}
            public FileOutputStream openFileOutput(String name,int mode)throws FileNotFoundException {
                if(!name.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException();return new FileOutputStream(new File(getFilesDir(),name));
            }
            public boolean deleteFile(String name){if(!name.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException();return new File(getFilesDir(),name).delete();}
            public ApplicationInfo getApplicationInfo(){ApplicationInfo info=new ApplicationInfo(super.getApplicationInfo());info.dataDir=OUT.getPath();return info;}
        };
        thread.start();gl=new Handler(thread.getLooper());gl.post(()->{
            try {
                EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);check(EGL14.eglInitialize(display,new int[2],0,new int[2],0),"initialize");
                EGLConfig config=CosmicEgl.choose(display,EGL14.EGL_PBUFFER_BIT);
                EGLContext context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
                EGLSurface surface=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
                check(EGL14.eglMakeCurrent(display,surface,surface,context),"current");
                VivoOptions options=fixture(isolated);options.style=style;
                renderer=new VivoRenderer(isolated,options,W,H,0,false,false,()->{});
                Class<?> spring=VivoEngineRuntime.prepare(isolated).get().originalClass("androidx.dynamicanimation.animation.AnimationHandler");
                java.lang.reflect.Method getSpring=spring.getDeclaredMethod("getInstance");getSpring.setAccessible(true);
                springFrame=spring.getDeclaredMethod("doAnimationFrame",long.class);springFrame.setAccessible(true);
                java.util.concurrent.FutureTask<Void> setupClock=new java.util.concurrent.FutureTask<>(()->{springClock=getSpring.invoke(null);return null;});
                main.post(setupClock);setupClock.get(3,java.util.concurrent.TimeUnit.SECONDS);
                renderer.visible(true);start=System.nanoTime();draw();
            }catch(Throwable e){fail(e);}
        });
    }
    void draw(){
        if(ended)return;
        try {
            main.post(()->{try{long now=SystemClock.uptimeMillis();animatorFrame.invoke(animatorClock,now);springFrame.invoke(springClock,now);}catch(Exception e){fail(e);}});
            if(phase==4){
                if(family==1)renderer.touch(frames%20==0?0:frames%20==19?1:2,.3f+(frames%20)*.02f,.6f);
                else main.post(()->{
                    android.hardware.SensorEventListener current=listener;
                    if(current!=null){simulated.timestamp=SystemClock.elapsedRealtimeNanos();simulated.values[0]=.9f;simulated.values[1]=1.2f;simulated.values[2]=.35f;current.onSensorChanged(simulated);}
                });
            }
            renderer.motion().advance(System.nanoTime(),1);renderer.render();frames++;
            check(GLES30.glGetError()==0,"GL phase "+phase);
            if(fixture.equals("video")){
                Object layer=field(field(renderer,"scene"),"layer");
                java.util.List<?> clips=(java.util.List<?>)field(layer,"clips");check(clips.size()==1,"one selected clip decoder");
                android.graphics.SurfaceTexture stream=(android.graphics.SurfaceTexture)field(clips.get(0),"stream");
                long stamp=stream.getTimestamp();
                if(stamp!=videoTimestamp){if(stamp<videoTimestamp)videoLoops++;videoTimestamp=stamp;videoFrames++;}
            }
            if(System.nanoTime()-start<1_700_000_000L){gl.postDelayed(this::draw,25);return;}
            pixels.clear();GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
            int[] argb=new int[W*H];int nonBlack=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int i=((H-1-y)*W+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
                lights[phase]+=r+g+b;if(r+g+b>15)nonBlack++;
                argb[y*W+x]=0xff000000|(r<<16)|(g<<8)|b;
            }
            check(nonBlack>W*H/3,"nonblack output phase="+phase+" pixels="+nonBlack);
            java.util.zip.CRC32 crc=new java.util.zip.CRC32();for(int pixel:argb){crc.update(pixel>>16);crc.update(pixel>>8);crc.update(pixel);}hashes[phase]=crc.getValue();
            Bitmap bitmap=Bitmap.createBitmap(argb,W,H,Bitmap.Config.ARGB_8888);
            try(FileOutputStream out=new FileOutputStream(new File(OUT,family+"-"+style+"-"+fixture+"-p"+phase+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
            if(fixture.equals("video")){
                Object scene=field(renderer,"scene"),layer=field(scene,"layer");
                java.util.List<?> clips=(java.util.List<?>)field(layer,"clips");check(clips.size()==1,"one selected clip decoder");
                android.graphics.SurfaceTexture stream=(android.graphics.SurfaceTexture)field(clips.get(0),"stream");
                check(stream.getTimestamp()>0,"video actually submitted a decoded frame");
            }
            if(++phase==modes.length){
                check(lights[0]<lights[1]*.85,"AOD must be dimmer than lock");
                check(hashes[1]!=hashes[4],"private touch/gyro stimulus must change the image");
                if(fixture.equals("video")){check(videoFrames>60,"continuous decoded frames");check(videoLoops>=2,"clip loops past EOS");}
                listener=null;
                renderer.visible(false);
                gl.postDelayed(()->{
                    try{
                        java.lang.reflect.Field f=VivoRenderer.class.getDeclaredField("scene");f.setAccessible(true);Object scene=f.get(renderer);
                        f=VivoEngineScene.class.getDeclaredField("running");f.setAccessible(true);check(!f.getBoolean(scene),"hidden controller stops");
                        Object layer=field(scene,"layer");for(Object clip:(java.util.List<?>)field(layer,"clips")){
                            check(!(Boolean)field(clip,"playing"),"hidden video decoder stops");
                            check(field(field(clip,"decoder"),"codec")==null,"hidden hardware codec released");
                        }
                        if(fixture.equals("video"))System.out.println("VIVO_CLIP_PASS frames="+videoFrames+" loops="+videoLoops+" hidden_codec=released");
                        renderer.close();ended=true;System.out.println("VIVO_PASS family="+family+" style="+style+" fixture="+fixture+" checks="+checks+" frames="+frames+" luminance="+java.util.Arrays.toString(lights));System.exit(0);
                    }catch(Throwable e){fail(e);}
                },300);return;
            }
            renderer.motion().change(modes[phase],true);start=System.nanoTime();renderer.motion().advance(start,1);
            if(phase==4){
                if(family==1)renderer.touch(0,.3f,.6f);
                else {
                    Object scene=field(renderer,"scene");VivoSensorGain gain=(VivoSensorGain)field(scene,"sensorGain");
                    java.lang.reflect.Constructor<android.hardware.SensorEvent> ctor=android.hardware.SensorEvent.class.getDeclaredConstructor(int.class);ctor.setAccessible(true);
                    simulated=ctor.newInstance(3);simulated.sensor=(android.hardware.Sensor)field(gain,"gyro");
                    // Inject only into this isolated engine, never into Android's sensor service.
                    main.post(()->{gain.close();try{listener=(android.hardware.SensorEventListener)field(gain,"original");}catch(Exception e){fail(e);}});
                }
            }
            gl.postDelayed(this::draw,25);
        }catch(Throwable e){fail(e);}
    }
    private VivoOptions fixture(Context c)throws Exception {
        VivoOptions options=new VivoOptions(family);
        if(fixture.equals("factory"))return options;
        options.area=4;
        for(int i=0;i<(family==2?3:1);i++){
            String name="vivo-test-slot-"+i+".png";
            Bitmap bitmap=Bitmap.createBitmap(480,960,Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);android.graphics.Paint paint=new android.graphics.Paint();
            canvas.drawColor(new int[]{0xffac4c36,0xff277d67,0xff357ca7}[i]);
            paint.setColor(0xffe8d7a1);for(int y=0;y<960;y+=100)canvas.drawRect(0,y,480,y+25,paint);
            paint.setColor(0xff30363c);canvas.drawCircle(240,600,100,paint);
            try(FileOutputStream out=c.openFileOutput(name,0)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
            options.photos.add(name);options.rasterVideos.add("");
        }
        if(family==2){options.slots[1].rotation=1;options.slots[2].zoom=1.4f;options.slots[2].x=.7f;}
        if(fixture.equals("video")){
            File clip=new File(c.getFilesDir(),"vivo-test-clip.mp4");
            try(VivoVideoMetadata metadata=VivoVideoMetadata.read(clip)){
                check(metadata.duration==2000&&metadata.rate==30&&metadata.frames==60,"fixture metadata "+metadata.duration+"/"+metadata.rate+"/"+metadata.frames);
                check(metadata.poster.getWidth()==320&&metadata.poster.getHeight()==640,"bounded decoded poster");
                try(FileOutputStream out=new FileOutputStream(new File(OUT,"2-1-video-poster.png"))){metadata.poster.compress(Bitmap.CompressFormat.PNG,100,out);}
            }
            VivoMediaSelection selection=new VivoMediaSelection(c,options);
            selection.importUris(VivoEngineRuntime.prepare(c).get(),VivoMediaSelection.SLOT_VIDEO+1,
                java.util.Collections.singletonList(android.net.Uri.fromFile(new File(c.getFilesDir(),"vivo-test-clip.mp4"))));
            options=selection.result;options.validateFiles(c);
        }
        return options;
    }
    private void fail(Throwable e){ended=true;e.printStackTrace();System.exit(1);}
    public static void main(String[] args)throws Exception{Looper.prepareMainLooper();new VivoPipelineProbe(Integer.parseInt(args[0]),Integer.parseInt(args[1]),args.length>2?args[2]:"factory").start();Looper.loop();}
}
