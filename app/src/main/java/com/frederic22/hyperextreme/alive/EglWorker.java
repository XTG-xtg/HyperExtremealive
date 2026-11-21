package com.frederic22.hyperextreme.alive;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.opengl.*;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
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
    volatile int displayState = Display.STATE_UNKNOWN;
    volatile boolean displayInteractive = true;
    volatile String lastFailure = "";
    volatile LiquidTouchMap liquidMap;
    volatile float configuredBrightness = AodBrightness.DEFAULT, renderedExposure = 1;
    volatile long discardedFrames, lastFrameAtMs, lastSwapStartedMs, maxSwapMs;
    volatile String frameStage = "idle";
    volatile int swapInterval = 1;
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
    private boolean targetDark, retryPosted;
    private boolean preparationPosted, preparing;
    private long companionFirstSequence = -1, companionSettledSequence = -1, clearedAodSequence = -1;
    private long preparationUntil;
    private volatile boolean requestedVisible;
    private volatile int requestedDisplay = Display.STATE_UNKNOWN;
    private volatile boolean requestedInteractive = true;
    private volatile EngineRegistry.Snapshot requestedState = new EngineRegistry.Snapshot(1, 1, 0, 0);
    private final Runnable preparation = () -> {
        preparationPosted = false;
        if (!canPrepare() || !error.isEmpty()) return;
        if (this.renderer == null) initialize(true);
        if (this.renderer != null && canPrepare()) {
            preparing = true;
            preparationUntil = SystemClock.uptimeMillis() + 500;
            draw(true);
        }
    };
    private long attachedAt;
    private float targetRefreshHz = 120f;
    private long frameIntervalNanos = 8_333_333L;
    private long lastFramePacingCheckMs;
    private boolean swapIntervalApplied;
    private boolean highFrameRateRequested;
    private int consecutiveFrameErrors;
    private final Runnable pace = () -> draw(false);
    private final Runnable retry = () -> {
        retryPosted = false;
        if (currentSurface() && renderVisible()) {
            if (!error.isEmpty()) recoveries++;
            initialize();
        }
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
    private int width, height;
    private float presentationX, presentationY;
    private final Runnable minuteTick = () -> {
        try {
            applyPlacement();
            request();
        } catch (Throwable error) {
            fail(error);
        }
    };
    private boolean visible;
    private volatile boolean closed;
    private EngineRegistry.Snapshot state = new EngineRegistry.Snapshot(1, 1, 0, 0);

    EglWorker(Context c) { this(c, false); }
    EglWorker(Context c, boolean preview) { this(c, preview, true, () -> {}); }
    EglWorker(Context c, boolean preview, boolean homeInput, Runnable homeReady) {
        context = c.getApplicationContext();
        this.preview = preview;
        this.homeInput = homeInput;
        this.homeReady = homeReady;
        options = AliveRenderOptions.load(context);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    long attach(Surface surface, int w, int h, int variant, boolean dark) {
        final long generation = generations.incrementAndGet();
        handler.post(() -> {
            if (closed || generation != generations.get()) return;
            release();
            targetSurface = surface;
            targetWidth = w;
            targetHeight = h;
            targetVariant = variant;
            targetDark = dark;
            recovery.reset();
            attachedAt = SystemClock.uptimeMillis();
            firstFrameMs = -1;
            renderedFrames = 0;
            error = "";
            discardedFrames = lastFrameAtMs = lastSwapStartedMs = maxSwapMs = 0;
            consecutiveFrameErrors = 0;
            activeGeneration = generation;
            // The hidden companion slot must not compete for texture/shader work at boot.
            if (renderVisible()) initialize();
        });
        return generation;
    }

    private boolean currentSurface() {
        return !closed && activeGeneration == generations.get() && targetSurface != null;
    }

    private boolean renderVisible() {
        return visible && requestedVisible && DisplayFramePolicy.canRender(preview, displayState)
            && DisplayFramePolicy.canRender(preview, requestedDisplay);
    }

    private boolean canPresent() {
        return currentSurface() && DisplayFramePolicy.canSubmit(preview, visible, requestedVisible,
            displayState, requestedDisplay, state.sequence, requestedState.sequence)
            && DisplayFramePolicy.sceneMatches(preview, requestedDisplay, requestedInteractive, state.mode);
    }

    boolean readyForCompanion() {
        return renderedGeneration == generations.get() && renderedFrames > 0 && error.isEmpty();
    }

    private boolean canPrepare() {
        if (homeInput && !preview && renderer != null && currentSurface() && state.sequence == requestedState.sequence
            && CompanionPreparation.ambient(targetVariant, requestedVisible, requestedState.mode, requestedDisplay, requestedInteractive)) {
            // Never overwrite the cleared black home buffer with a mid-collapse full-screen frame.
            // Only draw the settled AOD frame once the collapse animation has actually finished.
            return SystemClock.uptimeMillis() - state.startMs >= 800L;
        }
        PowerManager power = context.getSystemService(PowerManager.class);
        return currentSurface() && state.sequence == requestedState.sequence
            && CompanionPreparation.allowed(preview, targetVariant, requestedVisible, requestedState.mode,
                requestedDisplay, requestedInteractive) && power != null && power.isInteractive();
    }

    void prepareCompanion() {
        handler.post(() -> {
            if (canPrepare() && !preparationPosted && !preparing && error.isEmpty()) {
                preparationPosted = true;
                // Refresh hidden home buffer promptly on AOD so fingerprint unlock never flashes stale desktop.
                long delay = requestedState.mode == 0 ? 16L : 120L;
                handler.postDelayed(preparation, delay);
            }
        });
    }

    private void initialize() {
        initialize(false);
    }

    private void initialize(boolean prepare) {
        if (!currentSurface()) return;
        Surface surface = targetSurface;
        int w = targetWidth, h = targetHeight, variant = targetVariant;
        boolean dark = targetDark;
        long began = SystemClock.uptimeMillis();
        try {
            if (!surface.isValid() || w <= 0 || h <= 0) throw new IllegalStateException("Surface not ready");
            DynamicResources.require(context, variant);
            width = w;
            height = h;
            clock = EngineRegistry.clock(true);
            if (!preview) options = AliveRenderOptions.load(context);
            display = acquireDisplay();
            EGLConfig[] configs = {CosmicEgl.choose(display, EGL14.EGL_WINDOW_BIT, variant < 201 || variant >= 501 && variant <= 503)};
            eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            check(eglContext != EGL14.EGL_NO_CONTEXT, "create context");
            eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], surface, new int[]{EGL14.EGL_NONE}, 0);
            check(eglSurface != EGL14.EGL_NO_SURFACE, "create window");
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext), "make current");
            updateFramePacing(true);
            swapIntervalApplied = false;
            applySwapInterval();
            EngineRegistry.Snapshot[] history = prepare
                ? new EngineRegistry.Snapshot[]{new EngineRegistry.Snapshot(state.mode, state.mode, 0, state.sequence)}
                : !preview ? EngineRegistry.history() : new EngineRegistry.Snapshot[0];
            int initial = history.length == 0 ? state.from : history[0].from;
            if (variant < 201 || (variant >= 301 && variant <= 303) || InfinityTheme.supports(variant)) {
                MotionSession.Factory factory = () -> variant < 201
                    ? new CosmicMotion(context.getAssets(), variant, dark, initial, options.keepLock, (float) h / w)
                    : InfinityTheme.supports(variant) ? InfinityMotion.create(variant, initial) : new GradientMotion(context.getAssets(), variant);
                if (preview) motionSession = new MotionSession(factory.create());
                else {
                    sharedMotionKey = variant + ":" + dark + ":" + w + "x" + h + ":" + options.keepLock;
                    motionSession = MotionSession.acquire(sharedMotionKey, factory, true);
                }
                renderer = variant < 201
                    ? new CosmicRenderer(context.getAssets(), variant, dark, w, h, initial, options.keepLock, (CosmicMotion) motionSession.motion)
                    : InfinityTheme.supports(variant) ? InfinityRenderer.create(context.getAssets(), variant, dark, w, h, initial, (InfinityMotion) motionSession.motion)
                    : new GradientRenderer(context.getAssets(), variant, dark, w, h, initial, (GradientMotion) motionSession.motion);
            } else if (variant >= 701 && variant <= 703) {
                VivoOptions vivo = preview && previewVivo != null ? previewVivo : VivoOptions.load(context, variant, preview);
                renderer = new VivoRenderer(context, vivo, w, h, initial, preview, dark, this::request);
                if (!preview) pendingLiquidMap = ((VivoRenderer) renderer).touchMap();
                motionSession = new MotionSession(renderer.motion(), !preview);
            } else {
                renderer = variant >= 601 && variant <= 606
                    ? new PhotoRenderer(context, preview && previewPhoto != null ? previewPhoto : PhotoOptions.load(context, preview), w, h, initial, variant, preview, this::request)
                    : WallpaperRenderer.create(context.getAssets(), variant, dark, w, h, initial, options.keepLock, this::request);
                motionSession = new MotionSession(renderer.motion(), !preview);
            }
            // A newly attached slot reconstructs the same event chain as the existing slot.
            long initNowNanos = System.nanoTime();
            for (EngineRegistry.Snapshot event : history) {
                long time = eventNanosFromUptime(event.startMs, initNowNanos);
                motionSession.frame(event.sequence, event.mode, time, time, () -> {});
            }
            if (history.length > 0 && history[history.length - 1].sequence > state.sequence) state = history[history.length - 1];
            motionSession.visible(this, renderVisible());
            renderer.visible(renderVisible());
            presentation = new AodPresentation(context.getAssets(), w, h);
            // Exposure's FBO/shader otherwise compile on the first sleep, not at scene creation.
            if (prepare || renderer instanceof PhotoRenderer) presentation.prepare();
            applyOptions();
            applyPlacement();
            if (variant >= 501 && variant <= 503 || InfinityTheme.supports(variant)) {
                sensor = new StereoSensor(context, handler, (x, y, z, t) -> {
                    if (renderer != null && renderer.motion() instanceof InfinityMotion) {
                        synchronized (motionSession) {
                            ((InfinityMotion) renderer.motion()).gyroscope(x, y, t, width > height, options.sensitivity);
                        }
                        if (renderer.motion().active()) request();
                    }
                    if (renderer != null && renderer.motion() instanceof StereoMotion) {
                        ((StereoMotion) renderer.motion()).gyroscope(x, y, z, t);
                        if (renderer.motion().active()) request();
                    }
                });
                sensor.setActive(renderVisible() && (InfinityTheme.supports(variant) ? state.mode != 0 && options.infinityGyro : state.mode != 0 || options.stereoAod));
            }
            if (!currentSurface()) {
                release();
                return;
            }
            initMs = SystemClock.uptimeMillis() - began;
            consecutiveFrameErrors = 0;
            Log.i(Protocol.TAG, "GL ready variant=" + variant + " size=" + w + "x" + h + " initMs=" + initMs + " hz=" + targetRefreshHz);
            request();
        } catch (Throwable error) {
            fail(error);
        }
    }

    void state(EngineRegistry.Snapshot snapshot) {
        requestedState = snapshot;
        handler.post(() -> {
            if (snapshot.sequence < state.sequence) return;
            state = snapshot;
            // For non-shared sessions (Photo/Vivo), the opposite hidden slot must still consume
            // the state so it never stays stuck in a stale mode (e.g., Home stuck in HOME across
            // AOD, causing AOD->HOME unlock to see HOME->HOME with no animation).
            // The target slot about to become visible does NOT pre-start here; it anchors the
            // transition clock on its first visible frame in draw() to preserve full launch punch.
            boolean oppositeSlot = sharedMotionKey == null
                && ((homeInput && snapshot.mode == 0) || (!homeInput && snapshot.mode == 2));
            if (!preview && motionSession != null && (renderVisible() || oppositeSlot)) {
                try {
                    long nowNanos = System.nanoTime();
                    long eventNanos = eventNanosFromUptime(snapshot.startMs, nowNanos);
                    motionSession.frame(snapshot.sequence, snapshot.mode, eventNanos, nowNanos, () -> {});
                } catch (Throwable error) {
                    fail(error);
                }
            }
            try {
                applyOptions();
                applyPlacement();
                if (state.mode == 0 && !renderVisible()) clearStaleHomeBufferForAod();
                request();
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    void options(AliveRenderOptions value) {
        handler.post(() -> {
            try {
                options = value;
                applyOptions();
                applyPlacement();
                request();
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    void clock(ClockSnapshot value) {
        handler.post(() -> {
            try {
                clock = value;
                applyPlacement();
                request();
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    private void applyOptions() {
        configuredBrightness = options.brightness;
        if (renderer == null) return;
        renderer.continuous(state.mode == 0 ? options.continuousAod : options.continuousHome);
        if (renderer.motion() instanceof StereoMotion) ((StereoMotion) renderer.motion()).configure(options.sensitivity, options.stereoAod);
        if (renderer.motion() instanceof InfinityMotion) {
            synchronized (motionSession) {
                ((InfinityMotion) renderer.motion()).configureInput(options.infinityGesture, options.infinityGyro);
            }
        }
        if (sensor != null) sensor.setActive(renderVisible() && (renderer.motion() instanceof InfinityMotion ? state.mode != 0 && options.infinityGyro : state.mode != 0 || options.stereoAod));
    }

    private void applyPlacement() {
        handler.removeCallbacks(minuteTick);
        if (renderer == null) return;
        boolean anchored = renderer instanceof PhotoRenderer || renderer.motion() instanceof CosmicMotion;
        boolean system = anchored && options.followClock && clock != null && clock.usable(width, height);
        float x = system ? clock.bounds.x() : 0, y = system ? clock.bounds.y() : .165f;
        // A live system snapshot already contains burn-in motion: never add it twice.
        if (!system && !preview && options.burnIn) {
            x += com.frederic22.hyperextreme.alive.core.AodPlacement.driftX(System.currentTimeMillis());
            y += com.frederic22.hyperextreme.alive.core.AodPlacement.driftY(System.currentTimeMillis());
        }
        presentationX = options.aodOffsetX + x;
        presentationY = options.aodOffsetY - (y - .165f);
        synchronized (motionSession) {
            if (renderer instanceof PhotoRenderer) ((PhotoRenderer) renderer).anchor(x, y, system);
            else if (renderer.motion() instanceof CosmicMotion) renderer.motion().follow(x + options.aodOffsetX, y - options.aodOffsetY);
        }
        if (renderVisible() && !preview && state.mode == 0) handler.postDelayed(minuteTick, 60000 - System.currentTimeMillis() % 60000);
    }

    void photoOptions(PhotoOptions options) {
        handler.post(() -> {
            if (preview) previewPhoto = options;
            if (renderer instanceof PhotoRenderer) {
                try {
                    ((PhotoRenderer) renderer).configure(options);
                    request();
                } catch (Throwable error) {
                    fail(error);
                }
            }
        });
    }

    void vivoOptions(VivoOptions value) {
        VivoOptions copy = VivoOptions.parse(value.json());
        handler.post(() -> {
            if (preview) previewVivo = copy;
            if (renderer instanceof VivoRenderer) {
                try {
                    ((VivoRenderer) renderer).configure(copy);
                    request();
                } catch (Throwable error) {
                    fail(error);
                }
            }
        });
    }

    void touch(int action, float x, float y) {
        long sequence = requestedState.sequence, generation = generations.get(), epoch = inputEpoch.get(), time = System.nanoTime();
        boolean wasVisible = requestedVisible;
        handler.post(() -> {
            try {
                if (renderer instanceof VivoRenderer) {
                    ((VivoRenderer) renderer).touch(action, x, y);
                    request();
                } else if (renderer != null && renderer.motion() instanceof InfinityMotion && infinityInput(sequence, generation, epoch, wasVisible)) {
                    synchronized (motionSession) {
                        ((InfinityMotion) renderer.motion()).touch(this, action, x, y, time);
                    }
                    request();
                }
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    private boolean infinityInput(long sequence, long generation, long epoch, boolean wasVisible) {
        return !closed && wasVisible && renderVisible() && (preview || displayInteractive && requestedInteractive)
            && generation == activeGeneration && generation == generations.get() && epoch == inputEpoch.get()
            && sequence == state.sequence && sequence == requestedState.sequence
            && state.mode != 0 && (preview || state.mode != 2 || homeInput);
    }

    void flashDemo(boolean value) {
        handler.post(() -> {
            if (preview && renderer instanceof VivoRenderer) {
                ((VivoRenderer) renderer).flashDemo(value);
                request();
            }
        });
    }

    void resumeDisplay() {
        handler.post(() -> {
            stopFrames();
            recoverIfNeeded();
            request();
        });
    }

    void display(int value, boolean interactive) {
        if (!interactive) inputEpoch.incrementAndGet();
        requestedDisplay = value;
        requestedInteractive = interactive;
        handler.post(() -> {
            if (closed || displayState == value && displayInteractive == interactive) return;
            displayState = value;
            displayInteractive = interactive;
            updateVisibility();
            Log.i(Protocol.TAG, "render display=" + value + " interactive=" + interactive + " visible=" + visible);
        });
    }

    void visible(boolean value) {
        if (!value) inputEpoch.incrementAndGet();
        requestedVisible = value;
        handler.post(() -> {
            if (closed) return;
            visible = value;
            updateVisibility();
        });
    }

    private void updateVisibility() {
        boolean active = renderVisible();
        try {
            if ((!active || !preview && !displayInteractive) && renderer != null && renderer.motion() instanceof InfinityMotion) {
                synchronized (motionSession) {
                    ((InfinityMotion) renderer.motion()).cancelTouch(this);
                }
            }
            if (motionSession != null) motionSession.visible(this, active);
            if (renderer != null) renderer.visible(active);
            applyOptions();
            applyPlacement();
            stopFrames();
            if (active) {
                if (display != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE) applySwapInterval();
                recoverIfNeeded();
                request();
            } else {
                updateSurfaceFrameRateHint(false);
                handler.removeCallbacks(retry);
                retryPosted = false;
                clearStaleHomeBufferForAod();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void recoverIfNeeded() {
        if (renderer == null && !retryPosted && renderVisible() && currentSurface() && recovery.resume(SystemClock.uptimeMillis())) {
            retryPosted = true;
            handler.post(retry);
        }
    }

    /**
     * When the home slot hides on entering AOD, immediately overwrite its stale full-screen
     * desktop buffer with solid black (without disturbing motionSession) so fast fingerprint
     * unlock never flashes the previous desktop frame before the new unlock animation draws.
     */
    private void clearStaleHomeBufferForAod() {
        if (!homeInput || preview || renderVisible() || requestedState.mode != 0 || requestedInteractive) return;
        if (clearedAodSequence == requestedState.sequence) return;
        if (!currentSurface() || renderer == null || display == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return;
        if (!targetSurface.isValid() || !DisplayFramePolicy.canRender(false, requestedDisplay)) return;
        try {
            applySwapInterval();
            GLES30.glViewport(0, 0, width, height);
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
            GLES30.glColorMask(true, true, true, true);
            GLES30.glClearColor(0f, 0f, 0f, 1f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            if (EGL14.eglSwapBuffers(display, eglSurface)) {
                clearedAodSequence = requestedState.sequence;
                preparedGeneration = activeGeneration;
            } else {
                EGL14.eglGetError();
            }
        } catch (Throwable ignored) {}
    }

    void launcherOffset(float x, float y, float xStep, float yStep) {
        long sequence = requestedState.sequence, generation = generations.get(), epoch = inputEpoch.get(), time = System.nanoTime();
        boolean wasVisible = requestedVisible;
        handler.post(() -> {
            try {
                if (renderer == null) return;
                if (renderer.motion() instanceof InfinityMotion) {
                    if (!infinityInput(sequence, generation, epoch, wasVisible) || state.mode != 2) return;
                    // A zero page step is a single-page axis, often reported with a default offset of zero.
                    synchronized (motionSession) {
                        ((InfinityMotion) renderer.motion()).follow(
                            InfinityLauncherOffset.centered(x, xStep),
                            InfinityLauncherOffset.centered(y, yStep),
                            time);
                    }
                    request();
                } else applyOffset(x - .5f, y - .5f);
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    void touchOffset(float x, float y) {
        handler.post(() -> {
            try {
                // Infinity receives relative drags through touch(); never reinterpret them as launcher offsets.
                if (renderer != null && !(renderer.motion() instanceof InfinityMotion)) applyOffset(x, y);
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    private void applyOffset(float x, float y) {
        if (renderer != null) {
            // Cosmic.follow means AOD clock anchor, not launcher scrolling or touch coordinates.
            if (!(renderer.motion() instanceof CosmicMotion) && !(renderer.motion() instanceof GradientMotion)) {
                synchronized (motionSession) {
                    renderer.motion().follow(x, y);
                }
            }
            renderer.interaction();
            request();
        }
    }

    void detach() {
        generations.incrementAndGet();
        handler.post(() -> {
            targetSurface = null;
            release();
        });
    }

    void close() {
        closed = true;
        generations.incrementAndGet();
        handler.post(() -> {
            targetSurface = null;
            release();
            thread.quitSafely();
        });
    }

    private void request() {
        if (Looper.myLooper() != handler.getLooper()) {
            handler.post(this::request);
            return;
        }
        if (preparing && !renderVisible()) {
            if (canPrepare() && SystemClock.uptimeMillis() < preparationUntil) draw(true);
            else preparing = false;
            return;
        }
        if (!currentSurface() || !renderVisible() || renderer == null) return;
        handler.removeCallbacks(pace);
        handler.post(pace);
    }

    private void stopFrames() {
        handler.removeCallbacks(pace);
    }

    private void draw(boolean prepare) {
        if (!currentSurface() || renderer == null) return;
        if (prepare ? !canPrepare() : !canPresent()) {
            discardedFrames++;
            frameStage = "discarded";
            return;
        }
        if (!prepare) {
            preparing = false;
        }
        long frameStartNanos = System.nanoTime();
        try {
            frameStage = "render";
            if (!targetSurface.isValid()) throw new IllegalStateException("Surface lost before draw");
            applySwapInterval();
            long eventAgeMs = state.startMs > 0 ? Math.max(0L, SystemClock.uptimeMillis() - state.startMs) : 0L;
            // Convert Snapshot.startMs (uptimeMillis) into System.nanoTime() domain so deep-sleep
            // clock drift never clamps MotionSession back to an old lastFrame and skips the first
            // half of the animation. For fresh live transitions (< 300ms), anchor at frame 0.
            long effectiveEventNanos = (!prepare && eventAgeMs < 300L)
                ? frameStartNanos : eventNanosFromUptime(state.startMs, frameStartNanos);
            if (!motionSession.frame(state.sequence, state.mode, effectiveEventNanos, frameStartNanos, renderer::render)) {
                discardedFrames++;
                frameStage = "discarded";
                return;
            }
            frameStage = renderer.frameReady() ? "presentation" : "waiting-assets";
            float progress = prepare ? 1 : Math.max(0, Math.min(1, (SystemClock.uptimeMillis() - state.startMs) / 700f));
            if (!prepare && urgentFrame(progress)) {
                updateSurfaceFrameRateHint(true);
            }
            // Families with native anchors preserve their full background. Other families use
            // the presentation pass, fading the displacement out before lock/home settles.
            float aodWeight = (state.from == 0 ? 1 : 0) + ((state.mode == 0 ? 1 : 0) - (state.from == 0 ? 1 : 0)) * progress;
            boolean translate = !(renderer instanceof PhotoRenderer) && !(renderer.motion() instanceof CosmicMotion);
            // Keep the scene/placement transition, but never boost the restored lock/home.
            // The display wake can precede the asynchronous scene signal.
            boolean ambient = state.mode == 0 && (preview || !requestedInteractive);
            float exposure = AodBrightness.frameGain(options.brightness, aodWeight, ambient);
            if (renderer.frameReady()) presentation.render(exposure,
                translate ? presentationX * aodWeight : 0, translate ? presentationY * aodWeight : 0);
            int glError = GLES30.glGetError();
            if (glError != GLES30.GL_NO_ERROR) throw new IllegalStateException("GL error 0x" + Integer.toHexString(glError));
            if (renderer.frameReady()) {
                // A main-thread hide/OFF/new scene may arrive while render() is running.
                // Do not enqueue that obsolete frame or block on its hidden BufferQueue.
                if (prepare ? !canPrepare() : !canPresent()) {
                    preparing = false;
                    discardedFrames++;
                    frameStage = "discarded";
                    return;
                }
                frameStage = "swap";
                lastSwapStartedMs = SystemClock.uptimeMillis();
                if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                    int eglErr = EGL14.eglGetError();
                    if (eglErr == EGL14.EGL_CONTEXT_LOST || eglErr == EGL14.EGL_BAD_SURFACE || !targetSurface.isValid()) {
                        throw new IllegalStateException("swap EGL=0x" + Integer.toHexString(eglErr));
                    }
                    Log.w(Protocol.TAG, "transient swap failure EGL=0x" + Integer.toHexString(eglErr));
                    if (++consecutiveFrameErrors >= 3) {
                        throw new IllegalStateException("repeated swap EGL=0x" + Integer.toHexString(eglErr));
                    }
                    if (!prepare) paceAfter(frameStartNanos, progress);
                    return;
                }
                consecutiveFrameErrors = 0;
                lastFrameAtMs = SystemClock.uptimeMillis();
                long swapMs = lastFrameAtMs - lastSwapStartedMs;
                if (swapMs >= 100 && swapMs > maxSwapMs) {
                    Log.w(Protocol.TAG, "slow swap ms=" + swapMs + " mode=" + state.mode + " requested=" + requestedState.mode
                        + " visible=" + requestedVisible + " display=" + requestedDisplay + " interval=" + swapInterval);
                }
                maxSwapMs = Math.max(maxSwapMs, swapMs);
                frameStage = "submitted";
                if (!currentSurface()) return;
                if (firstFrameMs < 0) {
                    firstFrameMs = SystemClock.uptimeMillis() - attachedAt;
                    Log.i(Protocol.TAG, "first frame generation=" + activeGeneration + " firstMs=" + firstFrameMs + " initMs=" + initMs + " mode=" + state.mode + " prepared=" + prepare);
                }
                recovery.drawn(SystemClock.uptimeMillis());
                error = "";
                if (!preview) lastError = "";
                renderedGeneration = activeGeneration;
                renderedExposure = exposure;
                if (!preview && pendingLiquidMap != null) {
                    liquidMap = pendingLiquidMap;
                    pendingLiquidMap = null;
                    notifyConfig();
                }
                renderedFrames++;
                long n = preview ? renderedFrames : frames.incrementAndGet();
                if (n % 300 == 1) Log.i(Protocol.TAG, "frame=" + n + " mode=" + state.mode + " preview=" + preview);
                if (prepare) {
                    preparedGeneration = activeGeneration;
                    preparing = false;
                } else if (!preview && (state.mode == 0 || state.mode == 2)) {
                    boolean settled = progress >= 1 && (renderer instanceof PhotoRenderer
                        ? (state.mode == 2 ? !((PhotoRenderer) renderer).frameAnimating() : !((PhotoRenderer) renderer).animating()) && !((PhotoRenderer) renderer).needsRedraw()
                        : renderer.motion() instanceof InfinityMotion ? !renderer.motion().active()
                        : SystemClock.uptimeMillis() - state.startMs >= 2500);
                    // Only refresh the hidden companion slot once the active transition has settled.
                    // Refreshing mid-transition writes a partially-expanded/collapsed frame into
                    // the hidden buffer, causing a visible flash on fast double-tap sleep or unlock.
                    if (settled && companionSettledSequence != state.sequence) {
                        companionSettledSequence = state.sequence;
                        EngineRegistry.MAIN.post(homeReady);
                    }
                }
            }
            if (prepare) return;
            paceAfter(frameStartNanos, progress);
        } catch (Throwable error) {
            handleDrawError(error, frameStartNanos, prepare);
        }
    }

    /**
     * Isolate transient single-frame errors (modeled after HyperAlive LensRenderer) so a minor
     * glitch does not tear down and recreate the entire EGL context and cause a black flash.
     */
    private void handleDrawError(Throwable error, long frameStartNanos, boolean prepare) {
        boolean fatal = targetSurface == null || !targetSurface.isValid()
            || display == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT
            || (error.getMessage() != null && (error.getMessage().contains("EGL_CONTEXT_LOST")
                || error.getMessage().contains("0x300e")
                || error.getMessage().contains("Surface lost")
                || error.getMessage().contains("mask decode failed")));
        if (fatal || ++consecutiveFrameErrors >= 3) {
            fail(error);
            return;
        }
        Log.w(Protocol.TAG, "Transient draw error (skipping frame " + consecutiveFrameErrors + ")", error);
        if (!prepare && currentSurface() && renderVisible() && renderer != null) {
            paceAfter(frameStartNanos, 0f);
        }
    }

    /**
     * Hardware-VSync-first frame pacing modeled after HyperAlive LensRenderer:
     * - Awake/preview uses eglSwapInterval(1): if swap already blocked >= 2.5ms, post immediately
     *   for the next VSync without Choreographer/timer jitter.
     * - Active transitions request peak refresh rate on Android 11+ Surface.setFrameRate so LTPO
     *   panels do not stall at 30/60Hz waking from DOZE.
     * - DOZE uses eglSwapInterval(0) + bounded frame-budget delay so suspended consumers never hang.
     */
    private void paceAfter(long frameStartNanos, float progress) {
        if (!currentSurface() || !renderVisible() || renderer == null) {
            updateSurfaceFrameRateHint(false);
            return;
        }
        boolean urgent = urgentFrame(progress);
        boolean coast = !urgent && coastFrame();
        updateSurfaceFrameRateHint(urgent);
        if (!urgent && !coast) return;

        refreshFramePacingIfNeeded();
        long spentNanos = System.nanoTime() - frameStartNanos;
        handler.removeCallbacks(pace);
        long delayMs;
        if (swapShouldBlock()) {
            if (urgent) {
                // With eglSwapInterval(1) active, let SurfaceFlinger's hardware VSync throttle
                // every transition frame directly — adding postDelayed() on top of VSync causes
                // fast GPU frames (<2.5ms) to miss the next 8.33ms VSync edge and stutter at 60fps.
                delayMs = 0L;
            } else {
                long remainMs = 33L - (spentNanos / 1_000_000L);
                delayMs = Math.max(0L, remainMs);
            }
        } else {
            if (urgent) {
                // Early AOD collapse on HyperOS still runs on a 120Hz STATE_ON panel;
                // pace at the display's real frame interval instead of capping at 60Hz (16ms).
                long targetNs = displayState == Display.STATE_ON ? frameIntervalNanos : 16_666_666L;
                long remainNanos = targetNs - spentNanos - 1_500_000L;
                delayMs = remainNanos <= 500_000L ? 0L : remainNanos / 1_000_000L;
            } else {
                long remainMs = 33L - (spentNanos / 1_000_000L);
                delayMs = Math.max(1L, remainMs);
            }
        }
        if (delayMs <= 0L) handler.post(pace);
        else handler.postDelayed(pace, delayMs);
    }

    private boolean urgentFrame(float progress) {
        if (renderer instanceof PhotoRenderer) {
            PhotoRenderer photo = (PhotoRenderer) renderer;
            return photo.frameReady() && (photo.needsRedraw() || photo.animating() || progress < 1);
        }
        if (renderer.motion() instanceof InfinityMotion) {
            return renderer.motion().active() || progress < 1;
        }
        return renderer.motion().active() || progress < 1 || SystemClock.uptimeMillis() - state.startMs < 2500;
    }

    private boolean coastFrame() {
        if (renderer instanceof PhotoRenderer) {
            PhotoRenderer photo = (PhotoRenderer) renderer;
            return photo.frameReady() && options.continuousAod && photo.motion().active() && !photo.animating() && !photo.needsRedraw();
        }
        if (renderer.motion() instanceof InfinityMotion) return false;
        return state.mode == 0 ? options.continuousAod : options.continuousHome;
    }

    private static long eventNanosFromUptime(long startMs, long nowNanos) {
        if (startMs <= 0) return nowNanos;
        long ageMs = Math.max(0L, SystemClock.uptimeMillis() - startMs);
        return nowNanos - ageMs * 1_000_000L;
    }

    private boolean swapShouldBlock() {
        // Keep hardware VSync (eglSwapInterval=1) active whenever the panel is still STATE_ON,
        // including early AOD collapse before entering STATE_DOZE, for rock-solid 120Hz pacing.
        return preview || displayState == Display.STATE_ON || displayState == Display.STATE_UNKNOWN;
    }

    private void applySwapInterval() {
        int want = swapShouldBlock() ? 1 : 0;
        if (swapIntervalApplied && swapInterval == want) return;
        if (!EGL14.eglSwapInterval(display, want)) {
            EGL14.eglGetError();
            return;
        }
        swapInterval = want;
        swapIntervalApplied = true;
    }

    private void refreshFramePacingIfNeeded() {
        long now = SystemClock.uptimeMillis();
        if (now - lastFramePacingCheckMs >= 1000L) {
            updateFramePacing(false);
        }
    }

    private void updateFramePacing(boolean logIt) {
        lastFramePacingCheckMs = SystemClock.uptimeMillis();
        float hz = 120f;
        try {
            DisplayManager dm = context.getSystemService(DisplayManager.class);
            Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null) {
                float curHz = d.getRefreshRate();
                float maxSupportedHz = curHz;
                Display.Mode[] modes = d.getSupportedModes();
                if (modes != null) {
                    for (Display.Mode m : modes) {
                        if (m != null && m.getRefreshRate() > maxSupportedHz) {
                            maxSupportedHz = m.getRefreshRate();
                        }
                    }
                }
                float userCapHz = maxSupportedHz;
                try {
                    ContentResolver cr = context.getContentResolver();
                    int ur = Settings.System.getInt(cr, "user_refresh_rate", -1);
                    if (ur <= 0) ur = Settings.Secure.getInt(cr, "user_refresh_rate", -1);
                    if (ur <= 0) ur = Math.round(Settings.System.getFloat(cr, "peak_refresh_rate", -1f));
                    if (ur >= 60) userCapHz = Math.min(maxSupportedHz, (float) ur);
                } catch (Throwable ignored) {}
                hz = Math.max(curHz, userCapHz);
                if (hz < 60f) hz = 120f;
            }
        } catch (Throwable ignored) {}
        targetRefreshHz = hz;
        frameIntervalNanos = Math.max(4_000_000L, Math.round(1_000_000_000d / hz));
        if (logIt) {
            Log.i(Protocol.TAG, "frame pacing target=" + hz + "Hz intervalMs=" + String.format(java.util.Locale.US, "%.2f", frameIntervalNanos / 1_000_000f));
        }
    }

    private void updateSurfaceFrameRateHint(boolean wantHigh) {
        if (Build.VERSION.SDK_INT < 30 || targetSurface == null || !targetSurface.isValid()) {
            highFrameRateRequested = false;
            return;
        }
        if (wantHigh == highFrameRateRequested) return;
        highFrameRateRequested = wantHigh;
        try {
            targetSurface.setFrameRate(wantHigh ? targetRefreshHz : 0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        } catch (Throwable ignored) {}
    }

    private void fail(Throwable error) {
        frameStage = "failed";
        this.error = lastFailure = error.toString();
        if (!preview) lastError = this.error;
        Log.e(Protocol.TAG, "Render failure generation=" + activeGeneration, error);
        long delay = recovery.failed(SystemClock.uptimeMillis());
        release();
        if (delay >= 0 && renderVisible() && currentSurface()) {
            retryPosted = true;
            handler.postDelayed(retry, delay);
        }
    }

    private void notifyConfig() {
        // A transient IPC failure must never tear down an already submitted wallpaper frame.
        EngineRegistry.MAIN.post(() -> {
            try {
                context.getContentResolver().notifyChange(Protocol.CONFIG, null);
            } catch (RuntimeException error) {
                Log.w(Protocol.TAG, "Config notification unavailable", error);
            }
        });
    }

    private static void check(boolean ok, String operation) {
        if (!ok) throw new IllegalStateException(operation + " EGL=0x" + Integer.toHexString(EGL14.eglGetError()));
    }

    // EGL_DEFAULT_DISPLAY is process-wide. Closing a preview must not terminate both wallpaper slots.
    static synchronized EGLDisplay acquireDisplay() {
        if (displayUsers == 0) {
            EGLDisplay candidate = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            check(EGL14.eglInitialize(candidate, version, 0, version, 1), "initialize");
            sharedDisplay = candidate;
        }
        displayUsers++;
        return sharedDisplay;
    }

    static synchronized void releaseDisplay() {
        if (--displayUsers == 0) {
            EGL14.eglTerminate(sharedDisplay);
            sharedDisplay = EGL14.EGL_NO_DISPLAY;
        }
    }

    private void release() {
        handler.removeCallbacks(preparation);
        preparationPosted = false;
        preparing = false;
        companionFirstSequence = companionSettledSequence = clearedAodSequence = -1;
        preparedGeneration = 0;
        handler.removeCallbacks(retry);
        retryPosted = false;
        boolean hadMap = liquidMap != null;
        liquidMap = null;
        pendingLiquidMap = null;
        if (hadMap && !preview) notifyConfig();
        stopFrames();
        updateSurfaceFrameRateHint(false);
        handler.removeCallbacks(minuteTick);
        if (presentation != null) {
            try { presentation.close(); } catch (RuntimeException e) { Log.w(Protocol.TAG, "presentation cleanup", e); }
            presentation = null;
        }
        if (sensor != null) {
            try { sensor.close(); } catch (RuntimeException e) { Log.w(Protocol.TAG, "sensor cleanup", e); }
            sensor = null;
        }
        if (motionSession != null) {
            if (renderer != null && renderer.motion() instanceof InfinityMotion) {
                synchronized (motionSession) {
                    ((InfinityMotion) renderer.motion()).cancelTouch(this);
                }
            }
            try {
                if (sharedMotionKey != null) MotionSession.release(sharedMotionKey, motionSession, this);
                else motionSession.visible(this, false);
            } catch (RuntimeException e) {
                Log.w(Protocol.TAG, "motion cleanup", e);
            }
            motionSession = null;
        }
        if (renderer != null) {
            try { renderer.close(); } catch (RuntimeException e) { Log.w(Protocol.TAG, "renderer cleanup", e); }
            renderer = null;
        }
        sharedMotionKey = null;
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
            releaseDisplay();
            EGL14.eglReleaseThread();
        }
        display = EGL14.EGL_NO_DISPLAY;
        eglSurface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
        swapIntervalApplied = false;
        consecutiveFrameErrors = 0;
    }
}
