package com.frederic22.hyperextreme.alive;

/** MiuiHome uses -1 for automatic page steps; zero still means a non-scrolling axis. */
final class InfinityLauncherOffset {
    static float centered(float offset,float step){
        if(!Float.isFinite(offset)||!Float.isFinite(step)||step==0)return 0;
        if(step<0&&step!=-1)return 0;
        return Math.max(0,Math.min(1,offset))-.5f;
    }
}
