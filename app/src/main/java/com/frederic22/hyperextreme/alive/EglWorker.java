package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.opengl.*;
import android.os.*;
import android.util.Log;
import android.view.Surface;
import android.view.Choreographer;
import java.util.concurrent.atomic.AtomicLong;

/** Owns EGL and all renderer calls on one Looper; surface callbacks never touch GL. */
final class EglWorker {
    private static EGLDisplay sharedDisplay = EGL14.EGL_NO_DISPLAY;
    private static int displayUsers;
    static final AtomicLong frames = new AtomicLong();
    static volatile String lastError = "";
    volatile String error = "";
    volatile long renderedFrames;
    private final Context context;
    private final boolean preview;
    private final HandlerThread thread = new HandlerThread("AliveGL");
    private final Handler handler;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private WallpaperRenderer renderer;
    private MotionSession motionSession;
    private String sharedMotionKey;
    private StereoSensor sensor;
    private boolean visible;
    private volatile boolean closed;
    private EngineRegistry.Snapshot state = new EngineRegistry.Snapshot(1, 1, 0, 0);
    private Choreographer choreographer;
    private boolean framePosted;
    private long scheduledDelay;
    private long frameNanos;
    private final Runnable tick = this::draw;
    private final Choreographer.FrameCallback vsync = this::onVsync;
    private void onVsync(long time){framePosted=false;frameNanos=time;handler.post(tick);}
    EglWorker(Context c) { this(c, false); }
    EglWorker(Context c, boolean preview) { context=c.getApplicationContext();this.preview=preview;thread.start();handler=new Handler(thread.getLooper()); }
    void attach(Surface surface, int w, int h, int variant, boolean dark) {
        handler.post(() -> {
            release();
            renderedFrames=0;error="";
            if (closed || !surface.isValid() || w <= 0 || h <= 0) return;
            try {
                display=acquireDisplay();
                EGLConfig[] configs={CosmicEgl.choose(display,EGL14.EGL_WINDOW_BIT)};
                eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
                check(eglContext!=EGL14.EGL_NO_CONTEXT,"create context");
                eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
                check(eglSurface!=EGL14.EGL_NO_SURFACE,"create window");
                check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext),"make current");
                EGL14.eglSwapInterval(display,1);
                if(variant<201||(variant>=301&&variant<=303)) {
                    MotionSession.Factory factory=()->variant<201
                        ?new CosmicMotion(context.getAssets(),variant,dark,state.from,false,(float)h/w)
                        :new GradientMotion(context.getAssets(),variant);
                    if(preview)motionSession=new MotionSession(factory.create());
                    else {
                        sharedMotionKey=variant+":"+dark+":"+w+"x"+h;
                        motionSession=MotionSession.acquire(sharedMotionKey,factory);
                    }
                    renderer=variant<201
                        ?new CosmicRenderer(context.getAssets(),variant,dark,w,h,state.from,false,(CosmicMotion)motionSession.motion)
                        :new GradientRenderer(context.getAssets(),variant,dark,w,h,state.from,(GradientMotion)motionSession.motion);
                } else {
                    EngineRegistry.Snapshot[] history=variant>=601&&!preview?EngineRegistry.history():new EngineRegistry.Snapshot[0];
                    int initial=history.length==0?state.from:history[0].from;
                    renderer=variant>=601?new PhotoRenderer(context,w,h,initial,variant,preview,this::request):WallpaperRenderer.create(context.getAssets(),variant,dark,w,h,state.from,false,this::request);
                    motionSession=new MotionSession(renderer.motion());
                    // Replay CPU-only events so a newly bound home slot does not start halfway
                    // through AOD -> lock -> home from a fictitious, fully expanded lock frame.
                    for(EngineRegistry.Snapshot event:history){
                        long time=event.startMs*1000000L;
                        motionSession.frame(event.sequence,event.mode,time,time,()->{});
                    }
                    if(history.length>0&&history[history.length-1].sequence>state.sequence)state=history[history.length-1];
                }
                motionSession.visible(this,visible);
                renderer.continuous(true);renderer.visible(visible);
                choreographer=Choreographer.getInstance();
                if (variant>=501&&variant<=503) {
                    sensor=new StereoSensor(context,handler,(x,y,z,t)->{
                        if(renderer!=null&&renderer.motion() instanceof StereoMotion)
                            ((StereoMotion)renderer.motion()).gyroscope(x,y,z,t);
                    });
                    sensor.setActive(visible);
                }
                error="";if(!preview)lastError="";Log.i(Protocol.TAG,"GL ready variant="+variant+" size="+w+"x"+h);request();
            } catch (Throwable error) { fail(error); }
        });
    }
    void state(EngineRegistry.Snapshot snapshot) { handler.post(()->{
        state=snapshot;
        // Hidden photo engines retain the complete event chain without submitting GPU frames.
        if(renderer instanceof PhotoRenderer&&motionSession!=null){
            try {
                long time=snapshot.startMs*1000000L;
                motionSession.frame(snapshot.sequence,snapshot.mode,time,time,()->{});
            }catch(Throwable error){fail(error);}
        }
        request();
    }); }
    void photoOptions(PhotoOptions options) { handler.post(()->{
        if(renderer instanceof PhotoRenderer)try{((PhotoRenderer)renderer).configure(options);request();}catch(Throwable error){fail(error);}
    }); }
    void resumeDisplay(){handler.post(()->{stopFrames();request();});}
    void visible(boolean value) { handler.post(()->{if(visible==value)return;visible=value;if(motionSession!=null)motionSession.visible(this,value);if(renderer!=null)renderer.visible(value);if(sensor!=null)sensor.setActive(value);stopFrames();if(value)request();}); }
    void offset(float x, float y) { handler.post(()->{if(renderer!=null){
        // Cosmic.follow means AOD clock anchor, not launcher scrolling or touch coordinates.
        if(!(renderer.motion() instanceof CosmicMotion)&&!(renderer.motion() instanceof GradientMotion))renderer.motion().follow(x,y);
        renderer.interaction();request();
    }}); }
    void detach() { handler.post(this::release); }
    void close() { handler.post(()->{closed=true;release();thread.quitSafely();}); }
    private void request() {
        if(Looper.myLooper()!=handler.getLooper()){handler.post(this::request);return;}
        if(framePosted&&scheduledDelay>0)stopFrames();
        schedule(0);
    }
    private void schedule(long delay) {
        if(!closed&&visible&&renderer!=null&&choreographer!=null&&!framePosted){
            framePosted=true;scheduledDelay=delay;
            if(delay==0)choreographer.postFrameCallback(vsync);else choreographer.postFrameCallbackDelayed(vsync,delay);
        }
    }
    private void stopFrames(){
        if(choreographer!=null)choreographer.removeFrameCallback(vsync);
        framePosted=false;handler.removeCallbacks(tick);
    }
    private void draw() {
        if(!visible||closed||renderer==null)return;
        try {
            motionSession.frame(state.sequence,state.mode,state.startMs*1000000L,frameNanos,renderer::render);
            int glError=GLES30.glGetError();if(glError!=GLES30.GL_NO_ERROR)throw new IllegalStateException("GL error 0x"+Integer.toHexString(glError));
            if(renderer.frameReady()) {
                check(EGL14.eglSwapBuffers(display,eglSurface),"swap");
                renderedFrames++;long n=preview?renderedFrames:frames.incrementAndGet();if(n%300==1)Log.i(Protocol.TAG,"frame="+n+" mode="+state.mode+" preview="+preview);
            }
            // Follow display vsync. An extra 17ms sleep after eglSwapBuffers can miss the next vsync;
            // the old 1fps AOD rule also broke preview shape/color timing.
            if(renderer instanceof PhotoRenderer) {
                PhotoRenderer photo=(PhotoRenderer)renderer;
                // Missing masks wake us when ready. Settled photos need no identical redraws;
                // only the AOD lens rotation keeps ticking, with time-based motion at ~30fps.
                if(photo.frameReady()&&photo.motion().active())schedule(photo.animating()?0:25);
            } else request();
        } catch(Throwable error){fail(error);}
    }
    private void fail(Throwable error){this.error=error.toString();if(!preview)lastError=error.toString();Log.e(Protocol.TAG,"Render failure",error);release();}
    private static void check(boolean ok,String operation){if(!ok)throw new IllegalStateException(operation+" EGL=0x"+Integer.toHexString(EGL14.eglGetError()));}
    // EGL_DEFAULT_DISPLAY is process-wide. Closing a preview must not terminate both wallpaper slots.
    static synchronized EGLDisplay acquireDisplay(){
        if(displayUsers==0){
            EGLDisplay candidate=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version=new int[2];check(EGL14.eglInitialize(candidate,version,0,version,1),"initialize");
            sharedDisplay=candidate;
        }
        displayUsers++;return sharedDisplay;
    }
    static synchronized void releaseDisplay(){
        if(--displayUsers==0){EGL14.eglTerminate(sharedDisplay);sharedDisplay=EGL14.EGL_NO_DISPLAY;}
    }
    private void release(){
        stopFrames();
        if(sensor!=null){sensor.close();sensor=null;}
        if(motionSession!=null){
            if(sharedMotionKey!=null)MotionSession.release(sharedMotionKey,motionSession,this);
            else motionSession.visible(this,false);
            motionSession=null;
        }
        if(renderer!=null){try{renderer.close();}catch(RuntimeException e){Log.w(Protocol.TAG,"renderer cleanup",e);}renderer=null;}
        sharedMotionKey=null;
        if(display!=EGL14.EGL_NO_DISPLAY){
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(eglSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,eglSurface);
            if(eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,eglContext);
            releaseDisplay();EGL14.eglReleaseThread();
        }
        display=EGL14.EGL_NO_DISPLAY;eglSurface=EGL14.EGL_NO_SURFACE;eglContext=EGL14.EGL_NO_CONTEXT;
    }
}
