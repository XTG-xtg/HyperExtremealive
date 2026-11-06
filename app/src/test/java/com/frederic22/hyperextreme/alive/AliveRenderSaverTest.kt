package com.frederic22.hyperextreme.alive

import android.app.Application
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35], application=Application::class)
class AliveRenderSaverTest {
    @Test fun recreationKeepsOffsetsAndManualMode() {
        val options=AliveRenderOptions(true, false, true, true, true, false, 17, 2f).position(.3f, -.4f)
        val scope=object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved=with(RenderSaver) { scope.save(options) }
        val restored=RenderSaver.restore(checkNotNull(saved))!!
        assertEquals(.3f, restored.aodOffsetX, 0f)
        assertEquals(-.4f, restored.aodOffsetY, 0f)
        assertFalse(restored.followClock)
        assertTrue(restored.burnIn)
        assertEquals(2f, restored.brightness, 0f)
    }
}
