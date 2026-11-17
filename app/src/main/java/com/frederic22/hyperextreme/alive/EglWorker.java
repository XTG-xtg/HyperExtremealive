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
    volatile long activeGeneration, renderedGeneration;
    volatile long firstFrameMs = -1, initMs, recoveries;
    volatile long preparedGeneration;
    volatile int displayState = android.view.Display.STATE_UNKNOWN;
    volatile boolean displayInteractive = true;
    volatile String lastFailure = "";
    volatile LiquidTouchMap liquidMap;
    volatile float configuredBrightness=AodBrightness.DEFAULT, renderedExposure=1;
    volatile long discardedFrames,lastFrameAtMs,lastSwapStartedMs,maxSwapMs;
    volatile String frameStage="idle";
    volatile int swapInterval=1;
    private LiquidTouchMap pendingLiquidMap;
    private final AtomicLong generations = new AtomicLong();
    private final AtomicLong inputEpoch = new AtomicLong();
    private final Context context;
    private final boolean preview;
    private final boolean homeInput;
    private final Runnable homeReady;
    private final HandlerThread thread = new HandlerThread("AliveGL");
    private final Handler handler;
    private final RenderRecovery recovery = new RenderRecovery();
    private Surface targetSurface;
    private int targetWidth, targetHeight, targetVariant;
    private boolean targetDark, retryPosted, bootstrapFrame;
    private boolean preparationPosted, preparing, readyNotified;
    private long preparationUntil;
    private volatile boolean requestedVisible;
    private volatile int requestedDisplay = android.view.Display.STATE_UNKNOWN;
    private volatile boolean requestedInteractive = true;
    private volatile EngineRegistry.Snapshot requestedState = new EngineRegistry.Snapshot(1,1,0,0);
    private final Runnable preparation = () -> {
        preparationPosted=false;
        if(!canPrepare()||preparedGeneration==activeGeneration||!error.isEmpty())return;
        if(this.renderer==null)initialize(true);
        if(this.renderer!=null&&canPrepare()) {
            preparing=true;preparationUntil=SystemClock.uptimeMillis()+500;
            draw(true);
        }
    };
    private long attachedAt, fallbackUntil;
    private final Runnable retry = () -> {
        retryPosted = false;
        if (currentSurface() && renderVisible()) { if(!error.isEmpty())recoveries++;initialize(); }
    };
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private WallpaperRenderer renderer;
    private MotionSession motionSession;
    private String sharedMotionKey;
    private StereoSensor sensor;
    private AliveRenderOptions options;
    private PhotoOptions previewPhoto;
    private VivoOptions previewVivo;
    private AodPresentation presentation;
    private ClockSnapshot clock;
    private int width,height;
    private float presentationX,presentationY;
    private final Runnable minuteTick=()->{try{applyPlacement();request();}catch(Throwable error){fail(error);}};
    private boolean visible;
    private volatile boolean closed;
    private EngineRegistry.Snapshot state = new EngineRegistry.Snapshot(1, 1, 0, 0);
    private Choreographer choreographer;
    private FramePump pump;
    EglWorker(Context c) { this(c, false); }
    EglWorker(Context c, boolean preview) { this(c,preview,true,()->{}); }
    EglWorker(Context c, boolean preview, boolean homeInput, Runnable homeReady) { context=c.getApplicationContext();this.preview=preview;this.homeInput=homeInput;this.homeReady=homeReady;options=AliveRenderOptions.load(context);thread.start();handler=new Handler(thread.getLooper()); }
    long attach(Surface surface, int w, int h, int variant, boolean dark) {
        final long generation = generations.incrementAndGet();
        handler.post(() -> {
            if(closed||generation!=generations.get())return;
            release();
            targetSurface=surface;targetWidth=w;targetHeight=h;targetVariant=variant;targetDark=dark;
            recovery.reset();attachedAt=SystemClock.uptimeMillis();firstFrameMs=-1;
            renderedFrames=0;error="";
            discardedFrames=lastFrameAtMs=lastSwapStartedMs=maxSwapMs=0;
            activeGeneration=generation;
            // The hidden companion slot must not compete for texture/shader work at boot.
            if(renderVisible())initialize();
        });
        return generation;
    }
    private boolean currentSurface() {
        return !closed && activeGeneration==generations.get() && targetSurface!=null;
    }
    private boolean renderVisible() {
        return visible && requestedVisible && DisplayFramePolicy.canRender(preview, displayState)
            && DisplayFramePolicy.canRender(preview, requestedDisplay);
    }
    private boolean canPresent() {
        return currentSurface()&&DisplayFramePolicy.canSubmit(preview,visible,requestedVisible,
            displayState,requestedDisplay,state.sequence,requestedState.sequence);
    }
    boolean readyForCompanion() {
        return renderedGeneration==generations.get()&&renderedFrames>0&&error.isEmpty();
    }
    private boolean canPrepare() {
        PowerManager power=context.getSystemService(PowerManager.class);
        return currentSurface()&&state.sequence==requestedState.sequence
            &&CompanionPreparation.allowed(preview,targetVariant,requestedVisible,requestedState.mode,
                requestedDisplay,requestedInteractive)&&power!=null&&power.isInteractive();
    }
    void prepareCompanion() { handler.post(()->{
        if(canPrepare()&&!preparationPosted&&!preparing&&preparedGeneration!=activeGeneration&&error.isEmpty()) {
            preparationPosted=true;handler.postDelayed(preparation,150);
        }
    }); }
    private void initialize() {
        initialize(false);
    }
    private void initialize(boolean prepare) {
            if (!currentSurface()) return;
            Surface surface=targetSurface;
            int w=targetWidth,h=targetHeight,variant=targetVariant;boolean dark=targetDark;
            long began=SystemClock.uptimeMillis();
            try {
                if (!surface.isValid() || w<=0 || h<=0) throw new IllegalStateException("Surface not ready");
                DynamicResources.require(context,variant);
                width=w;height=h;clock=EngineRegistry.clock(true);if(!preview)options=AliveRenderOptions.load(context);
                display=acquireDisplay();
                EGLConfig[] configs={CosmicEgl.choose(display,EGL14.EGL_WINDOW_BIT,variant<201||variant>=501&&variant<=503)};
                eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
                check(eglContext!=EGL14.EGL_NO_CONTEXT,"create context");
                eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
                check(eglSurface!=EGL14.EGL_NO_SURFACE,"create window");
                check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext),"make current");
                // FramePump already follows vsync. Do not wait for another vsync inside
                // swapBuffers when doze/rapid surface handoff suspends the consumer.
                int[] minimumInterval={1};
                EGL14.eglGetConfigAttrib(display,configs[0],EGL14.EGL_MIN_SWAP_INTERVAL,minimumInterval,0);
                swapInterval=Math.max(0,minimumInterval[0]);
                check(EGL14.eglSwapInterval(display,swapInterval),"swap interval");
                EngineRegistry.Snapshot[] history=prepare
                    ?new EngineRegistry.Snapshot[]{new EngineRegistry.Snapshot(state.mode,state.mode,0,state.sequence)}
                    :!preview?EngineRegistry.history():new EngineRegistry.Snapshot[0];
                int initial=history.length==0?state.from:history[0].from;
                if(variant<201||(variant>=301&&variant<=303)||InfinityTheme.supports(variant)) {
                    MotionSession.Factory factory=()->variant<201
                        ?new CosmicMotion(context.getAssets(),variant,dark,initial,options.keepLock,(float)h/w)
                        :InfinityTheme.supports(variant)?InfinityMotion.create(variant,initial):new GradientMotion(context.getAssets(),variant);
                    if(preview)motionSession=new MotionSession(factory.create());
                    else {
                        sharedMotionKey=variant+":"+dark+":"+w+"x"+h+":"+options.keepLock;
                        motionSession=MotionSession.acquire(sharedMotionKey,factory,true);
                    }
                    renderer=variant<201
                        ?new CosmicRenderer(context.getAssets(),variant,dark,w,h,initial,options.keepLock,(CosmicMotion)motionSession.motion)
                        :InfinityTheme.supports(variant)?InfinityRenderer.create(context.getAssets(),variant,dark,w,h,initial,(InfinityMotion)motionSession.motion)
                        :new GradientRenderer(context.getAssets(),variant,dark,w,h,initial,(GradientMotion)motionSession.motion);
                } else if(variant>=701&&variant<=703){
                    VivoOptions vivo=preview&&previewVivo!=null?previewVivo:VivoOptions.load(context,variant,preview);
                    renderer=new VivoRenderer(context,vivo,w,h,initial,preview,dark,this::request);
                    if(!preview)pendingLiquidMap=((VivoRenderer)renderer).touchMap();
                    motionSession=new MotionSession(renderer.motion(),!preview);
                } else {
                    renderer=variant>=601&&variant<=606?new PhotoRenderer(context,preview&&previewPhoto!=null?previewPhoto:PhotoOptions.load(context,preview),w,h,initial,variant,preview,this::request):WallpaperRenderer.create(context.getAssets(),variant,dark,w,h,initial,options.keepLock,this::request);
                    motionSession=new MotionSession(renderer.motion(),!preview);
                }
                // A newly attached slot reconstructs the same event chain as the existing slot.
                for(EngineRegistry.Snapshot event:history){
                    long time=event.startMs==0?System.nanoTime():event.startMs*1000000L;
                    motionSession.frame(event.sequence,event.mode,time,time,()->{});
                }
                if(history.length>0&&history[history.length-1].sequence>state.sequence)state=history[history.length-1];
                motionSession.visible(this,renderVisible());
                renderer.visible(renderVisible());
                presentation=new AodPresentation(context.getAssets(),w,h);
                // Exposure's FBO/shader otherwise compile on the first sleep, not at scene creation.
                if(prepare||renderer instanceof PhotoRenderer)presentation.prepare();
                applyOptions();applyPlacement();
                choreographer=Choreographer.getInstance();
                pump=new FramePump(new FramePump.Driver(){
                    private Choreographer.FrameCallback vsync;
                    private Runnable timeout;
                    public void vsync(Runnable frame,long delay){vsync=time->frame.run();choreographer.postFrameCallbackDelayed(vsync,delay);}
                    public void cancelVsync(){if(vsync!=null)choreographer.removeFrameCallback(vsync);vsync=null;}
                    public void timer(Runnable frame,long delay){timeout=frame;handler.postDelayed(frame,delay);}
                    public void cancelTimer(){if(timeout!=null)handler.removeCallbacks(timeout);timeout=null;}
                },this::draw);
                if (variant>=501&&variant<=503||InfinityTheme.supports(variant)) {
                    sensor=new StereoSensor(context,handler,(x,y,z,t)->{
                        if(renderer!=null&&renderer.motion() instanceof InfinityMotion){
                            synchronized(motionSession){((InfinityMotion)renderer.motion()).gyroscope(x,y,t,width>height,options.sensitivity);}
                            if(renderer.motion().active())request();
                        }
                        if(renderer!=null&&renderer.motion() instanceof StereoMotion){
                            ((StereoMotion)renderer.motion()).gyroscope(x,y,z,t);
                            if(renderer.motion().active())request();
                        }
                    });
                    sensor.setActive(renderVisible()&&(state.mode!=0||!InfinityTheme.supports(variant)&&options.stereoAod));
                }
                if(!currentSurface()){release();return;}
                initMs=SystemClock.uptimeMillis()-began;
                bootstrapFrame=true;
                Log.i(Protocol.TAG,"GL ready variant="+variant+" size="+w+"x"+h+" initMs="+initMs);request();
            } catch (Throwable error) { fail(error); }
    }
    void state(EngineRegistry.Snapshot snapshot) { requestedState=snapshot;handler.post(()->{
        if(snapshot.sequence<state.sequence)return;
        state=snapshot;
        // Consume every lifecycle event, including when AOD owns an invisible wallpaper slot.
        // Otherwise a hide -> sleep -> wake chain can leave a stale expanded CPU scene.
        if(!preview&&motionSession!=null){
            try {
                long time=snapshot.startMs*1000000L;
                motionSession.frame(snapshot.sequence,snapshot.mode,time,time,()->{});
            }catch(Throwable error){fail(error);}
        }
        try {applyOptions();applyPlacement();request();}catch(Throwable error){fail(error);}
    }); }
    void options(AliveRenderOptions value){handler.post(()->{try{options=value;applyOptions();applyPlacement();request();}catch(Throwable error){fail(error);}});}
    void clock(ClockSnapshot value){handler.post(()->{try{clock=value;applyPlacement();request();}catch(Throwable error){fail(error);}});}
    private void applyOptions(){
        configuredBrightness=options.brightness;
        if(renderer==null)return;
        renderer.continuous(state.mode==0?options.continuousAod:options.continuousHome);
        if(renderer.motion() instanceof StereoMotion)((StereoMotion)renderer.motion()).configure(options.sensitivity,options.stereoAod);
        if(sensor!=null)sensor.setActive(renderVisible()&&(state.mode!=0||!(renderer.motion() instanceof InfinityMotion)&&options.stereoAod));
    }
    private void applyPlacement(){
        handler.removeCallbacks(minuteTick);
        if(renderer==null)return;
        boolean anchored=renderer instanceof PhotoRenderer||renderer.motion() instanceof CosmicMotion;
        boolean system=anchored&&options.followClock&&clock!=null&&clock.usable(width,height);
        float x=system?clock.bounds.x():0,y=system?clock.bounds.y():.165f;
        // A live system snapshot already contains burn-in motion: never add it twice.
        if(!system&&!preview&&options.burnIn){x+=com.frederic22.hyperextreme.alive.core.AodPlacement.driftX(System.currentTimeMillis());y+=com.frederic22.hyperextreme.alive.core.AodPlacement.driftY(System.currentTimeMillis());}
        presentationX=options.aodOffsetX+x;presentationY=options.aodOffsetY-(y-.165f);
        synchronized(motionSession){
            if(renderer instanceof PhotoRenderer)((PhotoRenderer)renderer).anchor(x,y,system);
            else if(renderer.motion() instanceof CosmicMotion)renderer.motion().follow(x+options.aodOffsetX,y-options.aodOffsetY);
        }
        if(renderVisible()&&!preview&&state.mode==0)handler.postDelayed(minuteTick,60000-System.currentTimeMillis()%60000);
    }
    void photoOptions(PhotoOptions options) { handler.post(()->{
        if(preview)previewPhoto=options;
        if(renderer instanceof PhotoRenderer)try{((PhotoRenderer)renderer).configure(options);request();}catch(Throwable error){fail(error);}
    }); }
    void vivoOptions(VivoOptions value){
        VivoOptions copy=VivoOptions.parse(value.json());
        handler.post(()->{if(preview)previewVivo=copy;if(renderer instanceof VivoRenderer)try{((VivoRenderer)renderer).configure(copy);request();}catch(Throwable error){fail(error);}});
    }
    void touch(int action,float x,float y){
        long sequence=requestedState.sequence,generation=generations.get(),epoch=inputEpoch.get(),time=System.nanoTime();
        boolean wasVisible=requestedVisible;
        handler.post(()->{
        try{
            if(renderer instanceof VivoRenderer){((VivoRenderer)renderer).touch(action,x,y);request();}
            else if(renderer!=null&&renderer.motion() instanceof InfinityMotion&&infinityInput(sequence,generation,epoch,wasVisible)){
                synchronized(motionSession){((InfinityMotion)renderer.motion()).touch(this,action,x,y,time);}request();
            }
        }catch(Throwable error){fail(error);}
    });}
    private boolean infinityInput(long sequence,long generation,long epoch,boolean wasVisible){
        return !closed&&wasVisible&&renderVisible()&&(preview||displayInteractive&&requestedInteractive)
            &&generation==activeGeneration&&generation==generations.get()&&epoch==inputEpoch.get()
            &&sequence==state.sequence&&sequence==requestedState.sequence
            &&state.mode!=0&&(preview||state.mode!=2||homeInput);
    }
    void flashDemo(boolean value){handler.post(()->{
        if(preview&&renderer instanceof VivoRenderer){((VivoRenderer)renderer).flashDemo(value);request();}
    });}
    void resumeDisplay(){handler.post(()->{stopFrames();recoverIfNeeded();request();});}
    void display(int value, boolean interactive) { if(!interactive)inputEpoch.incrementAndGet();requestedDisplay=value;requestedInteractive=interactive;handler.post(()->{
        if(closed||displayState==value&&displayInteractive==interactive)return;
        displayState=value;displayInteractive=interactive;
        updateVisibility();
        Log.i(Protocol.TAG,"render display="+value+" interactive="+interactive+" visible="+visible);
    }); }
    void visible(boolean value) { if(!value)inputEpoch.incrementAndGet();requestedVisible=value;handler.post(()->{
        if(closed)return;
        visible=value;
        updateVisibility();
    }); }
    private void updateVisibility() {
        boolean active=renderVisible();
        try {
            if((!active||!preview&&!displayInteractive)&&renderer!=null&&renderer.motion() instanceof InfinityMotion){
                synchronized(motionSession){((InfinityMotion)renderer.motion()).cancelTouch(this);}
            }
            if(motionSession!=null)motionSession.visible(this,active);
            if(renderer!=null)renderer.visible(active);
            applyOptions();applyPlacement();stopFrames();
            if(active){recoverIfNeeded();request();}
            else {handler.removeCallbacks(retry);retryPosted=false;}
        }catch(Throwable error){fail(error);}
    }
    private void recoverIfNeeded(){
        if(renderer==null&&!retryPosted&&renderVisible()&&currentSurface()&&recovery.resume(SystemClock.uptimeMillis())){
            retryPosted=true;handler.post(retry);
        }
    }
    void launcherOffset(float x,float y,float xStep,float yStep){
        long sequence=requestedState.sequence,generation=generations.get(),epoch=inputEpoch.get(),time=System.nanoTime();
        boolean wasVisible=requestedVisible;
        handler.post(()->{try{
            if(renderer==null)return;
            if(renderer.motion() instanceof InfinityMotion){
                if(!infinityInput(sequence,generation,epoch,wasVisible)||state.mode!=2)return;
                synchronized(motionSession){((InfinityMotion)renderer.motion()).follow(
                    InfinityLauncherOffset.centered(x,xStep),InfinityLauncherOffset.centered(y,yStep),time);}
                request();
            }else applyOffset(x-.5f,y-.5f);
        }catch(Throwable error){fail(error);}});
    }
    void touchOffset(float x,float y){handler.post(()->{try{
        // Infinity receives relative drags through touch(); never reinterpret them as launcher offsets.
        if(renderer!=null&&!(renderer.motion() instanceof InfinityMotion))applyOffset(x,y);
    }catch(Throwable error){fail(error);}});}
    private void applyOffset(float x,float y){if(renderer!=null){
        // Cosmic.follow means AOD clock anchor, not launcher scrolling or touch coordinates.
        if(!(renderer.motion() instanceof CosmicMotion)&&!(renderer.motion() instanceof GradientMotion)){
            synchronized(motionSession){renderer.motion().follow(x,y);}
        }
        renderer.interaction();request();
    }}
    void detach() { generations.incrementAndGet();handler.post(()->{targetSurface=null;release();}); }
    void close() { closed=true;generations.incrementAndGet();handler.post(()->{targetSurface=null;release();thread.quitSafely();}); }
    private void request() {
        if(Looper.myLooper()!=handler.getLooper()){handler.post(this::request);return;}
        if(preparing&&!renderVisible()) {
            if(canPrepare()&&SystemClock.uptimeMillis()<preparationUntil)draw(true);
            else preparing=false;
            return;
        }
        // Unknown/awake displays retain the bounded recovery window. Confirmed visible doze
        // gets a separate display-lifetime budget, revoked immediately on OFF or hide.
        fallbackUntil=SystemClock.uptimeMillis()+3000;
        schedule(0);
    }
    private void schedule(long delay) {
        if(currentSurface()&&renderVisible()&&renderer!=null&&pump!=null){
            long fallback=DisplayFramePolicy.fallback(preview,displayState,displayInteractive,
                bootstrapFrame,delay,SystemClock.uptimeMillis(),fallbackUntil);
            pump.request(delay,fallback);
        }
    }
    private void stopFrames(){
        if(pump!=null)pump.cancel();
    }
    private void draw() {
        draw(false);
    }
    private void draw(boolean prepare) {
        if(!currentSurface()||renderer==null)return;
        if(prepare?!canPrepare():!canPresent()){discardedFrames++;frameStage="discarded";return;}
        if(!prepare)preparing=false;
        bootstrapFrame=false;
        try {
            frameStage="render";
            if(!targetSurface.isValid())throw new IllegalStateException("Surface lost before draw");
            long frameNanos=System.nanoTime();
            motionSession.frame(state.sequence,state.mode,state.startMs*1000000L,frameNanos,renderer::render);
            frameStage=renderer.frameReady()?"presentation":"waiting-assets";
            float progress=prepare?1:Math.max(0,Math.min(1,(SystemClock.uptimeMillis()-state.startMs)/700f));
            // Families with native anchors preserve their full background. Other families use
            // the presentation pass, fading the displacement out before lock/home settles.
            float aodWeight=(state.from==0?1:0)+((state.mode==0?1:0)-(state.from==0?1:0))*progress;
            boolean translate=!(renderer instanceof PhotoRenderer)&&!(renderer.motion() instanceof CosmicMotion);
            // Keep the scene/placement transition, but never boost the restored lock/home.
            // The display wake can precede the asynchronous scene signal.
            boolean ambient=state.mode==0&&(preview||!requestedInteractive);
            float exposure=AodBrightness.renderGain(options.brightness,aodWeight,ambient,preview,AodBrightness.panelManaged());
            if(renderer.frameReady())presentation.render(exposure,
                translate?presentationX*aodWeight:0,translate?presentationY*aodWeight:0);
            int glError=GLES30.glGetError();if(glError!=GLES30.GL_NO_ERROR)throw new IllegalStateException("GL error 0x"+Integer.toHexString(glError));
            if(renderer.frameReady()) {
                // Main-thread sleep/hide/detach requests can arrive during initialization/upload.
                // A main-thread hide/OFF/new scene may arrive while render() is running.
                // Do not enqueue that obsolete frame or block on its hidden BufferQueue.
                if(prepare?!canPrepare():!canPresent()){
                    preparing=false;discardedFrames++;frameStage="discarded";return;
                }
                frameStage="swap";lastSwapStartedMs=SystemClock.uptimeMillis();
                check(EGL14.eglSwapBuffers(display,eglSurface),"swap");
                lastFrameAtMs=SystemClock.uptimeMillis();
                long swapMs=lastFrameAtMs-lastSwapStartedMs;
                if(swapMs>=100&&swapMs>maxSwapMs)
                    Log.w(Protocol.TAG,"slow swap ms="+swapMs+" mode="+state.mode+" requested="+requestedState.mode
                        +" visible="+requestedVisible+" display="+requestedDisplay+" interval="+swapInterval);
                maxSwapMs=Math.max(maxSwapMs,swapMs);frameStage="submitted";
                if(!currentSurface())return;
                if(firstFrameMs<0){
                    firstFrameMs=SystemClock.uptimeMillis()-attachedAt;
                    Log.i(Protocol.TAG,"first frame generation="+activeGeneration+" firstMs="+firstFrameMs+" initMs="+initMs+" mode="+state.mode+" prepared="+prepare);
                }
                recovery.drawn(SystemClock.uptimeMillis());
                error="";if(!preview)lastError="";
                renderedGeneration=activeGeneration;
                renderedExposure=exposure;
                if(!preview&&pendingLiquidMap!=null){
                    liquidMap=pendingLiquidMap;pendingLiquidMap=null;
                    notifyConfig();
                }
                renderedFrames++;long n=preview?renderedFrames:frames.incrementAndGet();if(n%300==1)Log.i(Protocol.TAG,"frame="+n+" mode="+state.mode+" preview="+preview);
                if(prepare){preparedGeneration=activeGeneration;preparing=false;}
                else if(!preview&&state.mode==2&&!readyNotified){readyNotified=true;EngineRegistry.MAIN.post(homeReady);}
            }
            if(prepare)return;
            // Follow display vsync. An extra 17ms sleep after eglSwapBuffers can miss the next vsync;
            // the old 1fps AOD rule also broke preview shape/color timing.
            if(renderer instanceof PhotoRenderer) {
                PhotoRenderer photo=(PhotoRenderer)renderer;
                // Missing masks wake us when ready. Settled photos need no identical redraws;
                // only the AOD lens rotation keeps ticking, with time-based motion at ~30fps.
                if(photo.frameReady()&&(photo.needsRedraw()||photo.animating()||progress<1||options.continuousAod&&photo.motion().active()))schedule(photo.needsRedraw()||photo.animating()||progress<1?0:25);
            } else if(renderer.motion() instanceof InfinityMotion){
                // Infinity is event-driven: keep the settled AOD frame without an idle redraw loop.
                if(renderer.motion().active()||progress<1)schedule(0);
            } else if(renderer.motion().active()||SystemClock.uptimeMillis()-state.startMs<2500)schedule(0);
            else if(state.mode==0?options.continuousAod:options.continuousHome)schedule(25);
        } catch(Throwable error){fail(error);}
    }
    private void fail(Throwable error){
        frameStage="failed";
        this.error=lastFailure=error.toString();if(!preview)lastError=this.error;
        Log.e(Protocol.TAG,"Render failure generation="+activeGeneration,error);
        long delay=recovery.failed(SystemClock.uptimeMillis());
        release();
        if(delay>=0&&renderVisible()&&currentSurface()){
            retryPosted=true;handler.postDelayed(retry,delay);
        }
    }
    private void notifyConfig(){
        // A transient IPC failure must never tear down an already submitted wallpaper frame.
        EngineRegistry.MAIN.post(()->{
            try{context.getContentResolver().notifyChange(Protocol.CONFIG,null);}
            catch(RuntimeException error){Log.w(Protocol.TAG,"Config notification unavailable",error);}
        });
    }
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
        handler.removeCallbacks(preparation);preparationPosted=false;preparing=false;readyNotified=false;
        preparedGeneration=0;
        handler.removeCallbacks(retry);retryPosted=false;
        boolean hadMap=liquidMap!=null;liquidMap=null;pendingLiquidMap=null;
        if(hadMap&&!preview)notifyConfig();
        stopFrames();
        pump=null;
        handler.removeCallbacks(minuteTick);
        if(presentation!=null){try{presentation.close();}catch(RuntimeException e){Log.w(Protocol.TAG,"presentation cleanup",e);}presentation=null;}
        if(sensor!=null){try{sensor.close();}catch(RuntimeException e){Log.w(Protocol.TAG,"sensor cleanup",e);}sensor=null;}
        if(motionSession!=null){
            if(renderer!=null&&renderer.motion() instanceof InfinityMotion){
                synchronized(motionSession){((InfinityMotion)renderer.motion()).cancelTouch(this);}
            }
            try {
                if(sharedMotionKey!=null)MotionSession.release(sharedMotionKey,motionSession,this);
                else motionSession.visible(this,false);
            }catch(RuntimeException e){Log.w(Protocol.TAG,"motion cleanup",e);}
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
