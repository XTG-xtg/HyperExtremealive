package com.frederic22.hyperextreme.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SpatialFlagScriptTest {
    @Test fun cropAndRestartRemainInTheSerializedChain() {
        val script = SpatialFlag.script(true, "0123456789abcdef", 0, false, false, "0.5,0.25,0.25", true)
        assertTrue(script.startsWith("setprop ${SpatialFlag.PROP_ACTIVE} 0 && sleep 1.3 && "))
        assertTrue(script.contains("setprop ${SpatialFlag.PROP_CROP} '0.5,0.25,0.25' && setprop ${SpatialFlag.PROP_ACTIVE} 1"))
    }

    @Test fun malformedCropCannotEnterShell() {
        for (crop in listOf("NaN,0,0", "Infinity,0,0", "1,0", "1,0,0'; id", "1,0,0,0")) {
            assertThrows(IllegalArgumentException::class.java) {
                SpatialFlag.script(true, "0123456789abcdef", 0, false, false, crop)
            }
        }
    }

    @Test fun enableReachesEchoOnlyThroughTheSameChain() {
        val script = SpatialFlag.script(true, "0123456789abcdef", 2, true, false)
        assertTrue(script.startsWith("setprop debug.hyperextremealive.spatial_active 0 && "))
        assertTrue(script.contains("setprop debug.hyperextremealive.spatial_key '0123456789abcdef' && "))
        assertTrue(script.contains("setprop debug.hyperextremealive.spatial_scope 2 && "))
        assertTrue(script.contains("setprop debug.hyperextremealive.spatial_lockdepth 1 && "))
        assertTrue(script.contains("setprop debug.hyperextremealive.spatial_active 1 && echo "))
        assertTrue(script.endsWith("echo SPATIAL_FLAG:" + marker("spatial_active") + ":" + marker("spatial_key") + ":" + marker("spatial_scope") + ":" + marker("spatial_lockdepth")))
        assertFalse(script.contains("while"))
        assertFalse(script.contains("; echo"))
    }

    @Test fun disableWaitsForRendererWithoutLeavingTheChain() {
        val script = SpatialFlag.script(false, "", 0, false, true)
        assertTrue(script.contains("setprop debug.hyperextremealive.spatial_key '' && "))
        assertTrue(script.contains("setprop debug.hyperextremealive.spatial_active 0 && i=0 && while"))
        assertTrue(script.contains(dollar() + "i"))
        assertTrue(script.contains("i=" + dollar() + "((i+1))"))
        assertTrue(script.contains("done && [ \"" + dollar() + "(getprop debug.hyperextremealive.spatial_rendering)\" != 1 ] && echo"))
        assertFalse(script.contains("; while"))
        assertFalse(script.contains("; echo"))
    }

    private fun marker(name: String) = dollar() + "(getprop debug.hyperextremealive." + name + ")"
    private fun dollar(): String = "\u0024"
}
