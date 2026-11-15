package com.frederic22.hyperextreme.alive;

/** Infinity 1.0's AOD / facets / gradient modes, advanced by the host's frame clock. */
final class InfinityGeometryMotion extends InfinityMotion {
    static final int SCALE=0,ROTATE=1,LOCK_BG=2,HOME_BG=3,FACES=4,DOTS=5,LINES=6,STARS=7,BRIGHT=8;
    final float[] geometry=new float[9];
    final float[] groups={1,1,1,1,1};
    private final float[] fromGeometry=new float[9],targetGeometry=new float[9];
    private final boolean starProfile;
    private int mode;
    private long start=-1;
    private float elapsed;
    private boolean moving;
    private int scene;
    private float gestureAngle,gestureFrom,gestureTarget;
    private long gestureStart=-1,last=-1;
    private boolean gesturing;

    InfinityGeometryMotion(int variant,int scene){
        super(variant,scene);this.scene=scene;starProfile=(variant-811)%2==0;mode=mode(scene);
        targets(mode);System.arraycopy(targetGeometry,0,geometry,0,geometry.length);
    }
    private int mode(int scene){return scene==0?1:starProfile?(scene==1?1:2):(scene==1?2:3);}
    private void targets(int next){
        java.util.Arrays.fill(targetGeometry,0);
        targetGeometry[SCALE]=next==1?1:next==2?1.1f:1.5f;
        targetGeometry[ROTATE]=next==1?35:next==2?0:-20;
        targetGeometry[LOCK_BG]=next==2?1:0;targetGeometry[HOME_BG]=next==3?1:0;
        targetGeometry[FACES]=next==2?.5f:0;targetGeometry[DOTS]=next==1?1:0;
        targetGeometry[LINES]=next==1?.8f:0;targetGeometry[STARS]=next==1?1:0;
        targetGeometry[BRIGHT]=next==1?.4f:next==2?.5f:.6f;
    }
    @Override public void change(int next,boolean animate){
        if(animate&&next==scene)return;
        // Desktop follow fades separately; only absorb the old gyro/swipe into the AOD track.
        float displayedRotation=geometry[ROTATE]+rotationY()-desktopRotation();
        super.change(next,animate);scene=next;
        if(next==0){gestureAngle=0;gesturing=false;}
        int destination=mode(next);
        if(animate&&destination==mode&&next!=0)return;
        mode=destination;targets(mode);
        System.arraycopy(geometry,0,fromGeometry,0,geometry.length);
        if(next==0){fromGeometry[ROTATE]=displayedRotation;if(animate)geometry[ROTATE]=displayedRotation;}
        moving=animate;start=-1;elapsed=0;
        if(!animate){System.arraycopy(targetGeometry,0,geometry,0,geometry.length);java.util.Arrays.fill(groups,1);}
    }
    @Override public void advance(long time,float speed){
        if(last>time)return;
        super.advance(time,speed);
        last=time;
        if(gesturing){
            if(gestureStart<0)gestureStart=time;
            float t=(time-gestureStart)/1_000_000_000f;gestureAngle=gestureFrom+(gestureTarget-gestureFrom)*curve(t,false);
            if(t>=1)gesturing=false;
        }
        if(!moving)return;
        if(start<0)start=time;
        elapsed=Math.max(0,(time-start)/1_000_000f);
        for(int i=0;i<geometry.length;i++){
            float delay=0,duration=mode==1?3500:mode==2?2100:1667;
            if(i==FACES&&mode==2){delay=400;duration=1600;}
            else if(i==LOCK_BG||i==HOME_BG){delay=mode==3&&i==HOME_BG?133:0;duration=mode==3?900:1000;}
            else if((i==DOTS||i==STARS||i==LINES)&&mode==2){delay=750;duration=700;}
            else if(i==BRIGHT&&mode==1){delay=3400;duration=600;}
            float t=curve((elapsed-delay)/duration,false);
            geometry[i]=fromGeometry[i]+(targetGeometry[i]-fromGeometry[i])*t;
        }
        for(int i=0;i<5;i++)groups[i]=mode==1?curve((elapsed-(500+i*150))/2500,false):1;
        if(elapsed>=(mode==1?4000:mode==2?2100:1667)){
            moving=false;System.arraycopy(targetGeometry,0,geometry,0,geometry.length);java.util.Arrays.fill(groups,1);
        }
    }
    float pulseDots(){
        if(!moving||mode==1)return 0;
        float peak=mode==2?750:667,fadeStart=mode==2?750:867,fadeDuration=mode==2?700:800;
        return curve(elapsed/peak,false)*(1-curve((elapsed-fadeStart)/fadeDuration,false));
    }
    float pulseLines(){
        if(!moving||mode==1)return 0;
        float peak=mode==2?750:367,fadeStart=peak,fadeDuration=mode==2?700:900;
        return (mode==2?.8f:.5f)*curve(elapsed/peak,false)*(1-curve((elapsed-fadeStart)/fadeDuration,false));
    }
    @Override void swipe(float distance){
        if(scene==0||!Float.isFinite(distance)||Math.abs(distance)<.04f)return;
        gestureFrom=gestureAngle;gestureTarget=gestureAngle+Math.copySign(20,distance);gestureStart=-1;gesturing=true;
    }
    float rotationY(){return (scene==0?0:interaction()+gestureAngle)+desktopRotation();}
    @Override public void pause(){super.pause();last=-1;}
    @Override public boolean active(){return moving||gesturing||super.active();}
}
