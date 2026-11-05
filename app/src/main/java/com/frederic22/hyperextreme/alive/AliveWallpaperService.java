package com.frederic22.hyperextreme.alive;

import android.app.KeyguardManager;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.*;

public abstract class AliveWallpaperService extends WallpaperService {
    protected abstract int slot();
    @Override public Engine onCreateEngine(){return new AliveEngine(slot());}
    final class AliveEngine extends Engine implements DisplayManager.DisplayListener {
        private final int slot;
        private EglWorker worker;
        private int previewMode=1;
        private long previewSequence;
        private final DisplayManager displays=getSystemService(DisplayManager.class);
        private final KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        private final PowerManager power=getSystemService(PowerManager.class);
        private boolean lastInteractive=power.isInteractive();
        private boolean photo(){int variant=Config.variant(AliveWallpaperService.this);return !isPreview()&&(variant==601||variant==602);}
        private final BroadcastReceiver events=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            String a=i.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(a))signal(0,"screen-off");
            else if(Intent.ACTION_USER_PRESENT.equals(a))signal(2,"user-present");
            else if(Intent.ACTION_SCREEN_ON.equals(a))signal(keyguard.isKeyguardLocked()?1:2,"screen-on");
        }};
        AliveEngine(int slot){this.slot=slot;}
        @Override public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);setTouchEventsEnabled(true);setOffsetNotificationsEnabled(true);
            worker=new EglWorker(AliveWallpaperService.this,isPreview());
            if(!isPreview())EngineRegistry.register(slot,this);IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);
            if(!isPreview())getContentResolver().notifyChange(Protocol.CONFIG,null);
            registerReceiver(events,f,Context.RECEIVER_EXPORTED);
            displays.registerDisplayListener(this,EngineRegistry.MAIN);
            Log.i(Protocol.TAG,"engine created slot="+slot);
        }
        @Override public void onSurfaceChanged(SurfaceHolder h,int format,int w,int height){super.onSurfaceChanged(h,format,w,height);transition();worker.attach(h.getSurface(),w,height,Config.variant(AliveWallpaperService.this),Config.dark(AliveWallpaperService.this));worker.visible(isVisible());}
        @Override public void onSurfaceDestroyed(SurfaceHolder h){worker.detach();super.onSurfaceDestroyed(h);}
        @Override public void onVisibilityChanged(boolean value){
            worker.visible(value);
            if(value){Display d=displays.getDisplay(Display.DEFAULT_DISPLAY);int state=d==null?Display.STATE_ON:d.getState();
                int mode=(state==Display.STATE_OFF||state==Display.STATE_DOZE||state==Display.STATE_DOZE_SUSPEND||photo()&&!power.isInteractive())?0:keyguard.isKeyguardLocked()?1:2;
                signal(mode,"visible-slot-"+slot);transition();}
            Log.i(Protocol.TAG,"visible slot="+slot+" value="+value);
        }
        @Override public void onOffsetsChanged(float x,float y,float xs,float ys,int xp,int yp){worker.offset(x-.5f,y-.5f);}
        @Override public void onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_MOVE)worker.offset(e.getX()/Math.max(1,getSurfaceHolder().getSurfaceFrame().width())-.5f,e.getY()/Math.max(1,getSurfaceHolder().getSurfaceFrame().height())-.5f);}
        @Override public Bundle onCommand(String action,int x,int y,int z,Bundle extras,boolean result){
            // Standard platform lifecycle commands only; arbitrary external "mode" commands are ignored.
            if("android.wallpaper.wakingup".equals(action))signal(keyguard.isKeyguardLocked()?1:2,"wallpaper-waking");
            if("android.wallpaper.goingtosleep".equals(action))signal(0,"screen-off");
            return super.onCommand(action,x,y,z,extras,result);
        }
        private void signal(int mode,String reason){
            long now=SystemClock.uptimeMillis();
            if(isPreview()){
                if(mode!=previewMode){worker.state(new EngineRegistry.Snapshot(previewMode,mode,now,++previewSequence));previewMode=mode;}
            }else EngineRegistry.signal(AliveWallpaperService.this,mode,now,reason);
        }
        void transition(){if(!isPreview())worker.state(EngineRegistry.snapshot());}
        void reload(){android.graphics.Rect r=getSurfaceHolder().getSurfaceFrame();worker.attach(getSurfaceHolder().getSurface(),r.width(),r.height(),Config.variant(AliveWallpaperService.this),Config.dark(AliveWallpaperService.this));}
        public void onDisplayAdded(int id){}
        public void onDisplayRemoved(int id){}
        public void onDisplayChanged(int id){if(id==Display.DEFAULT_DISPLAY){
            if(photo()) {
                boolean interactive=power.isInteractive();
                if(interactive!=lastInteractive)signal(interactive?(keyguard.isKeyguardLocked()?1:2):0,interactive?"display-awake":"display-asleep");
                lastInteractive=interactive;
            }
            if(isVisible()){onVisibilityChanged(true);worker.resumeDisplay();}
        }}
        @Override public void onDestroy(){unregisterReceiver(events);displays.unregisterDisplayListener(this);EngineRegistry.unregister(slot,this);worker.close();if(!isPreview())getContentResolver().notifyChange(Protocol.CONFIG,null);super.onDestroy();}
    }
}
