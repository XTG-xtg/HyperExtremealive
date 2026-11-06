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
import android.widget.TextClock;
import com.frederic22.hyperextreme.alive.core.AlivePermissionPolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** API 102 adapter owned by HookEntry. Never mixes legacy Xposed APIs into this APK. */
public final class AliveHooks {
    private static final Set<Method> INSTALLED = Collections.synchronizedSet(new HashSet<>());
    private static final Map<ViewGroup, WeakReference<TextClock>> CLOCKS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Only Application.attach's base Context or ActivityThread's system Context, never an Activity.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static volatile Context context;
    private static volatile Bundle cached = Bundle.EMPTY;
    private static Handler ipc;
    private static long readAt, errorAt;
    private static boolean observing;
    private static XposedModule logger;
    private static final ThreadLocal<Object> NORMAL_PANEL_HOST=new ThreadLocal<>();

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
            Object thread = type.getMethod("currentActivityThread").invoke(null);
            if (thread != null) context = (Context) type.getMethod("getSystemContext").invoke(thread);
        } catch (ReflectiveOperationException ignored) { }
        return context;
    }

    // All Provider calls run off the system service / UI hook thread (which may hold locks).
    private static Bundle refresh() {
        long now = SystemClock.uptimeMillis();
        if (now - readAt < 750) return cached;
        readAt = now;
        try {
            Context c = context();
            if (c == null) return Bundle.EMPTY;
            if (!observing) {
                c.getContentResolver().registerContentObserver(Protocol.CONFIG, false,
                    new ContentObserver(worker()) {
                        @Override public void onChange(boolean selfChange) {
                            readAt = 0;
                            refresh();
                            MAIN.post(() -> {
                                for (ViewGroup root : new java.util.ArrayList<>(CLOCKS.keySet())) {
                                    if (root != null) updateClock(root);
                                }
                            });
                        }
                    });
                observing = true;
            }
            Bundle result = c.getContentResolver().call(Protocol.CONFIG, "getConfig", null, null);
            cached = result == null ? Bundle.EMPTY : result;
        } catch (RuntimeException error) {
            cached = Bundle.EMPTY;
            if (now - errorAt > 30000) {
                errorAt = now;
                log("Alive 配置读取失败：" + error);
            }
        }
        return cached;
    }

    private static void signal(int mode, String reason) {
        long at = SystemClock.uptimeMillis();
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

    private static void hook(XposedModule module, ClassLoader loader, String name, String method,
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
        } catch (Throwable error) { log("Alive 可选 hook 不可用：" + name + "#" + method + " / " + error); }
    }

    public static void installSystem(XposedModule module, ClassLoader loader) {
        logger = module;
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
        hook(module, Application.class.getClassLoader(), "android.app.Application", "attach", chain -> {
            Object result = chain.proceed();
            Object arg = chain.getArgs().get(0);
            if (arg instanceof Context) context = (Context) arg;
            worker().post(AliveHooks::refresh);
            return result;
        });
        worker().post(AliveHooks::refresh);
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
        hook(module, loader, "com.miui.aod.DozeHost", "disableShowWallpaper", chain -> {
            // This ROM's method returns void and paints an opaque background. Suppress only
            // the normal-clock template's call when the system already permitted wallpaper.
            if (chain.getThisObject()==NORMAL_PANEL_HOST.get()&&keepAodWallpaper(chain.getThisObject()) &&
                ((Method) chain.getExecutable()).getReturnType() == void.class) return null;
            return chain.proceed();
        });
        hook(module, loader, "com.miui.aod.AODView", "makeNormalPanel", chain -> {
            Object host=field(chain.getThisObject(),"mHost"),previous=NORMAL_PANEL_HOST.get();
            NORMAL_PANEL_HOST.set(host);
            try{
                Object result=chain.proceed();
                if(keepAodWallpaper(host)&&chain.getThisObject() instanceof android.view.View)
                    ((android.view.View)chain.getThisObject()).setBackgroundColor(android.graphics.Color.TRANSPARENT);
                return result;
            }finally{if(previous==null)NORMAL_PANEL_HOST.remove();else NORMAL_PANEL_HOST.set(previous);}
        });
        for (String name : new String[]{"com.miui.aod.AODView", "com.android.keyguard.widget.AodView",
                "com.miui.aod.linkagestyle.LinkageStyleClockView", "com.android.keyguard.KeyguardStatusView",
                "com.android.keyguard.clock.KeyguardClockContainer", "com.android.keyguard.KeyguardClockContainer"}) {
            hook(module, loader, name, "onAttachedToWindow", chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof ViewGroup) {
                    ViewGroup root = (ViewGroup) chain.getThisObject();
                    observeClock(root);
                    worker().post(() -> { refresh(); MAIN.post(() -> updateClock(root)); });
                }
                return result;
            });
        }
    }

    private static boolean keepAodWallpaper(Object host){
        // Never enable an AOD session, modify its schedule, or hide a fingerprint/occlusion mask.
        return host!=null&&cached.getBoolean("enabled",false)
            &&Boolean.TRUE.equals(field(host,"mNeedShowWallpaper"))
            &&Boolean.FALSE.equals(field(host,"mKeyguardOccluded"))
            &&Boolean.FALSE.equals(field(host,"mFingerprintPressed"));
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

    private static void updateClock(ViewGroup root) {
        if (!root.isAttachedToWindow()) return;
        WeakReference<TextClock> reference = CLOCKS.get(root);
        TextClock clock = reference == null ? null : reference.get();
        Bundle config = cached;
        if (!config.getBoolean("enabled", false) || !config.getBoolean("clock", false)) {
            if (clock != null) root.removeView(clock);
            CLOCKS.put(root, new WeakReference<>(null));
            return;
        }
        if (clock == null) {
            clock = new TextClock(root.getContext());
            clock.setFormat12Hour("HH:mm");
            clock.setFormat24Hour("HH:mm");
            clock.setTextSize(48);
            int pad = Math.round(24 * root.getResources().getDisplayMetrics().density);
            clock.setPadding(pad, pad * 2, pad, pad);
            root.addView(clock, new ViewGroup.LayoutParams(-2, -2));
            CLOCKS.put(root, new WeakReference<>(clock));
        }
        clock.setTextColor(config.getInt("clock_color", 0xffeeeeee));
        float scale = config.getFloat("clock_scale", 1f);
        clock.setScaleX(scale);
        clock.setScaleY(scale);
    }
}
