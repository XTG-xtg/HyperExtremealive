package com.frederic22.hyperextreme.alive;

import java.lang.ref.WeakReference;
import java.util.List;

/** Scoped to an owned full-AOD session; normal display brightness is never intercepted. */
final class AodSessionBrightness {
    private WeakReference<Object> owner=new WeakReference<>(null);
    private float panel=Float.NaN;

    synchronized void wallpaper(Object host,List<?> args,float level,boolean owned){
        if(!owned||AodBrightness.normalize(level)==0||!SystemAodDimming.candidate(args,AodBrightness.MAX)){
            clear();return;
        }
        if(owner.get()!=host){clear();owner=new WeakReference<>(host);}
    }
    synchronized void clear(){owner.clear();panel=Float.NaN;}
    private boolean active(float level){
        if(owner.get()==null||AodBrightness.normalize(level)==0){clear();return false;}
        return true;
    }
    synchronized float panel(float requested,float level){
        if(!active(level)||!Float.isFinite(requested)||requested<=0||requested>1)return requested;
        // Retain the first valid DOZE brightness, not the preceding lock/home brightness.
        if(!Float.isFinite(panel))panel=requested;
        return panel;
    }
    synchronized float scrim(float requested,float level){
        // Full black can be proximity/privacy hiding. Never turn that into a visible AOD.
        return active(level)&&Float.isFinite(requested)&&requested>0&&requested<1?0:requested;
    }
}
