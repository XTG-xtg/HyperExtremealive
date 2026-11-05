package com.frederic22.hyperextreme.alive;

public final class StereoMotionTest {
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        StereoMotion motion = new StereoMotion(2);
        check(motion.shiftX() == 0 && motion.shiftY() == 0, "neutral initial position");
        motion.advance(1_000_000_000L, 1);
        motion.gyroscope(0, 1, 0, 1_000_000_000L);
        motion.gyroscope(0, 1, 0, 1_020_000_000L);
        motion.advance(1_020_000_000L, 1);
        check(Math.abs(motion.shiftX()) > .0001f, "real gyro drives parallax");
        check(Float.isFinite(motion.shiftY()), "finite vertical shift");
        motion.gyroscope(Float.NaN, 1, 0, 1_040_000_000L);
        motion.advance(1_040_000_000L, 1);
        check(Float.isFinite(motion.shiftX()), "invalid sample rejected");
        motion.pause();
        for (int i = 0; i < 150; i++) motion.advance(1_060_000_000L + i * 20_000_000L, 1);
        check(Math.abs(motion.shiftX()) < .0001f, "returns to neutral without sensor events");
        check(StereoMotion.multiplier(0) == 1, "neutral sensitivity");
        System.out.println("PASS 6 stereo motion assertions");
    }
}
