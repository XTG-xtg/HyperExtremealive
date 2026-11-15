package com.frederic22.hyperextreme.alive;

/** One pending frame: vsync normally wins; a bounded timer covers suspended doze vsync. */
final class FramePump {
    interface Driver {
        void vsync(Runnable frame, long delay);
        void cancelVsync();
        void timer(Runnable frame, long delay);
        void cancelTimer();
    }
    private final Driver driver;
    private final Runnable draw;
    private long ticket, delay, deadline;
    private boolean pending;

    FramePump(Driver driver, Runnable draw) { this.driver = driver; this.draw = draw; }
    void request(long wait, long fallback) {
        if (pending && wait >= delay && (fallback < 0 || deadline >= 0 && fallback >= deadline)) return;
        cancel();
        pending = true;
        delay = wait;
        deadline = fallback;
        long token = ticket;
        Runnable frame = () -> {
            if (!pending || token != ticket) return;
            cancel();
            draw.run();
        };
        driver.vsync(frame, wait);
        if (fallback >= 0) driver.timer(frame, Math.max(wait, fallback));
    }
    void cancel() {
        ticket++;
        pending = false;
        driver.cancelVsync();
        driver.cancelTimer();
    }
}
