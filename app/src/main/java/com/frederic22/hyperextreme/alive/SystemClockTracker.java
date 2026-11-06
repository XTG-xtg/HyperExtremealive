package com.frederic22.hyperextreme.alive;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Observes HyperOS clock layout, including translation-only burn-in frames. Never moves it. */
final class SystemClockTracker implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private static final WeakHashMap<View,SystemClockTracker> TRACKERS=new WeakHashMap<>();
    private final java.lang.ref.WeakReference<View> root;
    private final Consumer<Bundle> publish;
    private final BooleanSupplier active;
    private long lastSample,lastPublish;
    private String previous="";
    private SystemClockTracker(View view,BooleanSupplier active,Consumer<Bundle> publish){this.root=new java.lang.ref.WeakReference<>(view);this.publish=publish;this.active=active;}
    static void watch(View view,BooleanSupplier active,Consumer<Bundle> publish){
        View root=view.getRootView();if(TRACKERS.containsKey(root))return;
        SystemClockTracker tracker=new SystemClockTracker(root,active,publish);TRACKERS.put(root,tracker);
        root.addOnAttachStateChangeListener(tracker);root.getViewTreeObserver().addOnPreDrawListener(tracker);
    }
    public boolean onPreDraw(){
        View view=root.get();long now=SystemClock.uptimeMillis();
        if(view==null||!active.getAsBoolean()||now-lastSample<200)return true;lastSample=now;
        View clock=find(view);if(clock==null)return true;
        Rect rect=new Rect();if(!clock.getGlobalVisibleRect(rect)||rect.isEmpty())return true;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
        if(view.getDisplay()==null||view.getDisplay().getDisplayId()!=android.view.Display.DEFAULT_DISPLAY)return true;
        view.getDisplay().getRealMetrics(metrics);
        if(!rect.intersect(0,0,metrics.widthPixels,metrics.heightPixels))return true;
        PowerManager power=view.getContext().getSystemService(PowerManager.class);boolean aod=power!=null&&!power.isInteractive();
        String key=rect.toShortString()+":"+aod+":"+metrics.widthPixels+":"+metrics.heightPixels;
        if(key.equals(previous)&&now-lastPublish<30000)return true;
        previous=key;lastPublish=now;
        Bundle b=new Bundle();b.putInt("width",metrics.widthPixels);b.putInt("height",metrics.heightPixels);
        b.putInt("left",rect.left);b.putInt("top",rect.top);b.putInt("right",rect.right);b.putInt("bottom",rect.bottom);
        b.putBoolean("aod",aod);b.putLong("at",now);publish.accept(b);return true;
    }
    static View find(View view){
        View group=find(view,true);
        return group!=null?group:find(view,false);
    }
    private static View find(View view,boolean groups){
        if(view.getVisibility()!=View.VISIBLE||view.getAlpha()<.05f||view.getWidth()<=0||view.getHeight()<=0)return null;
        String id="";
        try{if(view.getId()!=View.NO_ID)id=view.getResources().getResourceEntryName(view.getId()).toLowerCase(Locale.ROOT);}catch(RuntimeException ignored){}
        // Prefer a bounded clock group over one of its hour/minute children or full-screen hosts.
        if(groups&&(id.equals("clock_animation_container")||id.equals("keyguard_clock_container")||id.equals("clock_container")
                ||id.equals("miui_keyguard_clock_container")||id.equals("aod_clock")||id.equals("clock_view"))
                &&view.getHeight()<view.getRootView().getHeight()*.65f)return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),groups);if(result!=null)return result;}}
        // Never use the small status-bar clock when the actual keyguard clock is hidden.
        return !groups&&(id.equals("clock")||id.equals("time_view")||id.equals("aod_time"))
            &&view.getHeight()>view.getRootView().getHeight()*.035f
            &&view.getHeight()<view.getRootView().getHeight()*.65f?view:null;
    }
    public void onViewAttachedToWindow(View v){}
    public void onViewDetachedFromWindow(View v){if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnPreDrawListener(this);v.removeOnAttachStateChangeListener(this);TRACKERS.remove(v);}
}
