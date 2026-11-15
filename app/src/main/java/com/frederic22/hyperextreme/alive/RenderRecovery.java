package com.frederic22.hyperextreme.alive;

/** Per-surface retry budget. Only sustained drawing or a later visibility cycle rearms it. */
final class RenderRecovery {
    private static final long[] BACKOFF = {100, 350, 1000};
    private int failures;
    private long firstGood = -1, lastFailure = -1;

    void reset() { failures = 0; firstGood = lastFailure = -1; }
    void drawn(long now) { if (firstGood < 0) firstGood = now; }
    long failed(long now) {
        if (firstGood >= 0 && now - firstGood >= 5000) failures = 0;
        firstGood = -1;
        lastFailure = now;
        return ++failures <= BACKOFF.length ? BACKOFF[failures - 1] : -1;
    }
    boolean resume(long now) {
        if (failures > BACKOFF.length && now - lastFailure >= 10000) {
            reset();
            return true;
        }
        return failures <= BACKOFF.length;
    }
}
