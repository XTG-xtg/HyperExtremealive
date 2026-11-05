import com.frederic22.hyperextreme.alive.core.AliveState;

public final class StateTest {
    private static int count;
    private static void check(boolean value, String name) {
        count++;
        if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        AliveState s = new AliveState();
        check(s.accept(0, 1000, "screen-off"), "lock to AOD");
        check(s.accept(2, 1050, "biometric"), "direct unlock");
        check(!s.accept(1, 1100, "power-wake"), "reject late wake hint");
        check(s.current == 2, "no reverse animation");
        check(!s.accept(0, 1000, "screen-off"), "reject stale event");
        check(s.accept(0, 1200, "screen-off"), "fresh screen off wins");
        check(s.accept(1, 1250, "screen-on"), "normal wake");
        check(s.accept(2, 1600, "user-present"), "normal unlock");
        check(!s.accept(2, 1700, "visible-slot-1"), "handoff does not restart");
        check(s.previous == 1, "retained predecessor");
        check(s.startedMs == 1600, "retained epoch");
        check(!s.accept(8, 1800, "bad"), "unknown mode rejected");
        check(s.sequence == 5, "transition sequence");
        check(!s.accept(1, 1750, "scrim-keyguard"), "scrim does not undo fingerprint");
        check(s.accept(1, 1900, "scrim-keyguard"), "new keyguard outside guard window");
        check(!s.accept(-1, 1950, "bad"), "negative mode rejected");
        check(AliveState.name(0).equals("AOD"), "upstream mode zero means AOD");
        check(s.accept(0, 2000, "screen-off"), "new sleep cycle");
        System.out.println("PASS " + count + " state assertions");
    }
}
