package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.view.Display;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/** Isolated shader-family handoff; never applies wallpaper or changes physical display power. */
public final class CompanionPreparationProbe {
    private static Handler main;
    private static EglWorker worker;
    private static EglWorker home;
    private static ImageReader homeReader;
    private static ImageReader reader;
    private static int variant;
    private static Object renderer;
    private static Object field(Object owner,String name)throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private interface Checked {void run()throws Exception;}
    private static void gl(Checked code) {
        try {((Handler)field(worker,"handler")).post(()->{try{code.run();}catch(Throwable e){fail(e);}});}
        catch(Exception e){fail(e);}
    }
    private static void fail(Throwable error){error.printStackTrace();System.exit(2);}
    private static void later(long delay,Checked code){main.postDelayed(()->{try{code.run();}catch(Throwable e){fail(e);}},delay);}
    private static void await(BooleanSupplier condition,Checked next) {
        long until=SystemClock.uptimeMillis()+10000;
        main.post(new Runnable(){public void run(){try {
            if(condition.getAsBoolean()){next.run();return;}
            check(SystemClock.uptimeMillis()<until,"Timeout: "+worker.error);
            main.postDelayed(this,10);
        }catch(Throwable e){fail(e);}}});
    }
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();main=new Handler(Looper.getMainLooper());variant=Integer.parseInt(args[0]);
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context system=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context context=ProbeContext.vivo(system,"/data/local/tmp/hyperextreme-vivo-companion-test");
        check(context.getSystemService(PowerManager.class).isInteractive(),"Keep the phone awake for this isolated test");
        worker=new EglWorker(context,false);
        if(variant<201||variant>=301&&variant<=303) {
            home=new EglWorker(context,false);
            homeReader=ImageReader.newInstance(1200,2670,PixelFormat.RGBA_8888,3);
            homeReader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){}},main);
            home.display(Display.STATE_ON,true);
            home.state(new EngineRegistry.Snapshot(2,2,SystemClock.uptimeMillis(),1));
            home.attach(homeReader.getSurface(),1200,2670,variant,false);home.visible(true);
        }
        reader=ImageReader.newInstance(1200,2670,PixelFormat.RGBA_8888,3);
        reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){}},main);
        worker.display(Display.STATE_ON,true);
        worker.state(new EngineRegistry.Snapshot(2,2,SystemClock.uptimeMillis(),1));
        worker.attach(reader.getSurface(),1200,2670,variant,false);
        later(300,()->gl(()->{
            check(field(worker,"renderer")==null,"Hidden attachment eagerly initialized");
            worker.prepareCompanion();
            await(()->worker.preparedGeneration==worker.activeGeneration&&worker.renderedFrames>0,()->gl(()->{
                renderer=field(worker,"renderer");long count=worker.renderedFrames;
                check(count==1,"Preparation submitted more than one hidden buffer");
                if(home!=null)check(field(home,"motionSession")==field(worker,"motionSession"),"Companion reset shared CPU motion");
                later(700,()->{
                    check(worker.renderedFrames==count,"Preparation started a hidden animation loop");
                    System.out.println("ONE_PREPARED_BUFFER variant="+variant+" frames="+count+" initMs="+worker.initMs);
                    sleep(count);
                });
            }));
        }));
        later(30000,()->{throw new AssertionError("Global timeout");});Looper.loop();
    }
    private static void sleep(long frames) {
        long start=SystemClock.uptimeMillis();
        if(home!=null){home.state(new EngineRegistry.Snapshot(2,0,start,2));home.visible(false);}
        worker.state(new EngineRegistry.Snapshot(2,0,start,2));
        worker.display(Display.STATE_ON,false);worker.visible(true);
        await(()->worker.renderedFrames>frames,()->gl(()->{
            check(field(worker,"renderer")==renderer,"First sleep rebuilt the prepared renderer");
            if(renderer instanceof PhotoRenderer) {
                SceneMotion scene=(SceneMotion)field(renderer,"scene");
                check(scene.values[0]<.95f,"First visible frame skipped to settled AOD");
            } else if(((WallpaperRenderer)renderer).motion() instanceof CosmicMotion
                    ||((WallpaperRenderer)renderer).motion() instanceof BubbleMotion)
                check(((WallpaperRenderer)renderer).motion().active(),"First orb frame skipped the transition");
            System.out.println("PREPARED_TAKEOVER variant="+variant+" elapsedMs="+(SystemClock.uptimeMillis()-start));
            worker.visible(false);cancelledPreparation();
        }));
    }
    private static void cancelledPreparation() {
        worker.state(new EngineRegistry.Snapshot(2,2,SystemClock.uptimeMillis(),3));
        worker.display(Display.STATE_ON,true);
        worker.attach(reader.getSurface(),1200,2670,variant,false);
        worker.prepareCompanion();
        // Revoke before the delayed preparation: no decode, EGL or buffer publication.
        gl(()->{
            check((Boolean)field(worker,"preparationPosted"),"Preparation was not queued");
            worker.state(new EngineRegistry.Snapshot(2,0,SystemClock.uptimeMillis(),4));
            worker.display(Display.STATE_OFF,false);
        });
        later(500,()->gl(()->{
            check(field(worker,"renderer")==null&&worker.renderedFrames==0,"Sleep did not cancel queued preparation");
            worker.state(new EngineRegistry.Snapshot(2,2,SystemClock.uptimeMillis(),5));
            worker.display(Display.STATE_ON,true);worker.prepareCompanion();
            gl(()->{
                check((Boolean)field(worker,"preparationPosted"),"Detach preparation was not queued");
                worker.detach();
            });
            later(500,()->gl(()->{
                check(field(worker,"renderer")==null,"Detached generation resurrected");
                worker.close();later(200,()->{
                    if(home!=null){home.close();homeReader.close();}
                    reader.close();System.out.println("COMPANION_PREPARATION_PASS variant="+variant);System.exit(0);
                });
            }));
        }));
    }
}
