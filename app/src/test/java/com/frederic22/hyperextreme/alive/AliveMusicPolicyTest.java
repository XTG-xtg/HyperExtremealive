package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.frederic22.hyperextreme.alive.AliveMusicPolicy.Action.*;

public class AliveMusicPolicyTest {
    @Test public void disabledNeverTouchesWallpaper() {
        assertEquals(NONE, AliveMusicPolicy.decide(false, true, true, false, false));
    }

    @Test public void screenOffFreezesState() {
        assertEquals(NONE, AliveMusicPolicy.decide(true, false, true, true, true));
        assertEquals(NONE, AliveMusicPolicy.decide(true, false, false, false, false));
    }

    @Test public void nativeMusicControlsPreserveLiveLockWallpaper() {
        assertEquals(NONE, AliveMusicPolicy.decide(true, true, true, true, true));
    }

    @Test public void noStaticOrBlackSeedDuringMusic() {
        assertEquals(NONE, AliveMusicPolicy.decide(true, true, true, false, true));
    }

    @Test public void afterMusicReappliesMissingEngines() {
        assertEquals(RESTORE_LOCK, AliveMusicPolicy.decide(true, true, false, false, true));
        assertEquals(NONE, AliveMusicPolicy.decide(true, true, false, true, false));
        assertEquals(NONE, AliveMusicPolicy.decide(true, true, false, false, false));
        assertEquals(NONE, AliveMusicPolicy.decide(true, true, false, true, true));
    }
}
