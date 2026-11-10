package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.*;
import android.os.*;
import android.view.Display;
import java.lang.reflect.*;
import java.util.function.BooleanSupplier;

/** Isolated EGL failure and missing-vsync injection; never binds wallpapers or changes power. */
public final class RecoveryWorkerProbe {
    private static Handler main;
    private static EglWorker worker,companion;
    private static ImageReader reader,other;
    private static int variant;
    private static Object field(Object owner,String name)throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void set(Object owner,String name,Object value)throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
    }
    private interface Checked {void run()throws Exception;}
    private static void gl(EglWorker w,Checked code) {
        try {((Handler)field(w,"handler")).post(()->{try{code.run();}catch(Throwable e){fail(e);}});}
        catch(Exception e){fail(e);}
    }
    private static void fail(Throwable error){error.printStackTrace();System.exit(2);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void await(BooleanSupplier condition,Runnable next) {
        long until=SystemClock.uptimeMillis()+12000;
        main.post(new Runnable(){public void run(){
            try {
                if(condition.getAsBoolean()){next.run();return;}
                check(SystemClock.uptimeMillis()<until,"Timeout variant="+variant+" error="+worker.error);
                main.postDelayed(this,25);
            }catch(Throwable error){fail(error);}
        }});
    }
    private static ImageReader reader() {
        ImageReader r=ImageReader.newInstance(240,520,PixelFormat.RGBA_8888,3);
        r.setOnImageAvailableListener(source->{try(Image image=source.acquireLatestImage()){}},main);return r;
    }
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();main=new Handler(Looper.getMainLooper());variant=Integer.parseInt(args[0]);
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context system=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context context=ProbeContext.vivo(system,"/data/local/tmp/hyperextreme-vivo-recovery");
        worker=new EglWorker(context,false);companion=new EglWorker(context,false);
        reader=reader();other=reader();
        worker.state(new EngineRegistry.Snapshot(1,1,SystemClock.uptimeMillis(),1));
        worker.attach(reader.getSurface(),240,520,variant,false);
        companion.attach(other.getSurface(),240,520,variant,false);
        worker.visible(true);
        await(()->worker.renderedFrames>0,()->gl(companion,()->{
            check(field(companion,"renderer")==null,"Hidden slot eagerly loaded renderer");
            gl(worker,()->{
                long frames=worker.renderedFrames;
                EGLDisplay display=(EGLDisplay)field(worker,"display");
                EGLSurface surface=(EGLSurface)field(worker,"eglSurface");
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(display,surface);set(worker,"eglSurface",EGL14.EGL_NO_SURFACE);
                worker.resumeDisplay();
                await(()->worker.recoveries>=1&&worker.error.isEmpty()&&worker.renderedFrames>frames,
                    ()->{System.out.println("EGL_RECOVERED variant="+variant+" retries="+worker.recoveries+" firstMs="+worker.firstFrameMs);hiddenFailure();});
            });
        }));
        main.postDelayed(()->fail(new AssertionError("Global timeout")),60000);
        Looper.loop();
    }
    private static void hiddenFailure() {
        worker.visible(false);
        gl(worker,()->{
            Method fail=EglWorker.class.getDeclaredMethod("fail",Throwable.class);fail.setAccessible(true);
            fail.invoke(worker,new IllegalStateException("probe:hidden-context-loss"));
            long frames=worker.renderedFrames,retries=worker.recoveries;
            main.postDelayed(()->gl(worker,()->{
                check(worker.renderedFrames==frames&&worker.recoveries==retries,"Hidden recovery did GPU work");
                check(field(worker,"renderer")==null,"Hidden failure retained renderer");
                worker.visible(true);
                await(()->worker.recoveries>retries&&worker.error.isEmpty()&&worker.renderedFrames>frames,
                    RecoveryWorkerProbe::missingVsync);
            }),450);
        });
    }
    private static void missingVsync() {
        gl(worker,()->{
            ((FramePump)field(worker,"pump")).cancel();
            Handler handler=(Handler)field(worker,"handler");
            Method draw=EglWorker.class.getDeclaredMethod("draw");draw.setAccessible(true);
            FramePump pump=new FramePump(new FramePump.Driver(){
                Runnable timer;
                public void vsync(Runnable frame,long delay){} // Deliberately suspend display callbacks.
                public void cancelVsync(){}
                public void timer(Runnable frame,long delay){timer=frame;handler.postDelayed(frame,delay);}
                public void cancelTimer(){if(timer!=null)handler.removeCallbacks(timer);timer=null;}
            },()->{try{draw.invoke(worker);}catch(Exception error){fail(error);}});
            set(worker,"pump",pump);
            long frames=worker.renderedFrames;
            worker.options(new AliveRenderOptions(false,false,false,false,false,false,0,1.3f));
            worker.state(new EngineRegistry.Snapshot(1,0,SystemClock.uptimeMillis(),2));
            // Isolate the frame pump from the independent once-per-minute clock invalidation.
            gl(worker,()->handler.removeCallbacks((Runnable)field(worker,"minuteTick")));
            await(()->worker.renderedFrames>frames,()->{
                main.postDelayed(()->{
                    long settled=worker.renderedFrames;
                    main.postDelayed(()->{
                        check(worker.renderedFrames==settled,"Fallback ran indefinitely without vsync");
                        check(worker.error.isEmpty(),"Doze error "+worker.error);
                        System.out.println("NO_VSYNC_SETTLED variant="+variant+" frames="+settled);
                        if(variant==601)visibleDoze();else finish();
                    },400);
                },4000);
            });
        });
    }
    private static void visibleDoze() {
        worker.options(new AliveRenderOptions(false,true,false,false,false,false,0,1.3f));
        worker.display(Display.STATE_DOZE,false);
        long before=worker.renderedFrames;
        main.postDelayed(()->{
            long afterBudget=worker.renderedFrames;
            check(afterBudget>before+30,"Visible AOD did not animate without vsync");
            main.postDelayed(()->{
                check(worker.renderedFrames>afterBudget+5,"Visible AOD stopped after fallback budget");
                worker.display(Display.STATE_DOZE_SUSPEND,false);
                long beforeSuspend=worker.renderedFrames;
                main.postDelayed(()->{
                    check(worker.renderedFrames>beforeSuspend+5,"DOZE_SUSPEND froze visible animation");
                    worker.visible(false);
                    assertStopped(()->{
                        worker.display(Display.STATE_OFF,false);worker.visible(true);worker.resumeDisplay();
                        assertStopped(()->{
                            worker.display(Display.STATE_DOZE,false);
                            long frames=worker.renderedFrames;
                            await(()->worker.renderedFrames>frames,()->{
                                System.out.println("VISIBLE_DOZE_PASS variant="+variant+" frames="+worker.renderedFrames);
                                finish();
                            });
                        });
                    });
                },800);
            },800);
        },4200);
    }
    private static void assertStopped(Runnable next) {
        gl(worker,()->{
            long frames=worker.renderedFrames;
            main.postDelayed(()->{
                check(worker.renderedFrames==frames,"Hidden/OFF worker kept drawing");
                check(worker.error.isEmpty(),"Display lifecycle error "+worker.error);
                next.run();
            },500);
        });
    }
    private static void finish() {
        companion.visible(true);
        await(()->companion.renderedFrames>0,()->{
            worker.close();
            companion.resumeDisplay();
            long frames=companion.renderedFrames;
            await(()->companion.renderedFrames>frames,()->gl(companion,()->{
                Method fail=EglWorker.class.getDeclaredMethod("fail",Throwable.class);fail.setAccessible(true);
                fail.invoke(companion,new IllegalStateException("probe:detach-before-retry"));
                companion.detach();
                main.postDelayed(()->gl(companion,()->{
                    check(field(companion,"renderer")==null,"Detached generation resurrected");
                    check(!(Boolean)field(companion,"retryPosted"),"Detached generation retained retry");
                    Field users=EglWorker.class.getDeclaredField("displayUsers");users.setAccessible(true);
                    check(users.getInt(null)==0,"EGL display reference leaked");
                    companion.close();
                    main.postDelayed(()->{reader.close();other.close();System.out.println("RECOVERY_WORKER_PASS variant="+variant);System.exit(0);},200);
                }),1400);
            }));
        });
    }
}
