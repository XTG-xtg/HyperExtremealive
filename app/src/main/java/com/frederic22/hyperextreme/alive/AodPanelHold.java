package com.frederic22.hyperextreme.alive;

/** One physical-display session. The wallpaper shader remains responsible for the 0..5 gain. */
final class AodPanelHold {
    private float entry=Float.NaN;
    void clear(){entry=Float.NaN;}
    static boolean valid(float value){return Float.isFinite(value)&&value>0&&value<=1;}
    float brightness(float requested,float normal,float level,boolean eligible){
        if(!eligible||AodBrightness.normalize(level)==0){clear();return requested;}
        // OFF/proximity and unspecified requests must retain their native meaning.
        if(!valid(requested))return requested;
        if(!valid(entry))entry=valid(normal)?normal:requested;
        return entry;
    }
    static boolean eligible(boolean owned,boolean fullAod,boolean interactive,int display,int policy,int state){
        // Full AOD initially uses STATE_ON, then DOZE/DOZE_SUSPEND; STATE_OFF is final.
        return owned&&fullAod&&!interactive&&display==0&&policy==1&&(state==2||state==3||state==4);
    }
}
