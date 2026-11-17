package com.frederic22.hyperextreme.alive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Adapter for Xiaomi's full-AOD strategy, which bypasses DozeService brightness requests. */
final class MiuiAodPanelBrightness {
    private final AodPanelHold curve=new AodPanelHold();
    private volatile boolean owned;
    private volatile boolean fullMode,installed;
    private boolean watching;
    private long bindingRevision;
    private boolean reportedFailure;
    private String lastDecision="";
    private Handler worker;
    private Runnable changed;

    synchronized void clear(){lastDecision="";}
    void installed(boolean ready){installed=ready;}
    boolean managed(){return installed&&owned&&fullMode;}

    // Called only on the config worker; WallpaperManager/ActivityManager IPC must not run
    // in DisplayPowerController's hook/lock. Invalidate before refreshing changed bindings.
    void refresh(Context context,Handler worker,Runnable changed){
        if(context==null)return;
        this.worker=worker;this.changed=changed;
        try {
        if(!watching){
            IntentFilter filter=new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
            filter.addAction("android.intent.action.USER_SWITCHED");
            context.registerReceiver(new BroadcastReceiver(){
                @Override public void onReceive(Context c,Intent event){
                    synchronized(MiuiAodPanelBrightness.this){owned=false;bindingRevision++;clear();}
                    worker.post(changed);
                }
            },filter);
            android.database.ContentObserver modeObserver=new android.database.ContentObserver(worker){
                @Override public void onChange(boolean selfChange){changed.run();}
            };
            for(String key:new String[]{"full_screen_aod_on","doze_always_on"})
                context.getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(key),false,modeObserver);
            watching=true;
        }
        long revision;
        synchronized(this){revision=bindingRevision;}
        boolean current=SystemAodDimming.ownsSelectedLock(context);
        Context selected=SystemAodDimming.selectedContext(context);
        boolean mode=android.provider.Settings.Secure.getInt(selected.getContentResolver(),"full_screen_aod_on",0)==1
            &&android.provider.Settings.Secure.getInt(selected.getContentResolver(),"doze_always_on",0)==1;
        synchronized(this){
            if(revision!=bindingRevision)return;
            if(owned!=current)clear();
            owned=current;
            fullMode=mode;
        }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){synchronized(this){owned=false;fullMode=false;clear();}}
    }

    void powerRequest(Object display,Object request){
        try {
            if(Integer.valueOf(0).equals(field(display,"mDisplayId"))
                    &&(!Integer.valueOf(1).equals(field(request,"policy"))
                        ||Integer.valueOf(1).equals(field(request,"dozeScreenState"))))clear();
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){clear();}
    }

    static boolean contract(Method method){
        Class<?>[] p=method.getParameterTypes();
        return method.getName().equals("updateBrightness")&&p.length==1
            &&p[0].getName().equals("com.android.server.display.brightness.StrategyExecutionRequest")
            &&method.getReturnType().getName().equals("com.android.server.display.DisplayBrightnessState");
    }
    static boolean supported(ClassLoader loader){
        try{
            Class<?> execution=Class.forName("com.android.server.display.brightness.StrategyExecutionRequest",false,loader);
            Class<?> strategy=Class.forName("com.android.server.display.brightness.strategy.DozeBrightnessStrategy",false,loader);
            Method update=strategy.getDeclaredMethod("updateBrightness",execution);
            if(!contract(update))return false;
            Class<?> state=update.getReturnType();
            Class<?> builder=Class.forName(state.getName()+"$Builder",false,loader);
            builder.getMethod("from",state);builder.getMethod("setBrightness",float.class);builder.getMethod("build");
            Class<?> miui=Class.forName("com.android.server.display.brightness.strategy.DozeBrightnessStrategyImpl",false,loader);
            miui.getDeclaredField("mContext");miui.getDeclaredField("mIsFullAod");
            strategy.getDeclaredField("mDisplayId");strategy.getDeclaredField("mDozeBrightnessStrategyStub");
            execution.getMethod("getDisplayPowerRequest");state.getMethod("getBrightness");
            return true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unsupported){return false;}
    }

    Object update(Object strategy,Object execution,Object original,float level,Consumer<String> log){
        if(original==null)return null;
        try {
            Object stub=field(strategy,"mDozeBrightnessStrategyStub");
            if(stub==null||!stub.getClass().getName().equals("com.android.server.display.brightness.strategy.DozeBrightnessStrategyImpl"))return original;
            Object request=execution.getClass().getMethod("getDisplayPowerRequest").invoke(execution);
            Context context=(Context)field(stub,"mContext");
            PowerManager power=context==null?null:context.getSystemService(PowerManager.class);
            boolean full=(Boolean)field(stub,"mIsFullAod"),interactive=power==null||power.isInteractive();
            int display=(Integer)field(strategy,"mDisplayId"),policy=(Integer)field(request,"policy"),state=(Integer)field(request,"dozeScreenState");
            boolean eligible=AodPanelHold.eligible(owned,full,interactive,display,policy,state);
            float nativeBrightness=(Float)original.getClass().getMethod("getBrightness").invoke(original);
            float held;
            synchronized(this){
                String decision="owned="+owned+" full="+full+" interactive="+interactive+" display="+display+" policy="+policy+" state="+state+" level="+level;
                if(!decision.equals(lastDecision)){lastDecision=decision;log.accept("AOD Xiaomi scope "+decision);}
                if(!managed()||!eligible||AodBrightness.normalize(level)==0)return original;
                held=curve.brightness(nativeBrightness,level);
            }
            if(held==nativeBrightness||!AodPanelHold.valid(held))return original;
            Object result=copyBrightness(original,held);
            log.accept("AOD Xiaomi adaptive native="+nativeBrightness+" adjusted="+held+" level="+level);
            return result;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
            installed=false;
            if(worker!=null&&changed!=null)worker.post(changed);
            clear();
            if(!reportedFailure){reportedFailure=true;log.accept("AOD Xiaomi panel contract unavailable: "+unavailable);}
            return original;
        }
    }

    static Object copyBrightness(Object original,float brightness) throws ReflectiveOperationException {
        // Clone every native field; downstream thermal/power caps and brightness reasons remain.
        Class<?> state=original.getClass();
        Class<?> builder=Class.forName(state.getName()+"$Builder",false,state.getClassLoader());
        Object copy=builder.getMethod("from",state).invoke(null,original);
        builder.getMethod("setBrightness",float.class).invoke(copy,brightness);
        return builder.getMethod("build").invoke(copy);
    }

    private static Object field(Object host,String name) throws ReflectiveOperationException {
        return SystemFields.get(host,name);
    }
}
