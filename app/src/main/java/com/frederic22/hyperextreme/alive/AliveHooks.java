package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import com.frederic22.hyperextreme.alive.core.AlivePermissionPolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** API 102 adapter owned by HookEntry. Never mixes legacy Xposed APIs into this APK. */
public final class AliveHooks {
    private static final Set<java.lang.reflect.Executable> INSTALLED = Collections.synchronizedSet(new HashSet<>());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Only Application.attach's base Context or ActivityThread's system Context, never an Activity.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static volatile Context context;
    private static volatile Bundle cached = Bundle.EMPTY;
    private static Handler ipc;
    private static long readAt = -750, errorAt;
    private static int refreshFailures;
    private static boolean observing;
    private static XposedModule logger;
    private static final ThreadLocal<AodWallpaperGuard.Panel> NORMAL_PANEL=new ThreadLocal<>();
    private static final AodWallpaperGuard AOD_WALLPAPER=new AodWallpaperGuard();
    private static final AodSessionBrightness AOD_BRIGHTNESS=new AodSessionBrightness();
    private static final ThreadLocal<AodCreation> AOD_CREATION=new ThreadLocal<>();
    private static final class AodCreation {
        final Object host;final boolean eligible;boolean denied;
        AodCreation(Object host,ClassLoader loader){
            this.host=host;Object c=field(host,"mContext");
            eligible=c instanceof Context&&AodWallpaperEligibility.allowed((Context)c,loader)
                &&Boolean.FALSE.equals(field(host,"mKeyguardOccluded"))&&Boolean.FALSE.equals(field(host,"mFingerprintPressed"));
        }
    }
    private static boolean aodGuardEnabled;
    private static final Runnable RETRY_CONFIG=()->{readAt=-750;refresh();};

    private static synchronized Handler worker() {
        if (ipc == null) {
            HandlerThread thread = new HandlerThread("HyperExtremeAliveIPC");
            thread.start();
            ipc = new Handler(thread.getLooper());
        }
        return ipc;
    }

    private static void log(String message) {
        Log.i(Protocol.TAG, message);
        if (logger != null) logger.log(Log.INFO, Protocol.TAG, message);
    }

    private static Context context() {
        if (context != null) return context;
        try {
            Class<?> type = Class.forName("android.app.ActivityThread");
            context = (Context) type.getMethod("currentApplication").invoke(null);
            // A system Context attributes Binder calls to "android". It is invalid in a
            // dedicated-UID SystemUI process; wait for Application.attach there instead.
            if (context == null && systemContextAllowed(android.os.Process.myUid())) {
                Object thread = type.getMethod("currentActivityThread").invoke(null);
                if (thread != null) context = (Context) type.getMethod("getSystemContext").invoke(thread);
            }
        } catch (ReflectiveOperationException ignored) { }
        return context;
    }

    static boolean systemContextAllowed(int uid) { return uid >= 0 && uid % 100000 == 1000; }
    /** Reuse the observed config for hook layers; no Binder work on the UI thread. */
    public static float aodBrightness(){return AodBrightness.normalize(cached.getFloat(AodBrightness.KEY,AodBrightness.DEFAULT));}

    // All Provider calls run off the system service / UI hook thread (which may hold locks).
    private static Bundle refresh() {
        long now = SystemClock.uptimeMillis();
        if (now - readAt < 750) return cached;
        readAt = now;
        boolean retry=false;
        try {
            Context c = context();
            if (c == null) { retryRefresh();return cached; }
            if (!observing) {
                c.getContentResolver().registerContentObserver(Protocol.CONFIG, false,
                    new ContentObserver(worker()) {
                        @Override public void onChange(boolean selfChange) {
                            readAt = -750;refreshFailures=0;
                            refresh();
                        }
                    });
                observing = true;
            }
            Bundle result = c.getContentResolver().call(Protocol.CONFIG, "getConfig", null, null);
            cached = result == null ? Bundle.EMPTY : result;
            retry=result==null||cached.getBoolean("configured",false)&&!cached.getBoolean("enabled",false);
            LiquidKeyguardHooks.config(cached);
        } catch (RuntimeException error) {
            cached = Bundle.EMPTY;
            LiquidKeyguardHooks.config(cached);
            retry=true;
            if (now - errorAt > 30000) {
                errorAt = now;
                log("Alive 配置读取失败：" + error);
            }
        }
        if(retry)retryRefresh();else {refreshFailures=0;worker().removeCallbacks(RETRY_CONFIG);}
        // Initial reads and retries must reconcile a panel too, not only observer notifications.
        MAIN.post(() -> {
            AOD_WALLPAPER.refresh(cached.getBoolean("enabled",false));
        });
        return cached;
    }
    private static void retryRefresh(){
        worker().removeCallbacks(RETRY_CONFIG);
        long[] delays={150,500,1500};
        if(refreshFailures<delays.length)worker().postDelayed(RETRY_CONFIG,delays[refreshFailures++]);
    }

    private static void signal(int mode, String reason) {
        signal(mode,reason,SystemClock.uptimeMillis());
    }
    private static void signal(int mode, String reason, long at) {
        worker().post(() -> {
            if (!refresh().getBoolean("enabled", false)) return;
            try {
                Bundle event = new Bundle();
                event.putInt("mode", mode);
                event.putLong("at", at);
                event.putString("reason", reason);
                Context c = context();
                if (c != null) c.getContentResolver().call(Protocol.CONFIG, "signal", null, event);
            } catch (RuntimeException error) { log("Alive 状态发送失败：" + error); }
        });
    }
    private static void observeClock(android.view.View view){
        SystemClockTracker.watch(view,()->cached.getBoolean("enabled",false),geometry->worker().post(()->{
            if(!refresh().getBoolean("enabled",false))return;
            try{Context c=context();if(c!=null)c.getContentResolver().call(Protocol.CONFIG,"clockGeometry",null,geometry);}
            catch(RuntimeException error){log("Alive clock geometry unavailable: "+error);}
        }));
    }

    private interface Interceptor { Object invoke(XposedInterface.Chain chain) throws Throwable; }

    private static boolean hook(XposedModule module, ClassLoader loader, String name, String method,
                             Interceptor interceptor) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            int count = 0;
            for (Method m : type.getDeclaredMethods()) {
                if (!m.getName().equals(method) || INSTALLED.contains(m)) continue;
                module.hook(m).intercept(interceptor::invoke);
                INSTALLED.add(m);
                count++;
            }
            if (count > 0) log("Alive hook：" + name + "#" + method + " ×" + count);
            return count>0;
        } catch (Throwable error) { log("Alive 可选 hook 不可用：" + name + "#" + method + " / " + error);return false; }
    }

    public static void installSystem(XposedModule module, ClassLoader loader) {
        logger = module;
        hook(module,loader,"android.hardware.display.AmbientDisplayConfiguration","alwaysOnAvailable",chain->{
            return ForcedAodSupport.active(context()) ? true : chain.proceed();
        });
        log("Alive system_server adapter v2 starting");
        // WPMS checks the service package, not the UID of the wallpaper-setting helper.
        // Keep this available before enabled=true, otherwise first application cannot succeed.
        hook(module, loader, "com.android.server.pm.PackageManagerService", "checkPermission", chain -> {
            java.util.List<Object> args = chain.getArgs();
            if (((Method) chain.getExecutable()).getReturnType() == int.class && args.size() == 3
                    && args.get(0) instanceof String && args.get(1) instanceof String
                    && AlivePermissionPolicy.grants((String) args.get(0), (String) args.get(1))) {
                return android.content.pm.PackageManager.PERMISSION_GRANTED;
            }
            return chain.proceed();
        });
        for (String type : new String[]{"com.android.server.power.PowerManagerService",
                "com.android.server.power.PowerManagerService$BinderService"}) {
            for (String method : new String[]{"goToSleep", "goToSleepInternal", "goToSleepGroup", "wakeUp", "wakeUpInternal", "wakePowerGroup"}) {
                hook(module, loader, type, method, chain -> {
                    boolean sleep = method.startsWith("goToSleep");
                    boolean biometric = false;
                    for (Object arg : chain.getArgs()) {
                        if (arg instanceof String) {
                            String detail = ((String) arg).toLowerCase(Locale.ROOT);
                            biometric |= detail.contains("biometric") || detail.contains("fingerprint");
                        }
                    }
                    signal(sleep ? 0 : biometric ? 2 : 1,
                        sleep ? "screen-off" : biometric ? "biometric" : "power-wake");
                    return chain.proceed();
                });
            }
        }
        // No global permission bypass, package ownership bypass or LSPosed database writes.
    }

    public static void installUi(XposedModule module, ClassLoader loader) {
        logger = module;
        hook(module,loader,"com.android.keyguard.panel.KeyguardPanelViewController","doWallpaperBlackAnim",chain->{
            float brightness=aodBrightness();
            if(!SystemAodDimming.contract((Method)chain.getExecutable()))return chain.proceed();
            boolean owned=brightness>0&&SystemAodDimming.ownsFullAod(chain.getThisObject());
            AOD_BRIGHTNESS.wallpaper(chain.getThisObject(),chain.getArgs(),brightness,owned);
            Object[] args=SystemAodDimming.heldArguments(chain.getThisObject(),chain.getArgs(),brightness,owned);
            if(args==null)return chain.proceed();
            log("AOD wallpaper mask="+chain.getArgs().get(1)+" -> "+args[1]+" brightness="+brightness);
            // Keep native animation, wake-lock cleanup, depth and wake restoration callbacks.
            return chain.proceed(args);
        });
        String dozeService="com.android.systemui.doze.DozeService";
        hook(module,loader,dozeService,"setDozeScreenBrightness",chain->{
            java.util.List<Object> args=chain.getArgs();
            if(args.size()!=1||!(args.get(0) instanceof Float))return chain.proceed();
            android.os.PowerManager power=((Context)chain.getThisObject()).getSystemService(android.os.PowerManager.class);
            if(power==null||power.isInteractive()){AOD_BRIGHTNESS.clear();return chain.proceed();}
            float original=(Float)args.get(0),held=AOD_BRIGHTNESS.panel(original,aodBrightness());
            if(held==original)return chain.proceed();
            log("AOD panel brightness="+original+" -> "+held);
            return chain.proceed(new Object[]{held});
        });
        hook(module,loader,dozeService,"setDozeScreenState",chain->{
            if(chain.getArgs().size()==1&&Integer.valueOf(android.view.Display.STATE_OFF).equals(chain.getArgs().get(0)))
                AOD_BRIGHTNESS.clear();
            return chain.proceed();
        });
        hook(module,loader,dozeService,"onDreamingStopped",chain->{AOD_BRIGHTNESS.clear();return chain.proceed();});
        hook(module,loader,"com.android.systemui.statusbar.phone.DozeServiceHost","setAodDimmingScrim",chain->{
            java.util.List<Object> args=chain.getArgs();
            if(args.size()!=1||!(args.get(0) instanceof Float))return chain.proceed();
            float original=(Float)args.get(0),scrim=AOD_BRIGHTNESS.scrim(original,aodBrightness());
            if(scrim==original)return chain.proceed();
            log("AOD ambient scrim="+original+" -> "+scrim);
            return chain.proceed(new Object[]{scrim});
        });
        hook(module,loader,"miui.util.FeatureParser","getBoolean",chain->{
            if(chain.getArgs().size()==2 && chain.getArgs().get(0) instanceof String
                    && ForcedAodSupport.feature((String)chain.getArgs().get(0)) && ForcedAodSupport.active(context())) return true;
            return chain.proceed();
        });
        hook(module,loader,"android.hardware.display.AmbientDisplayConfiguration","alwaysOnAvailable",chain->{
            return ForcedAodSupport.active(context()) ? true : chain.proceed();
        });
        for(String[] contract:new String[][]{
                {"com.miui.aod.Utils","supportAod"},{"com.miui.aod.Utils","isSupportFsAod"},
                {"com.miui.aod.Utils","isInnerScreenSupportFsAod"},{"com.miui.aod.widget.AODSettings","isSupportAodWallpaper"}})
            hook(module,loader,contract[0],contract[1],chain->ForcedAodSupport.active(context()) ? true : chain.proceed());
        // Recompute on every settings update so removing our wallpaper restores the stock decision.
        String fullAod="com.android.keyguard.fullaod.MiuiFullAodManager";
        try {
            for(java.lang.reflect.Constructor<?> constructor:Class.forName(fullAod,false,loader).getDeclaredConstructors()) {
                if(INSTALLED.contains(constructor)) continue;
                module.hook(constructor).intercept(chain->{
                    Context c=null;
                    for(Object arg:chain.getArgs()) if(arg instanceof Context) { c=(Context)arg; break; }
                    if(ForcedAodSupport.active(c)) {
                        try {
                            ForcedAodSupport.prepare(loader);
                            log("Alive force AOD constructor flags verified");
                        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                            log("Alive force AOD preparation unavailable: "+unavailable);
                        }
                    }
                    return chain.proceed();
                });
                module.deoptimize(constructor);
                INSTALLED.add(constructor);
            }
        } catch(ReflectiveOperationException unavailable) { log("Alive force AOD constructor unavailable: "+unavailable); }
        hook(module,loader,fullAod,"onWallpaperChanged",chain->{
            Object result=chain.proceed();
            try { FullAodCompatibility.reconcile(chain.getThisObject()); }
            catch(ReflectiveOperationException unavailable) { log("Alive full AOD contract unavailable: "+unavailable); }
            return result;
        });
        hook(module,loader,fullAod,"updateSettings",chain->{
            try { chain.getThisObject().getClass().getMethod("onWallpaperChanged").invoke(chain.getThisObject()); }
            catch(ReflectiveOperationException unavailable) { log("Alive full AOD refresh unavailable: "+unavailable); }
            return chain.proceed();
        });
        LiquidKeyguardHooks.install(module,loader,event->worker().post(()->{
            try{Context c=context();if(c!=null)c.getContentResolver().call(Protocol.CONFIG,"liquidTap",null,event);}
            catch(RuntimeException error){log("Liquid touch delivery failed: "+error);}
        }),AliveHooks::log);
        hook(module, Application.class.getClassLoader(), "android.app.Application", "attach", chain -> {
            Object result = chain.proceed();
            Object arg = chain.getArgs().get(0);
            if (arg instanceof Context) context = (Context) arg;
            worker().post(AliveHooks::refresh);
            return result;
        });
        worker().post(AliveHooks::refresh);
        // USER_PRESENT can arrive after HyperOS's entire ~800ms exit animation.
        // Observe accepted exit callbacks instead; never change keyguard/authentication state.
        hook(module,loader,"com.android.systemui.statusbar.policy.KeyguardStateControllerImpl","notifyKeyguardGoingAway",chain->{
            long at=SystemClock.uptimeMillis();
            boolean leaving=UnlockSignals.goingAway((Method)chain.getExecutable(),chain.getArgs());
            Object result=chain.proceed();
            if(leaving)signal(2,"keyguard-going-away",at);
            return result;
        });
        hook(module,loader,"com.android.systemui.keyguard.KeyguardViewMediator","startKeyguardExitAnimation",chain->{
            long at=SystemClock.uptimeMillis();
            Object result=chain.proceed();
            if(UnlockSignals.exitStart((Method)chain.getExecutable()))signal(2,"keyguard-going-away",at);
            return result;
        });
        hook(module,loader,"com.android.systemui.keyguard.KeyguardViewMediator","finishSurfaceBehindRemoteAnimation",chain->{
            boolean relock=UnlockSignals.cancelled((Method)chain.getExecutable(),chain.getArgs());
            Object result=chain.proceed();
            if(relock)signal(1,"keyguard-exit-cancelled");
            return result;
        });
        final String[] last = {""};
        hook(module, loader, "com.android.systemui.statusbar.phone.ScrimController", "updateScrims", chain -> {
            Object result = chain.proceed();
            String state = String.valueOf(field(chain.getThisObject(), "mState", "mScrimState"));
            if (!state.equals(last[0])) {
                last[0] = state;
                if (state.contains("AOD") || state.contains("PULSING")) signal(0, "scrim-aod");
                else if (state.contains("UNLOCKED")) signal(2, "scrim-unlocked");
                else if (state.contains("KEYGUARD")) signal(1, "scrim-keyguard");
                Object scrim=field(chain.getThisObject(),"mScrimBehind","mScrimInFront");
                if(scrim instanceof android.view.View)observeClock((android.view.View)scrim);
            }
            return result;
        });
        boolean wallpaperHooks=AodWallpaperGuard.contracts(loader);
        wallpaperHooks &= hook(module,loader,"com.miui.aod.DozeHost","showSecurityIdentityViewAt",chain->{
            if(!aodGuardEnabled||Looper.myLooper()!=Looper.getMainLooper())return chain.proceed();
            AodCreation previous=AOD_CREATION.get(),creation=new AodCreation(chain.getThisObject(),loader);
            AOD_CREATION.set(creation);
            try {
                Object result=chain.proceed();
                if(!creation.denied&&result instanceof android.view.View)
                    AOD_WALLPAPER.created((android.view.View)result,creation.host,
                        root->AodWallpaperEligibility.allowed(root.getContext(),loader));
                worker().post(()->{readAt=-750;refreshFailures=0;refresh();});
                return result;
            }finally{if(previous==null)AOD_CREATION.remove();else AOD_CREATION.set(previous);}
        });
        for(String[] contract:new String[][]{
            {"com.miui.aod.Utils","isUsingSuperWallpaperStyle"},
            {"com.miui.aod.util.WallPaperUtils","isWallpaperSupportsAmbientMode"}})
            wallpaperHooks &= hook(module,loader,contract[0],contract[1],chain->{
                // Classification is local to the stock creation call. Its hardware/AOD gates still run.
                AodCreation creation=AOD_CREATION.get();
                if(aodGuardEnabled&&AodWallpaperEligibility.classify(creation!=null&&creation.eligible,cached.getBoolean("enabled",false))
                        &&((Method)chain.getExecutable()).getReturnType()==boolean.class)return true;
                return chain.proceed();
            });
        wallpaperHooks &= hook(module, loader, "com.miui.aod.DozeHost", "disableShowWallpaper", chain -> {
            // This ROM's method returns void and paints an opaque background. Suppress only
            // the normal-clock template's call when the system already permitted wallpaper.
            if(aodGuardEnabled&&Looper.myLooper()==Looper.getMainLooper()) {
                AodWallpaperGuard.Panel panel=NORMAL_PANEL.get();
                if(panel==null||panel.host.get()!=chain.getThisObject()){
                    AOD_WALLPAPER.revoke(chain.getThisObject());
                    AodCreation creation=AOD_CREATION.get();
                    if(creation!=null&&creation.host==chain.getThisObject())creation.denied=true;
                }
                else if(AOD_WALLPAPER.normalDisable(panel,chain.getThisObject(),cached.getBoolean("enabled",false)))return null;
            }
            return chain.proceed();
        });
        wallpaperHooks &= hook(module, loader, "com.miui.aod.AODView", "makeNormalPanel", chain -> {
            if(!aodGuardEnabled||Looper.myLooper()!=Looper.getMainLooper()||!(chain.getThisObject() instanceof android.view.View))return chain.proceed();
            AodWallpaperGuard.Panel panel=AOD_WALLPAPER.begin((android.view.View)chain.getThisObject()),previous=NORMAL_PANEL.get();
            NORMAL_PANEL.set(panel);
            try{
                Object result=chain.proceed();
                AOD_WALLPAPER.finish(panel,cached.getBoolean("enabled",false));
                worker().post(()->{readAt=-750;refreshFailures=0;refresh();});
                return result;
            }finally{if(previous==null)NORMAL_PANEL.remove();else NORMAL_PANEL.set(previous);}
        });
        for(String method:new String[]{"onStopDoze","screenTurnOn","removeAODView"})
            wallpaperHooks &= hook(module,loader,"com.miui.aod.DozeHost",method,chain->{
                if(aodGuardEnabled&&Looper.myLooper()==Looper.getMainLooper())AOD_WALLPAPER.revoke(chain.getThisObject());
                return chain.proceed();
            });
        wallpaperHooks &= hook(module,loader,"com.miui.aod.AODView","onDetachedFromWindow",chain->{
            if(aodGuardEnabled&&chain.getThisObject() instanceof android.view.View)AOD_WALLPAPER.detach((android.view.View)chain.getThisObject());
            return chain.proceed();
        });
        for(String method:new String[]{"setKeyguardOccluded","setAodVisibility","fireFingerprintPressed","onFingerprintPressed"})
            wallpaperHooks &= hook(module,loader,"com.miui.aod.DozeHost",method,chain->{
                Object result=chain.proceed();
                MAIN.post(()->AOD_WALLPAPER.refresh(cached.getBoolean("enabled",false)));
                return result;
            });
        aodGuardEnabled=wallpaperHooks;
        log("Alive AOD wallpaper guard="+aodGuardEnabled);
        for (String name : new String[]{"com.miui.aod.AODView", "com.android.keyguard.widget.AodView",
                "com.miui.aod.linkagestyle.LinkageStyleClockView", "com.android.keyguard.KeyguardStatusView",
                "com.android.keyguard.clock.KeyguardClockContainer", "com.android.keyguard.KeyguardClockContainer"}) {
            hook(module, loader, name, "onAttachedToWindow", chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof ViewGroup) {
                    ViewGroup root = (ViewGroup) chain.getThisObject();
                    observeClock(root);
                    worker().post(() -> { refresh(); MAIN.post(() -> {
                        AOD_WALLPAPER.refresh(cached.getBoolean("enabled",false));
                    }); });
                }
                return result;
            });
        }
    }

    private static Object field(Object host, String... names) {
        if (host == null) return null;
        for (Class<?> type = host.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : names) try {
                java.lang.reflect.Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(host);
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

}
