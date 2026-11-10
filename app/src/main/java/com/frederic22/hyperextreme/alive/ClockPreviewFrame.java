package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Preview-only fitting. Original clock geometry and the SystemUI host stay untouched. */
final class ClockPreviewFrame extends FrameLayout {
    final FrameLayout stage;
    ClockPreviewFrame(Context context) {
        super(context);
        stage = new FrameLayout(context);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        addView(stage);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int naturalWidth = getResources().getDisplayMetrics().widthPixels;
        stage.measure(MeasureSpec.makeMeasureSpec(naturalWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = stage.getMeasuredWidth(), height = stage.getMeasuredHeight();
        stage.layout(0, 0, width, height);
        if (width == 0 || height == 0) return;
        // Leave room for the largest user-selected scale without fitting it away.
        float scale = Math.min((float) (r - l) / width, (float) (b - t) / height) / 1.35f;
        stage.setPivotX(0); stage.setPivotY(0);
        stage.setScaleX(scale); stage.setScaleY(scale);
        stage.setTranslationX(((r - l) - width * scale) / 2);
        stage.setTranslationY(((b - t) - height * scale) / 2);
    }
}
