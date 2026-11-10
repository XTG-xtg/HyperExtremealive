package com.frederic22.hyperextreme.alive;

import android.os.*;
import android.view.View;
import android.widget.FrameLayout;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/** Optional, exact MIUIAod contracts. Missing contracts never change stock behavior. */
final class HyperOsExtensionHooks {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Main-thread owned; detach removes the strong reference and cancels IO generations.
    private static final Map<FrameLayout, HyperOsExtensionHost> HOSTS = new IdentityHashMap<>();
    private static final Set<Method> INSTALLED = new HashSet<>();
    private static Consumer<String> status = ignored -> {};
    static void report(String value) { status.accept(value); }

    static void install(XposedModule module, ClassLoader loader, Supplier<Bundle> config, Consumer<String> sink) {
        status = sink;
        try { Class.forName("com.miui.aod.AODView", false, loader); }
        catch (ReflectiveOperationException | LinkageError absent) { return; }
        if(!requiredContracts(loader)){report("required-contract-missing");return;}
        adapterEnabled=false;
        boolean maskHook = exact(module, View.class.getClassLoader(), "android.view.View", "setAlpha", new Class<?>[]{float.class}, chain -> {
            if (Looper.myLooper() == Looper.getMainLooper() && OwnedClockMask.any() && !HOSTS.isEmpty()) {
                View view = (View) chain.getThisObject();
                float alpha = ((Number) chain.getArgs().get(0)).floatValue();
                for (HyperOsExtensionHost h : HOSTS.values())
                    if (h.mask.intercept(view, alpha) || h.lightMask.intercept(view, alpha)) return null;
            }
            return chain.proceed();
        });
        if (!maskHook) return;
        boolean safe=after(module, loader, "com.miui.aod.AODView", "onDetachedFromWindow", new Class<?>[0], v -> detach((FrameLayout) v));
        safe &= after(module, loader, "com.miui.aod.AODView", "onAttachedToWindow", new Class<?>[0], v -> {
            if (!(v instanceof FrameLayout)) return;
            FrameLayout root = (FrameLayout) v;
            detach(root);
            HyperOsExtensionHost host = new HyperOsExtensionHost(root, field(v, "mHost"), config);
            HOSTS.put(root, host); host.refresh(); report("aod-attached");
        });
        for (String name : new String[]{"dozeTimeTick", "setKeyguardOccluded", "setAodVisibility"})
            safe &= after(module, loader, "com.miui.aod.DozeHost", name, new Class<?>[]{boolean.class}, v -> refresh());
        for (String name : new String[]{"onStopDoze", "screenTurnOn", "removeAODView"})
            safe &= after(module, loader, "com.miui.aod.DozeHost", name, new Class<?>[0], v -> {
                for (HyperOsExtensionHost h : HOSTS.values()) h.suspend();
            });
        for (String name : new String[]{"fireFingerprintPressed", "onFingerprintPressed"}) {
            boolean installed=exact(module, loader, "com.miui.aod.DozeHost", name, new Class<?>[]{boolean.class, boolean.class}, chain -> {
                boolean down = Boolean.TRUE.equals(chain.getArgs().get(0));
                Object nativeHost = chain.getThisObject();
                if (down) run(() -> { for (HyperOsExtensionHost h : HOSTS.values()) if (h.owns(nativeHost)) h.fingerprint(true); });
                Object result = chain.proceed();
                if (!down) run(() -> { for (HyperOsExtensionHost h : HOSTS.values()) if (h.owns(nativeHost)) h.fingerprint(false); });
                return result;
            });
            if(!installed){adapterEnabled=false;return;}
        }
        adapterEnabled=safe;
        if(!safe)return;
        // This MIUI ROM does not use pulse reason 1 for its notification light.
        // Observe a ROM-started timeline only; never request a pulse or acquire a wake lock.
        after(module, loader, "com.miui.aod.widget.NotificationAnimationView", "startAnimation", new Class<?>[0], v -> {
            if (v instanceof View && Boolean.TRUE.equals(field(v, "mAnimationRunning")))
                for (HyperOsExtensionHost h : HOSTS.values()) h.notification((View) v);
        });
        after(module, loader, "com.miui.aod.widget.NotificationAnimationView", "stopAnimation", new Class<?>[0], v -> {
            for (HyperOsExtensionHost h : HOSTS.values()) h.notificationStopped((View) v);
        });
    }
    private static boolean adapterEnabled;
    static boolean enabled(){return adapterEnabled;}
    static boolean requiredContracts(ClassLoader loader){
        try{
            Class<?> host=Class.forName("com.miui.aod.DozeHost",false,loader);
            for(String name:new String[]{"isDozing","isAodVisible","isFingerprintPressed"})
                if(host.getDeclaredMethod(name).getReturnType()!=boolean.class)return false;
            for(String name:new String[]{"fireFingerprintPressed","onFingerprintPressed"})host.getDeclaredMethod(name,boolean.class,boolean.class);
            for(String name:new String[]{"onStopDoze","screenTurnOn","removeAODView"})host.getDeclaredMethod(name);
            host.getDeclaredField("mKeyguardOccluded");
            Class<?> view=Class.forName("com.miui.aod.AODView",false,loader);
            view.getDeclaredField("mHost");view.getDeclaredMethod("onAttachedToWindow");view.getDeclaredMethod("onDetachedFromWindow");
            return true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError absent){return false;}
    }
    static void detach(FrameLayout root) { HyperOsExtensionHost old = HOSTS.remove(root); if (old != null) old.close(); }
    static void refresh() { run(() -> { for (HyperOsExtensionHost h : HOSTS.values()) h.refresh(); }); }
    private interface Around { Object invoke(io.github.libxposed.api.XposedInterface.Chain chain) throws Throwable; }
    private static boolean exact(XposedModule module, ClassLoader loader, String type, String method, Class<?>[] signature, Around code) {
        try {
            Method m = Class.forName(type, false, loader).getDeclaredMethod(method, signature);
            if (INSTALLED.contains(m)) return true;
            module.hook(m).intercept(code::invoke); INSTALLED.add(m); return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { report("contract-missing:" + method); return false; }
    }
    private static boolean after(XposedModule module, ClassLoader loader, String type, String method, Class<?>[] signature, Consumer<Object> code) {
        return exact(module, loader, type, method, signature, chain -> {
            Object result = chain.proceed(); Object host = chain.getThisObject(); run(() -> code.accept(host)); return result;
        });
    }
    private static void run(Runnable code) {
        Runnable safe = () -> { try { code.run(); } catch (RuntimeException | LinkageError e) {
            for(HyperOsExtensionHost h:HOSTS.values())try{h.suspend();}catch(RuntimeException|LinkageError ignored){}
            report("adapter-failed");
        } };
        if (Looper.myLooper() == Looper.getMainLooper()) safe.run(); else MAIN.post(safe);
    }
    static Object field(Object host, String name) {
        if (host == null) return null;
        for (Class<?> c = host.getClass(); c != null; c = c.getSuperclass()) try {
            Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(host);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return null;
    }
    static Object call(Object host, String name) {
        if (host == null) return null;
        try { return host.getClass().getMethod(name).invoke(host); }
        catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }
}
