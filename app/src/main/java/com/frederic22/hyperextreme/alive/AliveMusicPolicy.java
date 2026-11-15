package com.frederic22.hyperextreme.alive;

/** Native media controls must not replace a healthy live wallpaper. */
public final class AliveMusicPolicy {
    public enum Action { NONE, RESTORE_LOCK }

    private AliveMusicPolicy() { }

    public static Action decide(boolean enabled, boolean interactive, boolean musicScreen,
            boolean lockOurs, boolean homeOurs) {
        if (!enabled || !interactive || musicScreen) return Action.NONE;
        // HOME anchors ownership. Do not reclaim wallpapers after the user left Alive.
        if (!lockOurs && homeOurs) return Action.RESTORE_LOCK;
        return Action.NONE;
    }
}
