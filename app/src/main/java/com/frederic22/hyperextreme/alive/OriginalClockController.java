package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import android.os.*;

/** Original views are created on main; immutable code/font preparation stays off main. */
final class OriginalClockController implements AutoCloseable {
    static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AliveClockAssets");t.setDaemon(true);return t;});
    private final FrameLayout host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile int generation;
    private volatile boolean closed;
    private AutoCloseable face;
    private ClockUpdates updates;
    private View clockView;
    private int desiredColor;
    private float desiredScale = 1;
    private Consumer<String> status;
    OriginalClockController(FrameLayout host){this.host=host;}
    static List<String[]> catalog(Context context)throws Exception {
        List<String[]> result=new ArrayList<>();
        for(NativeHyperOsStyles.Style s:NativeHyperOsStyles.ALL)result.add(new String[]{s.id,s.title});
        for(int i=0;i<6;i++)result.add(new String[]{"flyme-"+i,"Flyme · "+(i==0?"横向时钟":i==2?"纵向时钟":"原版样式 "+(i+1))});
        for(NativeVivoClockStyles.Style s:NativeVivoClockStyles.all(context))result.add(new String[]{s.id,s.title});
        return result;
    }
    void load(Context assets,ExtensionOptions options,boolean aod,boolean preview,Consumer<String> done){
        if(closed)return;
        int token=++generation; clearFace();
        desiredColor=options.color;desiredScale=options.scale;status=done;
        IO.execute(()->{
            if(closed||token!=generation)return;
            try {
                Object runtime;
                if(NativeHyperOsStyles.find(options.style)!=null)runtime=OfficialHyperOsUi.open(assets);
                else if(NativeVivoClockStyles.contains(options.style)){runtime=new OfficialVivoClockUi(assets);NativeVivoClockStyles.all(assets);}
                else runtime=new OfficialUi(assets);
                main.post(()->{
                    if(closed||token!=generation){if(runtime instanceof AutoCloseable)try{((AutoCloseable)runtime).close();}catch(Exception ignored){}return;}
                    try {
                        View view;ClockUpdates.Listener tick;
                        if(runtime instanceof OfficialHyperOsUi){
                            NativeHyperOsFace clock=new NativeHyperOsFace((OfficialHyperOsUi)runtime,NativeHyperOsStyles.find(options.style),aod,false);
                            face=clock;view=clock;
                            tick=time->{try{clock.refresh(TimeZone.getDefault().getID(),android.text.format.DateFormat.is24HourFormat(assets));}catch(Exception e){failed(token,e,done);}};
                        } else if(runtime instanceof OfficialVivoClockUi){
                            NativeVivoClockFace clock=new NativeVivoClockFace(assets,(OfficialVivoClockUi)runtime,NativeVivoClockStyles.find(assets,options.style),aod,false);
                            view=clock;face=()->{};
                            tick=time->clock.update(time,TimeZone.getDefault(),android.text.format.DateFormat.is24HourFormat(assets));
                        } else {
                            ((OfficialUi)runtime).close();
                            OfficialClockFace clock=new OfficialClockFace(assets,preview);face=clock;view=clock;
                            clock.layoutStyle(Integer.parseInt(options.style.substring(6)));tick=clock::refresh;
                        }
                        clockView=view;applyAppearance();
                        GuardedClockFrame guard=new GuardedClockFrame(host.getContext(),e->failed(token,e,done),()->{
                            if(!closed&&token==generation){
                                if(preview){host.setAlpha(0);host.animate().alpha(1).setDuration(180).start();}
                                done.accept("");
                            }
                        });
                        guard.addView(view,new FrameLayout.LayoutParams(-1,-2));
                        host.addView(guard,new FrameLayout.LayoutParams(-1,-2));
                        updates=new ClockUpdates(host.getContext(),preview,tick);updates.start();
                    }catch(Exception|LinkageError e){failed(token,e,done);}
                });
            }catch(Exception|LinkageError e){main.post(()->failed(token,e,done));}
        });
    }
    // Slider/color edits reuse the already loaded view; no asset work or loading flash.
    void appearance(int color,float scale){
        if(closed||(desiredColor==color&&desiredScale==scale))return;
        desiredColor=color;desiredScale=scale;
        if(clockView!=null)try{applyAppearance();}
        catch(Exception|LinkageError error){if(status!=null)failed(generation,error,status);}
    }
    private void applyAppearance()throws Exception{
        if(clockView instanceof NativeHyperOsFace)((NativeHyperOsFace)clockView).color(desiredColor);
        else if(clockView instanceof NativeVivoClockFace)((NativeVivoClockFace)clockView).color(desiredColor);
        else if(clockView instanceof OfficialClockFace)((OfficialClockFace)clockView).color(desiredColor);
        clockView.setScaleX(desiredScale);clockView.setScaleY(desiredScale);
    }
    private void failed(int token,Throwable error,Consumer<String> done){
        if(closed||token!=generation)return;
        generation++;clearFace();done.accept(error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage()));
    }
    void tick(){if(updates!=null)updates.refresh(System.currentTimeMillis());}
    private void clearFace(){
        host.animate().cancel();host.setAlpha(1);
        if(updates!=null){updates.stop();updates=null;}
        if(face!=null)try{face.close();}catch(Exception ignored){}face=null;clockView=null;status=null;host.removeAllViews();
    }
    @Override public void close(){closed=true;generation++;clearFace();}
}
