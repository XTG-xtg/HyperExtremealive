package com.frederic22.hyperextreme.data;

/** Center-crop in the oriented source's pixel grid; no anisotropic stretching. */
public final class ImageFitGeometry {
    private ImageFitGeometry() { }

    public static double[] crop(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0)
            throw new IllegalArgumentException("Invalid image dimensions");
        double scale = Math.max((double) targetWidth / sourceWidth, (double) targetHeight / sourceHeight);
        double width = targetWidth / scale, height = targetHeight / scale;
        double left = (sourceWidth - width) / 2, top = (sourceHeight - height) / 2;
        return new double[]{left, top, left + width, top + height};
    }
}
