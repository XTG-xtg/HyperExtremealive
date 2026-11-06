package com.frederic22.hyperextreme.data

import com.frederic22.hyperextreme.spatial.SpatialCrop
import org.junit.Assert.*
import org.junit.Test

class SpatialCropTest {
    @Test fun nonfiniteStoredCropIsSanitizedBeforePublishing() {
        assertEquals(SpatialCrop.DEFAULT, SpatialCrop.clamp(SpatialCrop.Crop(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN)))
        assertEquals("1.0,0.0,0.0", SpatialCrop.encodeProp(SpatialCrop.Crop(Float.NaN, Float.NaN, Float.NaN)))
    }
    @Test fun cropPropertyMatchesShaderTransform() {
        val crop = SpatialCrop.Crop(0.6f, 0.4f, 2f)
        assertArrayEquals(floatArrayOf(0.5f, 0.35f, 0.15f),
            SpatialCrop.decodeProp(SpatialCrop.encodeProp(crop)), 0.00001f)
        assertEquals(0.5f, SpatialCrop.scaleOf(crop), 0.00001f)
    }

    @Test fun geometryClampLeavesRoomForSensorMotion() {
        val crop = SpatialCrop.clampWith(SpatialCrop.Crop(-1f, 2f, 1f), 0.35f, 0.4f, 0.04f, 0.03f)
        assertEquals(0.39f, crop.cx, 0.00001f)
        assertEquals(0.57f, crop.cy, 0.00001f)
        assertEquals(1f, crop.zoom, 0f)
    }

    @Test fun invalidPropertyFallsBackInsteadOfSendingNonfiniteValuesToGpu() {
        for (raw in listOf("", "0,0", "NaN,0,0", "1,Infinity,0", "1,0,0,0"))
            assertNull(SpatialCrop.decodeProp(raw))
    }
}
