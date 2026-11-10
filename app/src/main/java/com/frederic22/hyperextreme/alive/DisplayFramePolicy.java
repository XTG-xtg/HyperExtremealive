package com.frederic22.hyperextreme.alive;

import android.view.Display;

/** Display power, not interactivity, determines whether an AOD frame can still be seen. */
final class DisplayFramePolicy {
    static boolean canRender(boolean preview, int display) {
        return preview || display != Display.STATE_OFF;
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
