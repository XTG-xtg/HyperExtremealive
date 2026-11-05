package com.frederic22.hyperextreme.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialFlagScriptTest {
    @Test fun enableReachesEchoOnlyThroughTheSameChain() {
        val script = SpatialFlag.script(true, "0123456789abcdef", 2, true, false)
        assertTrue(script.startsWith("setprop debug.hyperextreme.spatial_active 0 && "))
        assertTrue(script.contains("setprop debug.hyperextreme.spatial_key '0123456789abcdef' && "))
        assertTrue(script.contains("setprop debug.hyperextreme.spatial_scope 2 && "))
        assertTrue(script.contains("setprop debug.hyperextreme.spatial_lockdepth 1 && "))
        assertTrue(script.contains("setprop debug.hyperextreme.spatial_active 1 && echo "))
        assertTrue(script.endsWith("echo SPATIAL_FLAG:" + marker("spatial_active") + ":" + marker("spatial_key") + ":" + marker("spatial_scope") + ":" + marker("spatial_lockdepth")))
        assertFalse(script.contains("while"))
        assertFalse(script.contains("; echo"))
    }

    @Test fun disableWaitsForRendererWithoutLeavingTheChain() {
        val script = SpatialFlag.script(false, "", 0, false, true)
        assertTrue(script.contains("setprop debug.hyperextreme.spatial_key '' && "))
        assertTrue(script.contains("setprop debug.hyperextreme.spatial_active 0 && i=0 && while"))
        assertTrue(script.contains(dollar() + "i"))
        assertTrue(script.contains("i=" + dollar() + "((i+1))"))
        assertTrue(script.contains("done && [ \"" + dollar() + "(getprop debug.hyperextreme.spatial_rendering)\" != 1 ] && echo"))
        assertFalse(script.contains("; while"))
        assertFalse(script.contains("; echo"))
    }

    private fun marker(name: String) = dollar() + "(getprop debug.hyperextreme." + name + ")"
    private fun dollar(): String = "\u0024"
}
