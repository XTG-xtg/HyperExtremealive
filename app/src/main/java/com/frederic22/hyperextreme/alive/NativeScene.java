// Derived from OpenAlive 2d54c72; MIT; see docs/alive/OPENALIVE_LICENSE and PROVENANCE.json.
package com.frederic22.hyperextreme.alive;

final class NativeScene {
    static { System.loadLibrary("hyperextreme_photo"); }
    static native long create(int width,int height,int mode,int aod,int lock,int home);
    static native void texture(long handle,int slot,int texture,int width,int height);
    static native void tint(long handle,int color);
    static native void frameCrop(long handle,float x,float y,float size,float angle);
    static native void photoCrop(long handle,float x,float y,float zoom);
    static native boolean frame(long handle,java.nio.ByteBuffer frame);
    static native boolean effects(long handle,java.nio.ByteBuffer effects);
    static native boolean render(long handle,int photo,int decorator,float photoAspect,java.nio.ByteBuffer frame);
    static native void destroy(long handle);
}
