package com.frederic22.hyperextreme.alive.core;

/** Center-crop UVs must stay inside the image; values above one smear clamped edges. */
public final class PhotoGeometry {
    private PhotoGeometry() { }

    public static float cropX(float viewportAspect, float photoAspect) {
        return Math.min(1f, viewportAspect / photoAspect);
    }

    public static float cropY(float viewportAspect, float photoAspect) {
        return Math.min(1f, photoAspect / viewportAspect);
    }
}
