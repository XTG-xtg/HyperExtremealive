package com.frederic22.hyperextreme.alive;

import android.animation.AnimatorSet;

/** Evaluate Android's original curves against the registry event time, not bind time. */
final class SceneTimeline {
    private AnimatorSet animation;
    private long origin = -1;

    void start(AnimatorSet next) {
        animation = next;
        origin = -1;
        next.start();
        next.pause();
    }

    void advance(long nanos) {
        if (animation == null || !animation.isStarted()) return;
        if (origin < 0) origin = nanos;
        long elapsed = Math.max(0, (nanos - origin) / 1_000_000L);
        if (elapsed >= animation.getTotalDuration()) animation.end();
        else animation.setCurrentPlayTime(Math.max(0, elapsed - animation.getStartDelay()));
    }
}
