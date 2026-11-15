package com.frederic22.hyperextreme.hook

import android.app.Application
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MusicIpcClientTest {
    private val handler = Handler(Looper.getMainLooper())
    private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @Test fun mergesPendingStatesAndDoesNotCallBinderFromSubmitter() {
        val calls = mutableListOf<Pair<String,Boolean>>()
        val client = MusicIpcClient(handler, { m,s -> calls.add(m to s) }, {})
        repeat(1000) { client.music(true); client.heal() }
        client.music(false)
        assertTrue(calls.isEmpty())
        idle()
        assertEquals(listOf("music" to false),calls)
    }
    @Test fun failedOldStateDoesNotOverwriteLatestState() {
        val calls = mutableListOf<Boolean>()
        lateinit var client: MusicIpcClient
        client = MusicIpcClient(handler, { _,shown ->
            calls.add(shown)
            if(calls.size==1) { client.music(false); throw SecurityException("provider restarting") }
        }, {})
        client.music(true);idle()
        assertEquals(listOf(true),calls)
        idle(2999);assertEquals(1,calls.size)
        idle(1);assertEquals(listOf(true,false),calls)
    }
    @Test fun persistentFailureBacksOffAndLimitsLogs() {
        var calls=0;var logs=0
        val client=MusicIpcClient(handler,{ _,_ -> calls++;throw SecurityException("denied") },{ logs++ })
        client.heal();idle()
        repeat(30) { client.heal();idle(1000) }
        assertEquals(4,calls);assertEquals(1,logs)
        idle(21000);assertEquals(5,calls);assertEquals(2,logs)
    }
}
