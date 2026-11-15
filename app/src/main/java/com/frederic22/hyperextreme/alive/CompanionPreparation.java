package com.frederic22.hyperextreme.alive;

import android.view.Display;

/** One shader-rendered companion buffer, only while the user is on the awake desktop. */
final class CompanionPreparation {
    static boolean supported(int variant) {
        return variant>=1&&variant<=19&&variant!=2&&variant!=5 || variant>=101&&variant<=105
            || variant>=201&&variant<=205 || variant>=301&&variant<=303
            || variant>=501&&variant<=503 || variant>=601&&variant<=606 || InfinityTheme.supports(variant);
    }
    static boolean allowed(boolean preview, int variant, boolean visible, int mode,
            int display, boolean interactive) {
        return !preview && supported(variant) && !visible && mode == 2
            && display == Display.STATE_ON && interactive;
    }
}
