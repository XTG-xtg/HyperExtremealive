package com.frederic22.hyperextreme.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageFitGeometryTest {
    @Test public void exactRatioIsNotCropped() {
        assertArrayEquals(new double[]{0, 0, 100, 200}, ImageFitGeometry.crop(100, 200, 300, 600), 1e-6);
    }
    @Test public void userExampleCropsSymmetricallyWithoutStretching() {
        double[] r = ImageFitGeometry.crop(1440, 3136, 631, 1471);
        assertEquals(0, r[1], 1e-6); assertEquals(3136, r[3], 1e-6);
        assertEquals(720, (r[0] + r[2]) / 2, 1e-6);
        assertEquals(631d / 1471, (r[2] - r[0]) / (r[3] - r[1]), 1e-6);
    }
    @Test public void sourceAndTargetGeometryMatrix() {
        int[][] dimensions = {{1,1}, {100,200}, {1920,1080}, {1440,3136}, {631,1471}, {4096,80}};
        for (int[] source : dimensions) for (int[] target : dimensions) {
            double[] r = ImageFitGeometry.crop(source[0], source[1], target[0], target[1]);
            assertTrue(r[0] >= -1e-6 && r[1] >= -1e-6);
            assertTrue(r[2] <= source[0] + 1e-6 && r[3] <= source[1] + 1e-6);
            assertEquals(source[0], r[0] + r[2], 1e-6);
            assertEquals(source[1], r[1] + r[3], 1e-6);
            assertEquals((double)target[0]/target[1], (r[2]-r[0])/(r[3]-r[1]), 1e-6);
        }
    }
    @Test public void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ImageFitGeometry.crop(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ImageFitGeometry.crop(100, 200, -1, 1));
    }
}
