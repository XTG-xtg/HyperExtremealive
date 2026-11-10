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
        private long lastLiquidTap;
        private boolean surfaceAttached;
        private int surfaceWidth,surfaceHeight,surfaceFormat;
        private final DisplayManager displays=getSystemService(DisplayManager.class);
        private final KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        private final PowerManager power=getSystemService(PowerManager.class);
        private boolean lastInteractive=power.isInteractive();
        private int lastDisplayState=Display.STATE_UNKNOWN;
        private final BroadcastReceiver events=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            updateDisplay();
            String a=i.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(a))signal(0,"screen-off");
            else if(Intent.ACTION_USER_PRESENT.equals(a))signal(2,"user-present");
            else if(Intent.ACTION_SCREEN_ON.equals(a))signal(keyguard.isKeyguardLocked()?1:2,"screen-on");
        }};
        AliveEngine(int slot){this.slot=slot;}
        @Override public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);setTouchEventsEnabled(true);setOffsetNotificationsEnabled(true);
            worker=new EglWorker(AliveWallpaperService.this,isPreview(),()->{
                if(slot==1&&!isPreview())EngineRegistry.prepareCompanion();
            });
            updateDisplay();
            if(!isPreview())EngineRegistry.register(slot,this);IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);
            if(!isPreview())signal(!power.isInteractive()?0:keyguard.isKeyguardLocked()?1:2,"engine-created");
            if(!isPreview())getContentResolver().notifyChange(Protocol.CONFIG,null);
            registerReceiver(events,f,Context.RECEIVER_EXPORTED);
            displays.registerDisplayListener(this,EngineRegistry.MAIN);
            Log.i(Protocol.TAG,"engine created slot="+slot);
        }
        @Override public void onSurfaceCreated(SurfaceHolder h){super.onSurfaceCreated(h);surfaceAttached=false;}
        @Override public void onSurfaceChanged(SurfaceHolder h,int format,int w,int height){
            super.onSurfaceChanged(h,format,w,height);updateDisplay();transition();
            if(!surfaceAttached||surfaceWidth!=w||surfaceHeight!=height||surfaceFormat!=format){
                surfaceAttached=true;surfaceWidth=w;surfaceHeight=height;surfaceFormat=format;
                worker.attach(h.getSurface(),w,height,Config.variant(AliveWallpaperService.this),Config.dark(AliveWallpaperService.this));
            }else worker.resumeDisplay();
            worker.visible(isVisible());
            if(!isPreview())EngineRegistry.prepareCompanion();
        }
        @Override public void onSurfaceRedrawNeeded(SurfaceHolder h){super.onSurfaceRedrawNeeded(h);worker.resumeDisplay();}
        @Override public void onSurfaceDestroyed(SurfaceHolder h){surfaceAttached=false;worker.detach();super.onSurfaceDestroyed(h);}
        @Override public void onVisibilityChanged(boolean value){
            updateDisplay();
            worker.visible(value);
            if(value){Display d=displays.getDisplay(Display.DEFAULT_DISPLAY);int state=d==null?Display.STATE_ON:d.getState();
                int mode=(state==Display.STATE_OFF||state==Display.STATE_DOZE||state==Display.STATE_DOZE_SUSPEND||!power.isInteractive())?0:keyguard.isKeyguardLocked()?1:2;
                signal(mode,"visible-slot-"+slot);transition();}
            Log.i(Protocol.TAG,"visible slot="+slot+" value="+value);
        }
        @Override public void onOffsetsChanged(float x,float y,float xs,float ys,int xp,int yp){worker.offset(x-.5f,y-.5f);}
        @Override public void onTouchEvent(MotionEvent e){
            // Keyguard taps arrive once through the selected-region bridge. Some ROMs also
            // mirror the same stream to WallpaperService; never inject the impulse twice.
            if(!isPreview()&&Config.variant(AliveWallpaperService.this)==701&&keyguard.isKeyguardLocked())return;
            float x=e.getX()/Math.max(1,getSurfaceHolder().getSurfaceFrame().width()),y=e.getY()/Math.max(1,getSurfaceHolder().getSurfaceFrame().height());
            worker.touch(e.getActionMasked(),x,y);
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE)worker.offset(x-.5f,y-.5f);
        }
        @Override public Bundle onCommand(String action,int x,int y,int z,Bundle extras,boolean result){
            // Standard platform lifecycle commands only; arbitrary external "mode" commands are ignored.
            if("android.wallpaper.wakingup".equals(action))signal(keyguard.isKeyguardLocked()?1:2,"wallpaper-waking");
            if("android.wallpaper.goingtosleep".equals(action))signal(0,"screen-off");
            return super.onCommand(action,x,y,z,extras,result);
        }
        // Hidden platform callback on ROMs that deliver ambient changes to wallpaper engines.
        public void onAmbientModeChanged(boolean ambient,long duration){
            updateDisplay();
            // Ambient hints cannot wake the scene; PowerManager and the wake fence arbitrate.
            if(ambient)signal(0,"wallpaper-ambient");
            else if(power.isInteractive())signal(keyguard.isKeyguardLocked()?1:2,"wallpaper-ambient-exit");
        }
        private void signal(int mode,String reason){
            long now=SystemClock.uptimeMillis();
            if(isPreview()){
                if(mode!=previewMode){worker.state(new EngineRegistry.Snapshot(previewMode,mode,now,++previewSequence));previewMode=mode;}
            }else EngineRegistry.signal(AliveWallpaperService.this,mode,now,reason);
        }
        void transition(){if(!isPreview())worker.state(EngineRegistry.snapshot());}
        LiquidTouchMap liquidMap(){return worker==null?null:worker.liquidMap;}
        Bundle renderStatus(){
            Bundle b=new Bundle();if(worker==null)return b;
            b.putBoolean("visible",isVisible());b.putBoolean("surface",surfaceAttached);
            b.putLong("generation",worker.activeGeneration);b.putLong("drawn_generation",worker.renderedGeneration);
            b.putLong("frames",worker.renderedFrames);b.putLong("first_frame_ms",worker.firstFrameMs);
            b.putLong("init_ms",worker.initMs);b.putLong("recoveries",worker.recoveries);
            b.putInt("display_state",worker.displayState);b.putBoolean("display_interactive",worker.displayInteractive);
            b.putLong("prepared_generation",worker.preparedGeneration);
            b.putString("error",worker.error);b.putString("last_failure",worker.lastFailure);return b;
        }
        void liquidTap(float x,float y,long at){
            LiquidTouchMap map=liquidMap();
            if(isPreview()||!isVisible()||at<=lastLiquidTap||map==null||!map.scene.equals(VivoOptions.load(AliveWallpaperService.this,701,false).json())||!map.hit(x,y))return;
            lastLiquidTap=at;worker.touch(MotionEvent.ACTION_DOWN,x,y);
            worker.touch(MotionEvent.ACTION_UP,x,y);
            Log.i(Protocol.TAG,"liquid tap accepted slot="+slot+" x="+x+" y="+y);
        }
        void clock(ClockSnapshot snapshot){if(!isPreview())worker.clock(snapshot);}
        boolean readyForCompanion(){return worker!=null&&worker.readyForCompanion();}
        void prepareCompanion(){if(worker!=null)worker.prepareCompanion();}
        void reload(){if(!surfaceAttached)return;android.graphics.Rect r=getSurfaceHolder().getSurfaceFrame();worker.attach(getSurfaceHolder().getSurface(),r.width(),r.height(),Config.variant(AliveWallpaperService.this),Config.dark(AliveWallpaperService.this));EngineRegistry.prepareCompanion();}
        public void onDisplayAdded(int id){}
        public void onDisplayRemoved(int id){}
        private boolean updateDisplay(){
            Display display=displays.getDisplay(Display.DEFAULT_DISPLAY);
            int value=display==null?Display.STATE_UNKNOWN:display.getState();
            boolean interactive=power.isInteractive();
            boolean changed=value!=lastDisplayState||interactive!=lastInteractive;
            lastDisplayState=value;lastInteractive=interactive;
            worker.display(value,interactive);
            return changed;
        }
        public void onDisplayChanged(int id){if(id==Display.DEFAULT_DISPLAY){
            boolean wasInteractive=lastInteractive;
            boolean changed=updateDisplay();
            if(!isPreview()&&wasInteractive!=lastInteractive)
                signal(lastInteractive?(keyguard.isKeyguardLocked()?1:2):0,lastInteractive?"display-awake":"display-asleep");
            // Refresh-rate/HDR notifications are not new wake events or doze draw budgets.
            if(isVisible()&&changed)transition();
        }}
        @Override public void onDestroy(){unregisterReceiver(events);displays.unregisterDisplayListener(this);EngineRegistry.unregister(slot,this);worker.close();if(!isPreview())getContentResolver().notifyChange(Protocol.CONFIG,null);super.onDestroy();}
    }
}
