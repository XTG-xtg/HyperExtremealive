// Derived from OpenAlive 2d54c72a573b2f66e20b558283bef58cdb0ada60; MIT; see docs/alive/OPENALIVE_LICENSE and docs/alive/PROVENANCE.json.
package com.frederic22.hyperextreme.alive;

/** Visible frame time, matching Animator.pause/resume for the imported wallpaper tracks. */
final class WallpaperTransitionTime {
    private long last=-1,elapsed;
    void restart(){elapsed=0;last=-1;}
    void pause(){last=-1;}
    float advance(long now){
        if(last>=0)elapsed+=Math.max(0,now-last);
        last=now;
        return elapsed/1_000_000f;
    }
}
