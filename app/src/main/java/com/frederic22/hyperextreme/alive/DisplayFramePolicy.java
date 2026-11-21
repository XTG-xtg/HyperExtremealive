package com.frederic22.hyperextreme.alive;

import android.view.Display;

/** Display power, not interactivity, determines whether an AOD frame can still be seen. */
final class DisplayFramePolicy {
    static boolean canRender(boolean preview, int display) {
        return preview || display != Display.STATE_OFF;
    }

    static boolean sceneMatches(boolean preview, int display, boolean interactive, int mode) {
        // The display can retain DOZE for a frame after PowerManager has woken.
        // Wait for the accepted scene instead of submitting the old AOD image.
        return preview || display == Display.STATE_UNKNOWN || (mode == 0) == !interactive;
    }

    /** Recheck producer-side lifecycle signals even before the GL queue consumes them. */
    static boolean canSubmit(boolean preview,boolean visible,boolean requestedVisible,
            int display,int requestedDisplay,long sequence,long requestedSequence) {
        return visible&&requestedVisible&&canRender(preview,display)&&canRender(preview,requestedDisplay)
            &&sequence==requestedSequence;
    }

    static long fallback(boolean preview, int display, boolean interactive,
            boolean bootstrap, long delay, long now, long fallbackUntil) {
        if (!canRender(preview, display)) return -1;
        if (bootstrap) return 0;
        boolean visibleDoze = !preview && (display == Display.STATE_DOZE
            || display == Display.STATE_DOZE_SUSPEND || display == Display.STATE_ON && !interactive);
        // Keep transitions smooth when the OEM suspends vsync; settled motion targets 30fps.
        if (visibleDoze) return delay == 0 ? 16 : 33;
        return now < fallbackUntil ? 100 : -1;
    }
}
