package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.FrameLayout;
import java.util.function.Consumer;

/** Private-ROM view errors must not escape into the host's measure/draw traversal. */
final class GuardedClockFrame extends FrameLayout {
    private final Consumer<Throwable> failure;
    private final Runnable ready;
    private boolean broken, reported;
    GuardedClockFrame(Context context, Consumer<Throwable> failure, Runnable ready) {
        super(context); this.failure = failure; this.ready = ready;
    }
    private void fail(Throwable error) {
        if (broken) return;
        broken = true; post(() -> failure.accept(error));
    }
    @Override protected void onMeasure(int width, int height) {
        if (!broken) try { super.onMeasure(width, height); return; }
        catch (RuntimeException | LinkageError error) { fail(error); }
        setMeasuredDimension(MeasureSpec.getSize(width), 0);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (broken) return;
        try {
            super.onLayout(changed, l, t, r, b);
            if (getMeasuredHeight() <= 0) { fail(new IllegalStateException("Empty clock layout")); return; }
            if (!reported) { reported = true; post(() -> { if (!broken) ready.run(); }); }
        } catch (RuntimeException | LinkageError error) { fail(error); }
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        if (!broken) try { super.dispatchDraw(canvas); }
        catch (RuntimeException | LinkageError error) { fail(error); }
    }
}
