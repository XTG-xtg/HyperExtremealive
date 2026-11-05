package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.*;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/** Isolated GPU regression. No services, preferences, wallpaper binding or user's pictures. */
public final class PhotoPipelineProbe {
    private static final int W=360,H=800;
    private static final File OUT=new File("/data/local/tmp/hyperextreme-photo-test");
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final int variant;
    private final boolean landscape;
    private PhotoRenderer renderer;
    private android.content.res.AssetManager assets;
    private File source;
    private int phase,frame,checks,waits,presented;
    private long now=1_000_000_000L;
    private final Set<Integer> intermediate=new HashSet<>();
    private final int[] modes={0,1,2,0,2,1,0};
    private byte[] locked,home;
    private long waitStarted;
    private boolean pending;
    private final ByteBuffer pixels=ByteBuffer.allocateDirect(W*H*4);

    PhotoPipelineProbe(int variant,boolean landscape){this.variant=variant;this.landscape=landscape;}
    static Object field(Object owner,String name)throws Exception{
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    void start()throws Exception {
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context system=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context app=system.createPackageContext(Protocol.PACKAGE,Context.CONTEXT_IGNORE_SECURITY);
        assets=app.getAssets();
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        check(EGL14.eglInitialize(display,new int[2],0,new int[2],0),"EGL initialize");
        EGLConfig config=CosmicEgl.choose(display,EGL14.EGL_PBUFFER_BIT);
        EGLContext context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        EGLSurface surface=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
        check(EGL14.eglMakeCurrent(display,surface,surface,context),"EGL current");
        OUT.mkdirs();File image=new File(OUT,landscape?"landscape.png":"portrait.png");source=image;
        int w=landscape?640:320,h=landscape?320:640;
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint p=new Paint();
        p.setColor(0xffdb6548);canvas.drawRect(0,0,w,h/2f,p);
        p.setColor(0xff357dae);canvas.drawRect(0,h/2f,w,h,p);
        p.setColor(0xfff4dd8c);canvas.drawRect(w*.48f,0,w*.52f,h,p);
        p.setColor(0xff224640);p.setStrokeWidth(3);
        for(int x=0;x<w;x+=40)canvas.drawLine(x,0,x,h,p);
        for(int y=0;y<h;y+=40)canvas.drawLine(0,y,w,y,p);
        p.setColor(Color.WHITE);canvas.drawCircle(w*.5f,h*.2f,18,p);canvas.drawRect(w*.45f,h*.75f,w*.55f,h*.8f,p);
        try(FileOutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        renderer=new PhotoRenderer(app.getAssets(),image,W,H,0,variant,()->handler.post(this::step));
        timelineRegression();
        renderer.motion().advance(now,1);
        handler.post(this::step);
    }

    void timelineRegression() throws Exception {
        SceneMotion anchored=new SceneMotion(W,H,0,0,0,6,false);
        FrameMotion positioned=new FrameMotion(W,H,0,true);
        check(Math.abs(anchored.values[9]-.335f)<.0001f,"default lens anchor preserved");
        check(Math.abs(positioned.values[5]-.315f)<.0001f,"default frame anchor preserved");
        anchored.followClock(.1f,.235f);positioned.position(.1f,.215f);
        check(Math.abs(anchored.values[8]-.1f)<.0001f&&Math.abs(anchored.values[9]-.235f)<.0001f,"lens position edits immediately");
        check(Math.abs(positioned.values[4]-.1f)<.0001f&&Math.abs(positioned.values[5]-.215f)<.0001f,"frame position edits immediately");
        positioned.change(1,anchored);anchored.change(1);positioned.finish();anchored.finish();
        check(anchored.values[8]==0&&positioned.values[4]==0,"lock endpoint unaffected by AOD anchor");
        positioned.change(0,anchored);anchored.change(0);positioned.finish();anchored.finish();
        check(Math.abs(anchored.values[9]-.235f)<.0001f&&Math.abs(positioned.values[5]-.215f)<.0001f,"sleep returns to edited anchors");
        PhotoOptions clamped=new PhotoOptions(0,.5f,.5f,1,.5f,.5f,1,0,Float.NaN,99);
        check(clamped.aodOffsetX==0&&clamped.aodOffsetY==.4f,"bounded finite anchors");
        anchored.close();positioned.close();
        SceneMotion early=new SceneMotion(W,H,0,0,2,6,false),late=new SceneMotion(W,H,0,0,2,6,false);
        early.change(2);early.advance(now);late.change(2);late.advance(now);
        for(int i=1;i<=30;i++)early.advance(now+i*16_666_667L);
        late.advance(now+30*16_666_667L);
        // Rotation includes continuous integration; all geometric transition tracks must seek identically.
        for(int i:new int[]{0,1,2,3,4,5,6,7,8,9,11})check(Math.abs(early.values[i]-late.values[i])<.0001f,"late slot seek "+i);
        FrameMotion f=new FrameMotion(W,H,0,true);SceneMotion common=new SceneMotion(W,H,0,-1,2,6,false);
        f.change(1,common);f.advance(now);f.advance(now+300_000_000L);
        float before=f.values[6];f.change(2,common);f.advance(now+400_000_000L);
        check(f.values[6]>before,"paired unlock must continue expansion");
        f.advance(now+2_000_000_000L);check(f.expanded(),"frame endpoint");
        f.close();early.close();late.close();common.close();
        HistoryMotion first=new HistoryMotion(),second=new HistoryMotion();
        MotionSession running=new MotionSession(first),rebound=new MotionSession(second);
        running.frame(1,1,now,now,()->{});
        for(int i=1;i<=15;i++)running.frame(1,1,now,now+i*16_666_667L,()->{});
        running.frame(2,2,now+300_000_000L,now+300_000_000L,()->{});
        running.frame(2,2,now+300_000_000L,now+450_000_000L,()->{});
        rebound.frame(1,1,now,now,()->{});
        rebound.frame(2,2,now+300_000_000L,now+300_000_000L,()->{});
        rebound.frame(2,2,now+300_000_000L,now+450_000_000L,()->{});
        for(int i=0;i<8;i++)check(Math.abs(first.frame.values[i]-second.frame.values[i])<.0001f,"rebound history geometry "+i);
        first.close();second.close();
    }
    private static final class HistoryMotion implements WallpaperMotion {
        final SceneMotion scene=new SceneMotion(W,H,0,-1,2,6,false);
        final FrameMotion frame=new FrameMotion(W,H,0,true);
        public void change(int mode,boolean animate){frame.change(mode,scene);scene.change(mode);if(!animate){frame.finish();scene.finish();}}
        public void advance(long time,float speed){frame.advance(time);scene.advance(time,speed);}
        public void follow(float x,float y){}
        public void pause(){}
        public boolean active(){return frame.active();}
        void close(){frame.close();scene.close();}
    }

    void step() {
        try {
            if(renderer==null)return;
            if(!pending){renderer.motion().advance(now,1);waitStarted=SystemClock.uptimeMillis();}
            FrameSample sample=(FrameSample)field(renderer,"sample");
            byte[] frozen=null;
            if(sample.pending){frozen=new byte[sample.base.capacity()];ByteBuffer copy=sample.base.duplicate();copy.clear();copy.get(frozen);}
            renderer.render();
            check(GLES30.glGetError()==GLES30.GL_NO_ERROR,"GL phase="+phase+" frame="+frame);
            if(!renderer.frameReady()) {
                waits++;
                check(SystemClock.uptimeMillis()-waitStarted<5000,"mask timeout");
                if(frozen!=null){byte[] after=new byte[frozen.length];ByteBuffer copy=sample.base.duplicate();copy.clear();copy.get(after);check(java.util.Arrays.equals(frozen,after),"pending geometry changed");}
                if(!pending){renderer.motion().advance(now+5_000_000L,1);}
                pending=true;handler.removeCallbacksAndMessages(null);handler.postDelayed(this::step,4);return;
            }
            pending=false;presented++;
            if(variant==602&&phase==0&&frame==60) {
                byte[] before=read();renderer.configure(new PhotoOptions(2,.5f,.5f,1,.25f,.4f,.5f,20));renderer.render();
                check(renderer.frameReady(),"cached frame crop sample");byte[] after=read();long difference=0;
                for(int i=0;i<before.length;i++)difference+=Math.abs((before[i]&255)-(after[i]&255));
                check(difference>100000,"frame crop must affect both paper photos");
                save("frame-crop-"+landscape+".png",after);
                renderer.configure(new PhotoOptions(2,.5f,.5f,1,.5f,.5f,1,0));renderer.render();
            }
            if(variant==602){int mask=(Integer)field(field(renderer,"masks"),"uploaded");if(mask>=0&&mask<61)intermediate.add(mask);}
            if(frame==0||frame==12||frame==24||frame==48||frame==120||frame==270) {
                byte[] data=read();save(variant+"-"+(landscape?"land":"port")+"-p"+phase+"-f"+frame+".png",data);
                if(phase==1&&frame==270)locked=data;
                if(phase==2&&frame==270)home=data;
            }
            frame++;now+=16_666_667L;
            if(frame>270) {
                phase++;frame=0;
                if(phase==modes.length){finish();return;}
                renderer.motion().change(modes[phase],true);
            }
            handler.removeCallbacksAndMessages(null);handler.post(this::step);
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    byte[] read(){pixels.clear();GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);byte[] data=new byte[pixels.capacity()];pixels.get(data);return data;}
    void save(String name,byte[] data)throws IOException {
        int[] colors=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;colors[(H-y-1)*W+x]=0xff000000|((data[i]&255)<<16)|((data[i+1]&255)<<8)|(data[i+2]&255);}
        Bitmap image=Bitmap.createBitmap(colors,W,H,Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(new File(OUT,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    void finish()throws Exception {
        long difference=0;for(int i=0;i<home.length;i++)difference+=Math.abs((home[i]&255)-(locked[i]&255));
        check(difference>100000,"lock/home must not be the same composition");
        // Readback is bottom-up: the blue lower half must stay below the red upper half.
        long upper=0,lower=0;
        for(int y=H/8;y<H*3/8;y++)for(int x=W/8;x<W*3/8;x++){
            int a=(y*W+x)*4,b=((H-y-1)*W+x)*4;
            lower+=(home[a+2]&255)-(home[a]&255);upper+=(home[b]&255)-(home[b+2]&255);
        }
        check(upper>100000,"home upper red/orientation");
        check(lower>100000,"home lower blue/orientation");
        if(variant==602)check(intermediate.size()>=20,"intermediate masks starved: "+intermediate);
        renderer.close();renderer=null;
        cropRegression();
        System.out.println("PHOTO_PASS variant="+variant+" landscape="+landscape+" checks="+checks+" frames="+presented+" waits="+waits+" intermediate="+intermediate.size()+" lockHomeMAE="+(difference/(double)home.length));
        System.exit(0);
    }
    void cropRegression()throws Exception {
        PhotoRenderer crop=new PhotoRenderer(assets,source,W,H,2,601,()->{});
        try {
            crop.configure(PhotoOptions.defaults());crop.render();byte[] base=read();
            crop.configure(new PhotoOptions(0,0,0,2,.5f,.5f,1,0));crop.render();byte[] first=read();
            save("crop-"+landscape+"-top.png",first);
            crop.configure(new PhotoOptions(0,1,1,2,.5f,.5f,1,0));crop.render();byte[] second=read();
            save("crop-"+landscape+"-bottom.png",second);
            long difference=0,changed=0;int black=0;
            for(int i=0;i<first.length;i+=4){
                for(int channel=0;channel<3;channel++){
                    difference+=Math.abs((first[i+channel]&255)-(second[i+channel]&255));
                    changed+=Math.abs((first[i+channel]&255)-(base[i+channel]&255));
                }
                if((first[i]&255)+(first[i+1]&255)+(first[i+2]&255)<3)black++;
            }
            check(difference>1000000&&changed>1000000,"crop controls must change sampling");
            check(black==0,"crop edges must stay inside image");
            crop.configure(new PhotoOptions(1,.5f,.5f,1,.5f,.5f,1,0));
            crop.motion().change(1,false);crop.render();
            check(crop.frameReady()&&GLES30.glGetError()==0,"striped lock shader");
            PhotoOptions invalid=new PhotoOptions(-1,Float.NaN,Float.POSITIVE_INFINITY,0,-4,7,0,80);
            check(invalid.lockStyle==0&&invalid.x==.5f&&invalid.y==.5f&&invalid.zoom==1&&invalid.frameSize==.25f&&invalid.frameAngle==45,"crop validation");
            check(GLES30.glGetError()==0,"crop GL state");
        } finally {crop.close();}
    }
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();new PhotoPipelineProbe(Integer.parseInt(args[0]),Boolean.parseBoolean(args[1])).start();Looper.loop();
    }
}
