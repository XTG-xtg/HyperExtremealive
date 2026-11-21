package com.frederic22.hyperextreme.hook

import android.os.Handler
import android.os.SystemClock

/** One Binder request at a time, one latest music state, and one coalesced heal. */
internal class MusicIpcClient(
    private val worker: Handler,
    private val deliver: (String, Boolean) -> Unit,
    private val log: (String) -> Unit,
) {
    private var pendingMusic: Boolean? = null
    private var pendingHeal = false
    private var scheduled = false
    private var failures = 0
    private var blockedUntil = 0L
    private var errorAt = -30_000L
    private val drain = Runnable { drainOnce() }

    @Synchronized fun music(shown: Boolean) {
        pendingMusic = shown
        pendingHeal = false
        scheduleLocked()
    }
    @Synchronized fun heal() {
        if (pendingMusic == null) pendingHeal = true
        scheduleLocked()
    }
    private fun scheduleLocked() {
        if (scheduled || (pendingMusic == null && !pendingHeal)) return
        scheduled = true
        worker.postDelayed(drain, (blockedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0))
    }
    private fun drainOnce() {
        val shown = synchronized(this) {
            val value = pendingMusic
            pendingMusic = null; pendingHeal = false
            value
        }
        val method = if (shown == null) "heal" else "music"
        val failure = runCatching { deliver(method, shown ?: false) }.exceptionOrNull()
        synchronized(this) {
            if (failure == null) {
                failures = 0; blockedUntil = 0
            } else {
                // Never retry an old on/off event over a newer state received during Binder IPC.
                if (pendingMusic == null) {
                    if (shown != null) pendingMusic = shown else pendingHeal = true
                }
                val delay = longArrayOf(3000, 6000, 12000, 30000, 60000)[failures.coerceAtMost(4)]
                failures = (failures + 1).coerceAtMost(5)
                val now = SystemClock.elapsedRealtime()
                blockedUntil = now + delay
                if (now - errorAt >= 30000) {
                    errorAt = now
                    log("MUSIC_WATCH $method deferred: ${failure.javaClass.simpleName}: ${failure.message}")
                }
            }
            scheduled = false
            scheduleLocked()
        }
    }
}
