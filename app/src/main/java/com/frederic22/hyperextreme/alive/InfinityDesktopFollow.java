package com.frederic22.hyperextreme.alive;

/** Bounded desktop parallax. Page offsets win over touch deltas from the same swipe. */
final class InfinityDesktopFollow {
    private boolean home,dragging,pageMovedX,pageMovedY;
    private float pageX,pageY,downX,downY,dragX,dragY,x,y;
    private long last=-1;

    InfinityDesktopFollow(boolean home){this.home=home;}
    void scene(boolean value,boolean animate){
        home=value;cancel();
        if(!animate){x=targetX();y=targetY();}
    }
    void page(float horizontal,float vertical,long time){
        if(!home||!Float.isFinite(horizontal)||!Float.isFinite(vertical))return;
        advance(time);
        // Launcher offsets grow towards the next page, opposite the finger's motion.
        float nextX=clamp(-horizontal*2),nextY=clamp(-vertical*2);
        if(dragging){pageMovedX|=Math.abs(nextX-pageX)>.0001f;pageMovedY|=Math.abs(nextY-pageY)>.0001f;}
        pageX=nextX;pageY=nextY;
    }
    void touch(int action,float px,float py,long time){
        if(!home)return;
        advance(time);
        if(!Float.isFinite(px)||!Float.isFinite(py)){cancel();return;}
        if(action==0){
            downX=px;downY=py;dragX=dragY=0;pageMovedX=pageMovedY=false;dragging=true;
        }else if(action==2&&dragging){dragX=clamp((px-downX)*2);dragY=clamp((py-downY)*2);}
        else if(action==1||action==3||action==5||action==6)cancel();
    }
    void cancel(){dragging=false;dragX=dragY=0;pageMovedX=pageMovedY=false;}
    private float targetX(){return home?clamp(pageX+(dragging&&!pageMovedX?dragX:0)):0;}
    private float targetY(){return home?clamp(pageY+(dragging&&!pageMovedY?dragY:0)):0;}
    void advance(long time){
        if(time<last)return;
        if(last>=0){
            // Input advances the old target first: a long idle gap cannot snap a new gesture.
            double seconds=(time-last)/1_000_000_000d;
            float blend=1-(float)Math.exp(-seconds*(dragging?22:12));
            x=approach(x,targetX(),blend);y=approach(y,targetY(),blend);
        }
        last=time;
    }
    private static float approach(float value,float target,float blend){
        float next=value+(target-value)*blend;return Math.abs(target-next)<.0001f?target:next;
    }
    private static float clamp(float value){return Math.max(-1,Math.min(1,value));}
    float x(){return x;}
    float y(){return y;}
    boolean active(){return x!=targetX()||y!=targetY();}
}
