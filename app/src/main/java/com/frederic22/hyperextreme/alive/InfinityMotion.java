package com.frederic22.hyperextreme.alive;

/** Instance-owned, event-time animation. No polling, Android animators or global listeners. */
class InfinityMotion implements WallpaperMotion {
    static final int LOCK=0,HOME=1,AOD=2,STAR=3,PLUS=4,EXIT=5;
    final int theme;
    final float[] values=new float[6],reveal,shift;
    private final float[] from=new float[6],target=new float[6];
    private final float direction,entranceShift;
    private long last=-1,start=-1,gyroTime=-1,gyroMovement=-1;
    private int scene,previousScene;
    private float angle,fromAngle,targetAngle,gyro,gyroTarget,tilt;
    private float enterFrom=1,enterTarget=1,enter=1;
    private boolean homeToAod;
    private boolean running;
    private float downX;
    private long downTime=-1;
    private Object touchOwner;
    private final InfinityDesktopFollow desktop;
    private boolean gestureEnabled=true,gyroEnabled=true;

    void configureInput(boolean gesture,boolean gyro){
        gestureEnabled=gesture;
        gyroEnabled=gyro;
        if(!gesture){cancelTouch(null);desktop.reset();}
        if(!gyro){gyroTime=-1;tilt=0;gyroTarget=0;}
    }
    boolean gestureEnabled(){return gestureEnabled;}

    InfinityMotion(int variant,int initial){
        if(!InfinityTheme.supports(variant))throw new IllegalArgumentException("Infinity variant");
        if(initial<0||initial>2)throw new IllegalArgumentException("Infinity scene");
        theme=variant-801;
        reveal=new float[InfinityTheme.LAYERS[theme]];shift=new float[reveal.length];
        direction=theme==3||theme==5?-1:1;entranceShift=theme==1?.1f:.3f;
        scene=initial;desktop=new InfinityDesktopFollow(initial==2);settle(initial);
    }
    static InfinityMotion create(int variant,int scene){
        return variant>=811?new InfinityGeometryMotion(variant,scene):new InfinityMotion(variant,scene);
    }
    private void targets(int next){
        target[LOCK]=next==1?1:0;target[HOME]=next==2?1:0;target[AOD]=next==0?1:0;
        target[STAR]=next==2?.5f:.8f;target[PLUS]=next==2?0:1;target[EXIT]=next==1?0:1;
    }
    private void settle(int next){
        targets(next);System.arraycopy(target,0,values,0,values.length);
        enter=enterFrom=enterTarget=1;
        homeToAod=false;
        java.util.Arrays.fill(reveal,1);java.util.Arrays.fill(shift,0);running=false;
    }
    public void change(int next,boolean animate){
        if(next<0||next>2)throw new IllegalArgumentException("Infinity scene");
        if(!animate){cancelTouch(null);desktop.scene(next==2,false);previousScene=scene;scene=next;settle(next);return;}
        if(next==scene)return;
        cancelTouch(null);desktop.scene(next==2,true);
        boolean transientLockFromAod=scene==1&&next==2&&previousScene==0&&running&&(start<0||last<0||(last-start)<120_000_000L);
        // Snapshot evaluated values so a sleep/wake reversal never jumps to a canned start frame.
        System.arraycopy(values,0,from,0,values.length);targets(next);
        if(transientLockFromAod){
            // Fingerprint unlock emits AOD->LOCK->HOME within a few ms; suppress the transient
            // LOCK flash (lock background + tangram foreground) on the pure-black AOD screen.
            from[LOCK]=0;values[LOCK]=0;
            from[EXIT]=1;values[EXIT]=1;
            java.util.Arrays.fill(reveal,1);enter=enterFrom=1;
        }
        homeToAod=scene==2&&next==0;
        if(next==1&&values[EXIT]>.999f){java.util.Arrays.fill(reveal,0);enter=0;}
        enterFrom=enter;enterTarget=1;
        fromAngle=angle;targetAngle=angle+(homeToAod?22:10)*direction;start=last;
        previousScene=scene;scene=next;running=true;
        if(next==0){gyroTime=-1;gyroTarget=gyro;tilt=0;}
    }
    public void advance(long time,float speed){
        if(last>time)return;
        desktop.advance(time);
        double dt=last<0?0:(time-last)/1_000_000_000d;
        if(start<0)start=time;
        double elapsed=Math.max(0,(time-start)/1_000_000d);
        if(running){
            for(int i=0;i<values.length;i++){
                float duration=i<=AOD?(i==AOD?617:600):i==EXIT?750:1000;
                // Let the stars become legible before the desktop gradient disappears.
                if(homeToAod&&i<=AOD)duration=1200;
                float t=curve((float)(elapsed/duration),homeToAod&&i<=AOD||i==EXIT||i==AOD);
                values[i]=from[i]+(target[i]-from[i])*t;
            }
            angle=fromAngle+(targetAngle-fromAngle)*curve((float)(elapsed/(homeToAod?1600:2000)),false);
            enter=enterFrom+(enterTarget-enterFrom)*curve((float)(elapsed/650),true);
        }
        if(dt>0){
            float step=(float)Math.min(dt,.05);
            gyro+=(gyroTarget-gyro)*(1-(float)Math.exp(-step*8));
            float wanted=Math.max(-.1f,Math.min(.1f,tilt*1.3f));
            // Follow each chain at a time-based rate equivalent to the original 60 Hz coefficients.
            float revealFollow=1-(float)Math.pow(.8,step*60),shiftFollow=1-(float)Math.pow(.91,step*60);
            if(theme<=1){chain(0,reveal.length/2,1,wanted,revealFollow,shiftFollow);chain(reveal.length-1,reveal.length/2-1,-1,wanted,revealFollow,shiftFollow);}
            else if(theme==3)chain(reveal.length-1,-1,-1,wanted,revealFollow,shiftFollow);
            else chain(0,reveal.length,1,wanted,revealFollow,shiftFollow);
            if(dt>2.5){java.util.Arrays.fill(reveal,enter);java.util.Arrays.fill(shift,wanted);gyro=gyroTarget;}
        }
        if(running&&elapsed>=2500){running=false;System.arraycopy(target,0,values,0,6);java.util.Arrays.fill(reveal,enterTarget);angle=targetAngle;}
        last=time;
    }
    private void chain(int begin,int end,int step,float wanted,float revealFollow,float shiftFollow){
        for(int i=begin;i!=end;i+=step){
            float r=i==begin?enter:reveal[i-step],s=i==begin?wanted:shift[i-step];
            reveal[i]+= (r-reveal[i])*revealFollow;shift[i]+=(s-shift[i])*shiftFollow;
            if(Math.abs(r-reveal[i])<.001f)reveal[i]=r;
            if(Math.abs(s-shift[i])<.0001f)shift[i]=s;
        }
    }
    static float curve(float x,boolean layers){
        if(x<=0)return 0;if(x>=1)return 1;
        float x1=layers?.17f:.33f,x2=layers?.67f:.1f,y1=layers?.17f:0,low=0,high=1;
        for(int i=0;i<16;i++){float t=(low+high)*.5f,u=1-t;float at=3*u*u*t*x1+3*u*t*t*x2+t*t*t;if(at<x)low=t;else high=t;}
        float t=(low+high)*.5f,u=1-t;return 3*u*u*t*y1+3*u*t*t+t*t*t;
    }
    void gyroscope(float x,float y,long time,boolean landscape,int sensitivity){
        if(!gyroEnabled||scene==0||!Float.isFinite(x)||!Float.isFinite(y)||time<=gyroTime)return;
        float axis=landscape?x:y;
        if(gyroMovement<0||Math.abs(axis)>.05f)gyroMovement=time;
        if(gyroTime>=0){double dt=(time-gyroTime)/1_000_000_000d;if(dt>0&&dt<.25){
            float gain=(float)Math.pow(2,Math.max(-50,Math.min(50,sensitivity))/50d);
            tilt=scene==1&&time-gyroMovement>5_000_000_000L?0:Math.max(-1,Math.min(1,tilt+(float)(axis*dt)*gain));
            gyroTarget=tilt*(scene==1?18:40);
        }}
        gyroTime=time;
    }
    float desktopX(){return desktop.x();}
    float desktopY(){return desktop.y();}
    float desktopRotation(){return desktopX()*12;}
    float rotation(){return gyro-angle+desktopRotation();}
    float starAlpha(){return values[STAR]*aodStarGain();}
    float aodStarGain(){return 1+.6f*values[AOD];}
    float interaction(){return gyro;}
    float layerShift(int i){return shift[i]+(1-reveal[i])*direction*entranceShift;}
    public void follow(float x,float y){follow(x,y,Math.max(0,last));}
    void follow(float x,float y,long time){if(gestureEnabled)desktop.page(x,y,time);}
    void swipe(float distance){
        if(!gestureEnabled||scene==0||!Float.isFinite(distance)||Math.abs(distance)<.04f)return;
        System.arraycopy(values,0,from,0,6);targets(scene);fromAngle=angle;
        targetAngle=angle+Math.copySign(10,distance);enterFrom=enter;
        homeToAod=false;start=last;running=true;
    }
    void touch(Object owner,int action,float x,float y,long time){
        if(!gestureEnabled||scene==0)return;
        if(action==0){touchOwner=owner;downX=x;downTime=time;}
        else if(touchOwner!=owner||downTime<0)return;
        if(!Float.isFinite(x)||!Float.isFinite(y)){cancelTouch(owner);return;}
        if(scene==2)desktop.touch(action,x,y,time);
        else if(action==1&&time>=downTime&&time-downTime<500_000_000L)swipe(downX-x);
        if(action==1||action==3||action==5||action==6)cancelTouch(owner);
    }
    void cancelTouch(Object owner){
        if(owner!=null&&touchOwner!=owner)return;
        downTime=-1;touchOwner=null;desktop.cancel();
    }
    public void pause(){cancelTouch(null);last=-1;gyroTime=-1;}
    public boolean active(){
        if(running||desktop.active()||Math.abs(gyroTarget-gyro)>.005f)return true;
        float wanted=Math.max(-.1f,Math.min(.1f,tilt*1.3f));
        for(float s:shift)if(Math.abs(s-wanted)>.0001f)return true;
        return false;
    }
}
