// Derived from OpenAlive; MIT; see docs/alive/OPENALIVE_LICENSE and IMPORTED-VIVO.json.
package com.frederic22.hyperextreme.alive;

/** Full-screen AOD geometry from HyperOS KeyguardPanelViewController.
 * The wallpaper alone scales about (0.5, 0.4); clocks and widgets stay system-owned.
 */
final class VivoWallpaperZoom {
    private double value=1.05,velocity,target=1.05,omega=2*Math.PI/.5;
    private long previous;
    private boolean active;
    private boolean enabled=true;
    void enabled(boolean value,int mode,long now){enabled=value;mode(mode,false,now);}
    void mode(int mode,boolean animate,long now){
        sample(now);
        double next=!enabled||mode==0?1:1.05;
        if(!animate){value=target=next;velocity=0;active=false;previous=now;return;}
        if(target==next)return;
        target=next;omega=2*Math.PI/(mode==0?.72:.5);previous=now;active=true;
    }
    float sample(long now){
        if(active){
            double t=Math.max(0,(now-previous)/1e9),x=value-target,b=velocity+omega*x;
            double e=Math.exp(-omega*t);
            value=target+(x+b*t)*e;velocity=(velocity-omega*b*t)*e;
            if(Math.abs(value-target)<.00002&&Math.abs(velocity)<.0002){value=target;velocity=0;active=false;}
        }
        previous=now;return (float)value;
    }
    boolean active(){return active;}
    void pause(){value=target;velocity=0;active=false;}
}
