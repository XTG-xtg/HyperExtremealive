package com.frederic22.hyperextreme.alive;

import android.content.*;
import android.content.res.AssetManager;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import com.frederic22.hyperextreme.alive.core.ExtensionPulse;
import com.frederic22.hyperextreme.alive.core.FingerprintVisualState;
import java.lang.ref.WeakReference;
import java.util.function.Supplier;

/** Attached ONLY to MIUIAod's verified clock root. No system display policy is changed. */
final class HyperOsExtensionHost implements AutoCloseable {
    private final FrameLayout root;
    private WeakReference<Object> nativeHost;
    private final Supplier<Bundle> config;
    private final Handler main=new Handler(Looper.getMainLooper());
    final OwnedClockMask mask=new OwnedClockMask();
    final OwnedClockMask lightMask=new OwnedClockMask();
    private final FingerprintVisualState finger=new FingerprintVisualState();
    private final ExtensionPulse pulse=new ExtensionPulse();
    private FrameLayout clockSlot;
    private OriginalClockController clock;
    private FlymeNotificationLight light;
    private Context assets;
    private View stock;
    private View nativeLight;
    private ViewGroup lightParent;
    private String loadedKey="";
    private boolean closed,ready;
    private int lightGeneration;
    private final ViewTreeObserver.OnPreDrawListener geometry=()->{
        if(!closed&&ready&&stock!=null&&clockSlot!=null){
            // Parent burn-in motion is inherited. Mirror only the replaced leaf's visual transforms.
            Object top=HyperOsExtensionHooks.call(stock,"getTopMargin");
            if(!(top instanceof Number)||!Float.isFinite(((Number)top).floatValue())){suspend();return true;}
            clockSlot.layout(stock.getLeft(),stock.getTop(),stock.getRight(),stock.getTop()+clockSlot.getMeasuredHeight());
            clockSlot.setTranslationX(stock.getTranslationX());clockSlot.setTranslationY(stock.getTranslationY()+((Number)top).floatValue());
            clockSlot.setScaleX(stock.getScaleX());clockSlot.setScaleY(stock.getScaleY());
            clockSlot.setRotation(stock.getRotation());clockSlot.setAlpha(mask.nativeAlpha());
            if(mask.bound(stock))clockSlot.setVisibility(stock.getVisibility());
        }
        return true;
    };
    private final Runnable timeout=this::clearLight;
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View v){}
        public void onViewDetachedFromWindow(View v){HyperOsExtensionHooks.detach(root);}
    };
    HyperOsExtensionHost(FrameLayout root,Object host,Supplier<Bundle> config){
        this.root=root;this.nativeHost=new WeakReference<>(host);this.config=config;root.addOnAttachStateChangeListener(attachment);
        root.getViewTreeObserver().addOnPreDrawListener(geometry);
    }
    private boolean allowed(){
        Object h=nativeHost.get();PowerManager p=root.getContext().getSystemService(PowerManager.class);
        return HyperOsExtensionHooks.enabled()&&!closed&&!finger.suppressed()&&root.isAttachedToWindow()&&root.isShown()&&config.get().getBoolean("enabled",false)
            &&p!=null&&!p.isInteractive()&&Boolean.TRUE.equals(HyperOsExtensionHooks.call(h,"isDozing"))
            &&Boolean.TRUE.equals(HyperOsExtensionHooks.call(h,"isAodVisible"))
            &&Boolean.FALSE.equals(HyperOsExtensionHooks.field(h,"mKeyguardOccluded"))
            &&Boolean.FALSE.equals(HyperOsExtensionHooks.call(h,"isFingerprintPressed"));
    }
    private Context assets()throws Exception {
        if(assets==null){
            Context base=root.getContext();AssetManager bundle=base.createPackageContext("com.xtg.hyperextremealive",Context.CONTEXT_IGNORE_SECURITY).getAssets();
            assets=new ContextWrapper(base){
                public AssetManager getAssets(){return bundle;}
                public Context getApplicationContext(){return this;}
            };
        }
        return assets;
    }
    void refresh(){
        if(closed)return;
        Object currentHost=HyperOsExtensionHooks.field(root,"mHost");
        if(currentHost!=nativeHost.get()){suspend();finger.clear();nativeHost=new WeakReference<>(currentHost);}
        ExtensionOptions options=ExtensionOptions.from(config.get());
        if(stock!=null&&(!stock.isAttachedToWindow()||stock.getParent()!=clockSlot.getParent()))clearClock();
        if(!options.clock||!config.get().getBoolean("enabled",false)){clearClock();}
        else if(allowed()){
            if(!options.clockKey().equals(loadedKey))loadClock(options);
            if(clockSlot!=null&&ready)clockSlot.setVisibility(View.VISIBLE);
            if(ready&&stock!=null)mask.bind(stock);
            if(clock!=null)clock.tick();
        }else{
            // Stock masks/animations retain authority; no parent alpha or biometric state writes.
            mask.clear();if(clockSlot!=null)clockSlot.setVisibility(View.INVISIBLE);
        }
        if(!allowed()||options.light==0)clearLight();
    }
    private void loadClock(ExtensionOptions options){
        clearClock();
        // AODView contains widgets/notifications. Replace only the proven MiuiClockView leaf.
        View target=findClock(root,0);
        if(target==null||!(target.getParent() instanceof FrameLayout)||!(HyperOsExtensionHooks.call(target,"getTopMargin") instanceof Number))return;
        stock=target;FrameLayout parent=(FrameLayout)target.getParent();
        clockSlot=new FrameLayout(parent.getContext());clockSlot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        clockSlot.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams((FrameLayout.LayoutParams)target.getLayoutParams());
        position.height=ViewGroup.LayoutParams.WRAP_CONTENT;
        parent.addView(clockSlot,parent.indexOfChild(target)+1,position);clock=new OriginalClockController(clockSlot);
        loadedKey=options.clockKey();
        try {clock.load(assets(),options,true,false,error->{
            if(closed)return;
            ready=error.isEmpty();
            if(ready&&allowed()){mask.bind(stock);clockSlot.setVisibility(View.VISIBLE);}
            else {mask.clear();if(clockSlot!=null)clockSlot.setVisibility(View.INVISIBLE);}
            HyperOsExtensionHooks.report(error.isEmpty()?"clock-loaded":"clock-failed");
        });}catch(Exception e){clearClock();HyperOsExtensionHooks.report("clock-failed");}
    }
    private static View findClock(View view,int depth){
        if(depth>12)return null;
        if(view.getClass().getName().equals("com.miui.clock.MiuiClockView"))return view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<Math.min(g.getChildCount(),80);i++){View found=findClock(g.getChildAt(i),depth+1);if(found!=null)return found;}}
        return null;
    }
    void notification(View source){
        ExtensionOptions o=ExtensionOptions.from(config.get());
        if(o.light==0||!allowed()||!source.isAttachedToWindow()||!source.isShown()
            ||source.getRootView()!=root.getRootView()||!(source.getParent() instanceof FrameLayout))return;
        if(nativeLight==source&&pulse.active(SystemClock.uptimeMillis()))return;
        // The ROM has already approved its pulse. Respect DND/power-save even if that ROM callback is broad.
        android.app.NotificationManager n=root.getContext().getSystemService(android.app.NotificationManager.class);
        PowerManager power=root.getContext().getSystemService(PowerManager.class);
        if(n==null||n.getCurrentInterruptionFilter()!=android.app.NotificationManager.INTERRUPTION_FILTER_ALL||power==null||power.isPowerSaveMode())return;
        if(pulse.begin(SystemClock.uptimeMillis(),o.seconds,true)<0)return;
        nativeLight=source;
        int generation=++lightGeneration;
        main.removeCallbacks(timeout);main.postDelayed(timeout,pulse.remaining(SystemClock.uptimeMillis()));
        final Context c;
        try{c=assets();}catch(Exception e){clearLight();return;}
        OriginalClockController.IO.execute(()->{
            try{
                FlymeNotificationLight.Model model=FlymeNotificationLight.load(c,o.light==1);
                main.post(()->{
                    if(closed||generation!=lightGeneration||!allowed()||!pulse.active(SystemClock.uptimeMillis())
                        ||!source.isAttachedToWindow()||!Boolean.TRUE.equals(HyperOsExtensionHooks.field(source,"mAnimationRunning")))return;
                    try{
                        removeLight();light=new FlymeNotificationLight(c,model,o.light==1);light.setRingColor(o.color);
                        light.onFailure(error->{if(generation==lightGeneration){clearLight();HyperOsExtensionHooks.report("notification-draw-failed");}});
                        light.setClickable(false);light.setFocusable(false);
                        lightParent=(ViewGroup)source.getParent();
                        lightParent.addView(light,lightParent.indexOfChild(source)+1,new FrameLayout.LayoutParams((FrameLayout.LayoutParams)source.getLayoutParams()));
                        int[] offset=new int[2];source.getLocationOnScreen(offset);
                        android.graphics.Rect bounds=root.getContext().getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds();
                        light.layoutForDisplay(bounds.width(),bounds.height(),root.getRootWindowInsets()==null?null:root.getRootWindowInsets().getDisplayCutout(),offset[0],offset[1]);
                        lightMask.bind(source);
                        play(generation);
                    }catch(Exception|LinkageError e){clearLight();HyperOsExtensionHooks.report("notification-failed");}
                });
            }catch(Exception|LinkageError e){main.post(()->{if(generation==lightGeneration)clearLight();});}
        });
    }
    private void play(int generation){
        if(generation!=lightGeneration||light==null||!allowed()||!pulse.active(SystemClock.uptimeMillis())){clearLight();return;}
        light.play(()->play(generation));
    }
    boolean owns(Object host){return nativeHost.get()==host;}
    void fingerprint(boolean down){
        if(closed)return;
        if(down){
            long token=finger.press();suspend();
            // A lost finger-up cannot strand our view; never override a still-pressed native state.
            main.postDelayed(()->releaseFinger(token),2000);
        }else{
            long token=finger.token();main.postDelayed(()->releaseFinger(token),64);
        }
    }
    private void releaseFinger(long token){
        if(closed)return;
        if(finger.release(token,!Boolean.FALSE.equals(HyperOsExtensionHooks.call(nativeHost.get(),"isFingerprintPressed"))))refresh();
    }
    void suspend(){
        mask.clear();if(clockSlot!=null)clockSlot.setVisibility(View.INVISIBLE);
        clearLight();
    }
    void notificationStopped(View source){if(nativeLight==source)clearLight();}
    void clearLight(){lightGeneration++;pulse.cancel();main.removeCallbacks(timeout);removeLight();nativeLight=null;}
    private void removeLight(){lightMask.clear();if(light!=null){light.stop();if(lightParent!=null)lightParent.removeView(light);light=null;}lightParent=null;}
    private void clearClock(){
        ready=false;mask.clear();if(clock!=null){clock.close();clock=null;}
        if(clockSlot!=null&&clockSlot.getParent() instanceof ViewGroup)((ViewGroup)clockSlot.getParent()).removeView(clockSlot);
        clockSlot=null;stock=null;loadedKey="";
    }
    @Override public void close(){if(closed)return;closed=true;main.removeCallbacksAndMessages(null);finger.clear();clearLight();clearClock();root.removeOnAttachStateChangeListener(attachment);if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(geometry);}
}
