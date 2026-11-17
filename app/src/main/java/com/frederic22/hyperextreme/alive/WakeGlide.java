package com.frederic22.hyperextreme.alive;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Blend only the actual main-display backlight/SDR pair from a managed AOD wake. */
final class WakeGlide {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final WeakHashMap<Object,Track> tracks=new WeakHashMap<>();
    private volatile float cap=Float.NaN;
    private final ThreadLocal<Replay> replaying=new ThreadLocal<>();
    private boolean failed;
    private static final class Replay {
        final Track track;final long revision;
        Replay(Track track){this.track=track;revision=track.revision;}
    }
    private static final class Track {
        final AodWakeBlend backlight=new AodWakeBlend(),sdr=new AodWakeBlend();
        WeakReference<Object> host;
        Method method;
        Object[] requested;
        Runnable tick;
        long revision;
    }
    static boolean contract(Method method){
        Class<?>[] p=method.getParameterTypes();
        return method.getName().equals("setState")&&method.getReturnType()==boolean.class
            &&p.length==3&&p[0]==int.class&&p[1]==float.class&&p[2]==float.class;
    }
    synchronized void cancel(){
        for(Track t:tracks.values())cancel(t);
    }
    private void cancel(Track t){
        t.revision++;t.backlight.cancel();t.sdr.cancel();t.requested=null;
        if(t.tick!=null)main.removeCallbacks(t.tick);
    }
    synchronized void wake(boolean eligible){
        if(!eligible){cancel();return;}
        long now=SystemClock.elapsedRealtime();
        for(Track t:tracks.values())if(t.backlight.begin(now)){t.sdr.begin(now);}
    }
    void caps(Object controller){
        try{
            if(!Integer.valueOf(0).equals(SystemFields.get(controller,"mDisplayId")))return;
            float max=brightnessCap(controller);
            cap=AodPanelHold.valid(max)?max:Float.NaN;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unknown){cap=Float.NaN;cancel();}
    }
    static float brightnessCap(Object controller) throws ReflectiveOperationException {
        // CachedBrightnessInfo uses MutableFloat on this ROM. Public BrightnessInfo
        // snapshots it under its own lock and exposes a plain normalized float.
        Object info=controller.getClass().getMethod("getBrightnessInfo").invoke(controller);
        float max=(Float)SystemFields.get(info,"brightnessMaximum");
        Object miui=SystemFields.get(controller,"mDisplayPowerControllerImpl");
        for(String key:new String[]{"mThermalMaxBrightness","mBatteryThermalLimitBrightness","mLowBatteryLimitBrightness"}){
            float limit=(Float)SystemFields.get(miui,key);
            if(AodPanelHold.valid(limit))max=Math.min(max,limit);
        }
        // mThermalBrightness is measured in nits, not normalized backlight.
        return max;
    }
    synchronized Object[] arguments(Object host,Method method,Object[] input,boolean ambient,boolean eligible){
        try {
            Replay replay=replaying.get();
            if(replay!=null&&(replay.revision!=replay.track.revision||replay.track.requested==null))return null;
            Object display=SystemFields.get(host,"this$0");
            if(!Integer.valueOf(0).equals(SystemFields.get(display,"mDisplayId")))return input;
            if(replay!=null&&!input[0].equals(SystemFields.get(display,"mScreenState"))){cancel(replay.track);return null;}
            int state=(Integer)input[0];float backlight=(Float)input[1],sdr=(Float)input[2];
            Track t=tracks.computeIfAbsent(host,key->new Track());
            if(!eligible||state==1||!AodPanelHold.valid(backlight)||!AodPanelHold.valid(sdr)){
                cancel(t);return replay==null?input:null;
            }
            long now=SystemClock.elapsedRealtime();
            if(ambient){
                // wakeUp is intercepted before PowerManager becomes interactive.
                // A last AOD submission in that gap must not disarm the wake.
                if(t.backlight.active(now))return replay==null?input:null;
                t.revision++;t.requested=null;
                t.backlight.ambient(backlight);t.sdr.ambient(sdr);
                if(t.tick!=null)main.removeCallbacks(t.tick);return input;
            }
            if(!t.backlight.active(now)){cancel(t);return input;}
            if(replay==null)t.revision++;
            float b=t.backlight.sample(backlight,now,cap),s=t.sdr.sample(sdr,now,cap);
            t.host=new WeakReference<>(host);t.method=method;t.requested=input.clone();
            if(t.tick==null)t.tick=()->replay(t);
            main.removeCallbacks(t.tick);
            if(t.backlight.active(now))main.postDelayed(t.tick,16);
            return new Object[]{state,b,s};
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unknown){
            cancel();
            if(!failed){failed=true;AliveHooks.log("AOD wake backlight contract unavailable: "+unknown);}
            return replaying.get()==null?input:null;
        }
    }
    private void replay(Track track){
        Object host;Method method;Object[] args;Replay replay;
        synchronized(this){
            host=track.host==null?null:track.host.get();method=track.method;args=track.requested;
            if(host==null||args==null)return;
            replay=new Replay(track);
        }
        // The hooked method can call PowerManager: never retain this lock across Binder.
        try{
            Object display=SystemFields.get(host,"this$0");
            if(!args[0].equals(SystemFields.get(display,"mScreenState")))return;
            replaying.set(replay);
            method.setAccessible(true);method.invoke(host,args);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unknown){cancel();}
        finally{replaying.remove();}
    }
}
