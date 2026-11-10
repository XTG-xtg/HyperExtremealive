package com.frederic22.hyperextreme.alive;

/** Does not consume motion events: drags, long presses and multitouch stay system-owned. */
final class LiquidTapGesture {
    private boolean selected,valid;
    private float downX,downY;
    private long downAt;
    private String scene="";
    boolean update(int action,int pointers,float x,float y,long at,boolean eligible,boolean hit,String key,float slop,long timeout){
        if(action==0){selected=valid=eligible&&hit&&pointers==1;downX=x;downY=y;downAt=at;scene=key;}
        if(!eligible||!scene.equals(key)||pointers!=1||at-downAt>timeout||Math.hypot(x-downX,y-downY)>slop||action==3)valid=false;
        boolean tap=action==1&&valid&&hit;
        if(action==1||action==3)valid=false;
        return tap;
    }
    boolean suppressDoubleTap(){return selected;}
}
