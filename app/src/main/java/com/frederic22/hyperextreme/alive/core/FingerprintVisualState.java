package com.frederic22.hyperextreme.alive.core;

/** Generation-guarded visual suppression, never an authentication decision. */
public final class FingerprintVisualState {
    private long serial;
    private boolean suppressed;
    public long press() { suppressed = true; return ++serial; }
    public long token() { return serial; }
    public boolean suppressed() { return suppressed; }
    public boolean release(long token, boolean nativeStillPressed) {
        if (token != serial || nativeStillPressed) return false;
        suppressed = false; serial++; return true;
    }
    public void clear() { suppressed = false; serial++; }
}
