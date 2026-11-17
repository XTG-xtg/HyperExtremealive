package com.frederic22.hyperextreme.alive;

/** Bounded hold then cross-fade, independent of Android callbacks for deterministic regressions. */
final class AodWakeBlend {
    static final long WINDOW=2500,HOLD=1000,FADE=1000;
    private float ambient=Float.NaN,raw,from,target,last;
    private long wake=-1,stableSince,fadeStart=-1;
    private long duration=FADE;
    void cancel(){ambient=Float.NaN;wake=-1;fadeStart=-1;}
    void ambient(float value){cancel();if(AodPanelHold.valid(value))ambient=value;}
    boolean begin(long now){
        if(wake>=0)return false;
        if(!AodPanelHold.valid(ambient))return false;
        wake=now;last=from=ambient;raw=target=ambient;stableSince=now;fadeStart=-1;
        return true;
    }
    boolean active(long now){return wake>=0&&now>=wake&&now-wake<WINDOW;}
    float sample(float requested,long now,float cap){
        if(!AodPanelHold.valid(requested)||!AodPanelHold.valid(cap)||!active(now)){
            cancel();return requested;
        }
        boolean changed=Math.abs(requested-raw)>Math.max(.00001f,Math.abs(raw)*.02f);
        if(changed){
            if(fadeStart>=0){
                from=interpolate(now);fadeStart=now;duration=Math.min(FADE,wake+WINDOW-now);target=requested;
            }
            raw=requested;stableSince=now;
        }
        if(fadeStart<0&&(now-stableSince>=HOLD||now-wake>=WINDOW-FADE)){
            fadeStart=now;duration=Math.min(FADE,wake+WINDOW-now);target=requested;
        }
        last=Math.min(cap,fadeStart<0?from:interpolate(now));
        // A newly reduced thermal cap takes precedence immediately, including the fade origin.
        from=Math.min(from,cap);target=Math.min(target,cap);
        return last;
    }
    private float interpolate(long now){
        float t=Math.max(0,Math.min(1,(now-fadeStart)/(float)Math.max(1,duration)));
        t=t*t*(3-2*t);
        return from+(target-from)*t;
    }
}
