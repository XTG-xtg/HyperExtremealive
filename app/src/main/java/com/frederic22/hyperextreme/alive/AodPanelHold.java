package com.frederic22.hyperextreme.alive;

/** Live OEM ambient brightness with a bounded, monotonic dark-to-bright lift. */
final class AodPanelHold {
    static boolean valid(float value){return Float.isFinite(value)&&value>0&&value<=1;}
    float brightness(float requested,float level){
        float gain=AodBrightness.normalize(level);
        if(gain==0)return requested;
        if(!valid(requested))return requested;
        if(gain<=1)return requested*gain;
        float low=.02f,high=.15f,bright=1+(gain-1)*.375f;
        float out=requested<=low?requested*gain:requested>=high?requested*bright
            :low*gain+(high*bright-low*gain)*(requested-low)/(high-low);
        return Math.min(1,Math.max(requested,Math.min(requested*gain,out)));
    }
    static boolean eligible(boolean owned,boolean fullAod,boolean interactive,int display,int policy,int state){
        // Full AOD initially uses STATE_ON, then DOZE/DOZE_SUSPEND; STATE_OFF is final.
        return owned&&fullAod&&!interactive&&display==0&&policy==1&&(state==2||state==3||state==4);
    }
}
