// Derived from OpenAlive 2d54c72a573b2f66e20b558283bef58cdb0ada60; MIT; see docs/alive/OPENALIVE_LICENSE and docs/alive/PROVENANCE.json.
package com.frederic22.hyperextreme.alive;

interface WallpaperMotion {
    void change(int scene,boolean animate);
    void advance(long time,float speed);
    void follow(float x,float y);
    void pause();
    boolean active();
}
