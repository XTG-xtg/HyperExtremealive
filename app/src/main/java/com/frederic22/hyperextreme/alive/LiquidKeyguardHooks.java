package com.frederic22.hyperextreme.alive;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** HyperOS keyguard-only bridge; never hooks PowerManager or consumes the panel stream. */
final class LiquidKeyguardHooks {
    private static final ThreadLocal<Object> SUPPRESS_HELPER=new ThreadLocal<>();
    private static final Map<Object,LiquidTapGesture> GESTURES=new WeakHashMap<>();
    private static volatile Snapshot snapshot=new Snapshot(null,-1);
    private static boolean installed;
    private static final class Snapshot {
        final LiquidTouchMap map;final long revision;
        Snapshot(LiquidTouchMap map,long revision){this.map=map;this.revision=revision;}
    }
    static void config(Bundle config){
        snapshot=new Snapshot(config.getBoolean("enabled")&&config.getInt("variant")==701?LiquidTouchMap.from(config.getBundle("liquid_touch")):null,config.getLong("revision",-1));
    }
    static void install(XposedModule module,ClassLoader loader,Consumer<Bundle> send,Consumer<String> log){
        if(installed)return;
        try{
            Class<?> panel=Class.forName("com.android.keyguard.injector.KeyguardPanelViewInjector",false,loader);
            Method touch=panel.getDeclaredMethod("onTouchEvent",MotionEvent.class,int.class,float.class,float.class,boolean.class,boolean.class,boolean.class);
            Method doubleTouch=Class.forName("com.android.keyguard.DoubleTapHelper",false,loader).getDeclaredMethod("onTouchEvent",MotionEvent.class);
            module.hook(doubleTouch).intercept(chain->{
                if(chain.getThisObject()!=SUPPRESS_HELPER.get())return chain.proceed();
                MotionEvent cancel=MotionEvent.obtain((MotionEvent)chain.getArgs().get(0));cancel.setAction(MotionEvent.ACTION_CANCEL);
                try{return chain.proceed(new Object[]{cancel});}finally{cancel.recycle();}
            });
            module.hook(touch).intercept(chain->{
                MotionEvent event=(MotionEvent)chain.getArgs().get(0);Object host=chain.getThisObject();Snapshot current=snapshot;
                LiquidTouchMap map=current.map;
                Object root=field(host,"notificationShadeWindowView");
                View view=root instanceof View?(View)root:null;
                boolean eligible=map!=null&&view!=null&&view.isShown()&&(Integer)chain.getArgs().get(1)==1
                    &&!Boolean.TRUE.equals(chain.getArgs().get(4))&&!Boolean.TRUE.equals(chain.getArgs().get(5))&&!Boolean.TRUE.equals(chain.getArgs().get(6));
                if(eligible){
                    Object sw=field(host,"screenWidth"),sh=field(host,"screenHeight");
                    eligible=sw instanceof Integer&&sh instanceof Integer&&(Integer)sw==map.screenWidth&&(Integer)sh==map.screenHeight;
                    if(eligible)eligible=!blocked(view,event.getRawX(),event.getRawY(),0);
                }
                float x=map==null?0:event.getRawX()/map.screenWidth,y=map==null?0:event.getRawY()/map.screenHeight;
                LiquidTapGesture gesture=GESTURES.computeIfAbsent(host,key->new LiquidTapGesture());
                boolean tap=gesture.update(event.getActionMasked(),event.getPointerCount(),event.getRawX(),event.getRawY(),event.getEventTime(),eligible,
                    map!=null&&map.hit(x,y),map==null?"":map.scene+":"+current.revision,view==null?0:ViewConfiguration.get(view.getContext()).getScaledTouchSlop(),ViewConfiguration.getLongPressTimeout());
                Object previous=SUPPRESS_HELPER.get();
                if(eligible&&gesture.suppressDoubleTap())SUPPRESS_HELPER.set(field(host,"doubleTapHelper"));
                try{
                    Object result=chain.proceed();
                    if(tap&&snapshot==current){
                        Bundle b=new Bundle();b.putFloat("x",x);b.putFloat("y",y);b.putLong("at",event.getEventTime());b.putLong("revision",current.revision);send.accept(b);
                    }
                    return result;
                }finally{if(previous==null)SUPPRESS_HELPER.remove();else SUPPRESS_HELPER.set(previous);}
            });
            installed=true;log.accept("liquid keyguard touch + region-specific double-tap bridge installed");
        }catch(Throwable error){log.accept("liquid keyguard bridge unavailable (system gestures unchanged): "+error);}
    }
    private static Object field(Object host,String name){
        if(host==null)return null;
        for(Class<?> c=host.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(host);}catch(ReflectiveOperationException ignored){}
        return null;
    }
    private static boolean blocked(View view,float x,float y,int depth){
        if(depth>24||!view.isShown()||view.getAlpha()<.05f)return false;
        Rect bounds=new Rect();if(!view.getGlobalVisibleRect(bounds)||!bounds.contains((int)x,(int)y))return false;
        Rect clip=view.getClipBounds();
        if(clip!=null){
            int[] location=new int[2];view.getLocationOnScreen(location);
            if(!clip.contains((int)((x-location[0])/view.getScaleX()),(int)((y-location[1])/view.getScaleY())))return false;
        }
        String name=view.getClass().getName();
        if(name.contains("ExpandableNotificationRow")||name.contains("KeyguardSecurity")
                ||name.contains("Emergency")||name.contains("MediaHost")||name.contains("Bouncer"))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--)if(blocked(group.getChildAt(i),x,y,depth+1))return true;}
        else if(view.isClickable()||view.isLongClickable())return true;
        return false;
    }
}
