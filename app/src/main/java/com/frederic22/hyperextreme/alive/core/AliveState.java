package com.frederic22.hyperextreme.alive.core;

/** Device-independent, monotonic state reducer. Values match the upstream renderers. */
public final class AliveState {
    public static final int AOD = 0, LOCK = 1, HOME = 2;
    public int previous = LOCK, current = LOCK;
    public long startedMs, sequence, lastEventMs = -1, homeConfirmedMs = -10000;
    public boolean accept(int next, long atMs, String reason) {
        if (next < AOD || next > HOME || atMs < lastEventMs) return false;
        // A late wake/lock hint must not reverse an already authenticated unlock.
        if (next == LOCK && current == HOME && ("screen-on".equals(reason) || "power-wake".equals(reason)
                || "display-awake".equals(reason) || "wallpaper-waking".equals(reason))) return false;
        if (next == LOCK && atMs - homeConfirmedMs < 600 && !"screen-off".equals(reason)
                && !"keyguard-exit-cancelled".equals(reason)) return false;
        lastEventMs = atMs;
        if (next == HOME) homeConfirmedMs = atMs;
        if (next == AOD) homeConfirmedMs = -10000;
        if (current == next) return false;
        previous = current; current = next; startedMs = atMs; sequence++;
        return true;
    }
    public static String name(int value) {
        switch (value) { case AOD: return "AOD"; case LOCK: return "LOCK"; case HOME: return "HOME"; default: throw new IllegalArgumentException("mode=" + value); }
    }
}
