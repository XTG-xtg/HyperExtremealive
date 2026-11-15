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
    private int[] lockPixels;
    private float touchX=.3f,touchY=.6f;
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
                if(fixture.equals("flash-matrix")||fixture.equals("flash-covers")){flashMatrix(isolated);return;}
                VivoOptions options=fixture(isolated);options.style=style;
                renderer=new VivoRenderer(isolated,options,W,H,0,false,false,()->{});
                if(family==1){
                    LiquidTouchMap map=renderer.touchMap();boolean found=false;
                    for(float y=.35f;y<.8f&&!found;y+=.025f)for(float x=.2f;x<.8f;x+=.025f)if(map.hit(x,y)){touchX=x;touchY=y;found=true;break;}
                    check(found,"fixture contains an interactive selected point");
                }
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
                if(family==1)renderer.touch(frames%8==0?0:frames%8==7?1:2,touchX,touchY);
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
            if(phase==1)lockPixels=argb.clone();
            if(phase==4&&fixture.equals("region")){
                int inside=0,outside=0;
                for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(argb[y*W+x]!=lockPixels[y*W+x]){
                    if(x>W*.6f||y<H*.1f||y>H*.9f)outside++;else inside++;
                }
                check(inside>50,"selected textured pixels actually animate: "+inside);
                check(outside==0,"unselected pixels stay identical: "+outside);
                System.out.println("LIQUID_REGION_PASS inside_changed="+inside+" outside_changed="+outside);
            }
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
                if(family==1)renderer.touch(0,touchX,touchY);
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
        if(fixture.equals("region")){
            options.area=1;options.wallpaperZoom=false;options.paint="vivo-test-region.png";
            Bitmap mask=Bitmap.createBitmap(480,960,Bitmap.Config.ARGB_8888);mask.eraseColor(android.graphics.Color.BLACK);
            android.graphics.Paint brush=new android.graphics.Paint();brush.setColor(android.graphics.Color.WHITE);
            new android.graphics.Canvas(mask).drawRect(0,144,240,816,brush);
            try(FileOutputStream out=c.openFileOutput(options.paint,0)){mask.compress(Bitmap.CompressFormat.PNG,100,out);}finally{mask.recycle();}
        }
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
    private void flashMatrix(Context context)throws Exception {
        boolean covers=fixture.equals("flash-covers");
        VivoOptions base=covers?new VivoOptions(3):fixture(context);base.wallpaperZoom=false;
        base.subject="vivo-matrix-subject.png";base.paint="vivo-matrix-paint.png";
        for(String name:new String[]{base.subject,base.paint}){
            Bitmap mask=Bitmap.createBitmap(480,960,Bitmap.Config.ARGB_8888);mask.eraseColor(0xff000000);
            android.graphics.Paint brush=new android.graphics.Paint();brush.setColor(0xffffffff);
            new android.graphics.Canvas(mask).drawRect(name.equals(base.subject)?0:240,0,name.equals(base.subject)?240:480,960,brush);
            try(FileOutputStream out=context.openFileOutput(name,0)){mask.compress(Bitmap.CompressFormat.PNG,100,out);}finally{mask.recycle();}
        }
        VivoCalls api=new VivoCalls(VivoEngineRuntime.prepare(context).get());
        for(int style=1;style<=5;style++)for(int area=covers?4:1;area<=4;area++)for(int pattern=0;pattern<(style==3&&!covers?2:1);pattern++){
            VivoOptions options=VivoOptions.parse(base.json());options.style=style;options.area=area;options.pattern=pattern==0?"d5_pattern_circle":"d5_pattern_star";
            renderer=new VivoRenderer(context,options,W,H,1,false,false,()->{});
            Object scene=field(renderer,"scene"),ctrl=field(scene,"controller"),anim=api.get(ctrl,"f");
            java.util.concurrent.FutureTask<Void> stop=new java.util.concurrent.FutureTask<>(()->{api.call(api.get(ctrl,"e"),"h");api.call(anim,"f");return null;});
            main.post(stop);stop.get(3,java.util.concurrent.TimeUnit.SECONDS);
            api.set(scene,"flashReady",true);api.set(anim,"D",api.type("n3.f").getField("a").get(null));api.set(anim,"I",1f);
            // Style 2 includes a native 950 ms startup fade and velocity integration.
            // A pair of immediate offscreen draws cannot exercise that contract.
            for(int f=0;f<65;f++){renderer.motion().advance(System.nanoTime(),1);renderer.render();Thread.sleep(16);}
            int[] baseline=null;long maxDifference=0;
            float[][] angles={{0,0},{-.6f,-.6f},{.6f,.6f},{-.6f,.6f},{.6f,-.6f}};
            for(int a=0;a<angles.length;a++){
                float fromX=(Float)api.get(anim,"n"),fromY=(Float)api.get(anim,"o");
                for(int f=1;f<=20;f++){
                    api.set(anim,"n",fromX+(angles[a][0]-fromX)*f/20);api.set(anim,"o",fromY+(angles[a][1]-fromY)*f/20);api.set(anim,"I",a==0?0f:1f);
                    renderer.motion().advance(System.nanoTime(),1);renderer.render();Thread.sleep(16);
                }
                check(GLES30.glGetError()==0,"flash matrix GL");pixels.clear();GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
                int[] argb=new int[W*H];long delta=0;int changed=0;
                for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                    int i=((H-1-y)*W+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
                    int pos=y*W+x;argb[pos]=0xff000000|r<<16|g<<8|b;
                    if(baseline!=null){int old=baseline[pos];int d=Math.abs(r-((old>>16)&255))+Math.abs(g-((old>>8)&255))+Math.abs(b-(old&255));delta+=d;if(d>6)changed++;}
                }
                if(a==0)baseline=argb;
                maxDifference=Math.max(maxDifference,delta);
                Bitmap bitmap=Bitmap.createBitmap(argb,W,H,Bitmap.Config.ARGB_8888);
                try(FileOutputStream out=new FileOutputStream(new File(OUT,(covers?"cover-":"matrix-")+style+"-"+area+"-"+pattern+"-"+a+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
                System.out.println("FLASH_MATRIX style="+style+" area="+area+" pattern="+pattern+" angle="+a+" delta="+delta+" changed="+changed+" shader="+api.get(api.get(api.get(ctrl,"c"),"i"),"f").getClass().getName());
            }
            check(maxDifference>(style==2?10000:100000),"flash style "+style+" area "+area+" visibly changes");renderer.close();renderer=null;
        }
        System.out.println("FLASH_MATRIX_PASS checks="+checks);System.exit(0);
    }
    private void fail(Throwable e){ended=true;e.printStackTrace();System.exit(1);}
    public static void main(String[] args)throws Exception{Looper.prepareMainLooper();new VivoPipelineProbe(Integer.parseInt(args[0]),Integer.parseInt(args[1]),args.length>2?args[2]:"factory").start();Looper.loop();}
}
