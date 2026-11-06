package com.frederic22.hyperextreme.data

import org.junit.Assert.*
import org.junit.Test

class RasterFlagScriptTest {
    @Test fun activationPublishesCompleteOptionsBeforeActive() {
        val script = RasterFlag.script(true, "123_abc", 9, true, 99, false, false)
        assertTrue(script.startsWith("setprop ${RasterFlag.PROP_ACTIVE} 0 && "))
        assertTrue(script.contains("setprop ${RasterFlag.PROP_SCOPE} 2 && "))
        assertTrue(script.contains("setprop ${RasterFlag.PROP_PAN_DEG} 45 && "))
        assertTrue(script.contains("setprop ${RasterFlag.PROP_SETTLE} 0 && setprop ${RasterFlag.PROP_ACTIVE} 1 && echo"))
    }

    @Test fun stopRequiresRendererAcknowledgementInSameSuccessChain() {
        val script = RasterFlag.script(false, "", 0, false, 25, true, true)
        assertTrue(script.contains("done && [ \"\$(getprop ${RasterFlag.PROP_RENDERING})\" != 1 ] && echo"))
        assertFalse(script.contains("; echo"))
        assertFalse(script.contains("; while"))
    }

    @Test fun untrustedKeysCannotEnterShell() {
        for (key in listOf("../photo", "123'; echo BAD", "123\n", "$(id)")) {
            assertThrows(IllegalArgumentException::class.java) { RasterFlag.script(true, key, 0, false, 25, true, false) }
        }
    }
}
