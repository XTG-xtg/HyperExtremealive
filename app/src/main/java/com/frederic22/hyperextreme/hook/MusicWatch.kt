package com.frederic22.hyperextreme.hook

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.View
import com.frederic22.hyperextreme.alive.Protocol

/**
 * Independent music-lockscreen watcher. Lives in SystemUI, not the renderer.
 *
 * HyperMusicCover needs the foreground layer; native media controls keep the wallpaper intact.
 * Only actual attached, visible views count; background music alone is not a media screen.
 */
internal object MusicWatch {
    private const val POLL_MS = 800L
    private const val HOLD_MS = 2500L
    private var started = false
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var lastLine = ""
    private var lastSeenAt = 0L
    private var lastCoverSeenAt = 0L
    private var lastMusicShown = false
    private var lastHealAt = 0L
    private var tries = 0
    private var client: MusicIpcClient? = null

    @Synchronized fun start() {
        if (started) return
        val app = runCatching {
            val at = Class.forName("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? android.app.Application
        }.getOrNull()
        if (app == null) {
            tries++
            if (tries == 1) HookRuntime.log("MUSIC_WATCH waiting for Application")
            if (tries <= 30) handler.postDelayed({ start() }, 1000L)
            else HookRuntime.log("MUSIC_WATCH gave up: no Application")
            return
        }
        started = true
        val ipcThread = HandlerThread("HyperExtremeMusicIPC").apply { start() }
        client = MusicIpcClient(Handler(ipcThread.looper), { method, shown ->
            val extras = android.os.Bundle().apply { putBoolean("shown", shown) }
            app.contentResolver.call(Protocol.CONFIG, method, null, extras)
        }, HookRuntime::log)
        val task = object : Runnable {
            override fun run() {
                runCatching { scan(app) }
                handler.postDelayed(this, POLL_MS)
            }
        }
        handler.postDelayed(task, POLL_MS)
        notifyApp("music", false)
        HookRuntime.log("MUSIC_WATCH started")
    }

    private fun scan(ctx: Context) {
        val interactive = runCatching {
            (ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive == true
        }.getOrDefault(false)
        if (!interactive) return
        val result = runCatching {
            val cl = Class.forName("android.view.WindowManagerGlobal", false, javaClass.classLoader)
            val inst = cl.getMethod("getInstance").invoke(null)
            val f = cl.getDeclaredField("mViews").apply { isAccessible = true }
            val views = f.get(inst) as? List<*>
            MusicViewScan.scan(views.orEmpty().filterIsInstance<View>())
        }.getOrElse { return } // An unavailable scan cannot claim that media UI disappeared.
        val now = SystemClock.elapsedRealtime()
        if (result.visible) lastSeenAt = now
        val effective = result.visible || (lastSeenAt != 0L && now - lastSeenAt < HOLD_MS)
        if (result.foregroundCover) lastCoverSeenAt = now
        val foregroundCover = result.foregroundCover || (lastCoverSeenAt != 0L && now - lastCoverSeenAt < HOLD_MS)
        val line = result.names.joinToString(" | ")
        if (line != lastLine) {
            lastLine = line
            HookRuntime.log("MUSIC_WATCH scan: " + if (line.isBlank()) "(none)" else line)
        }
        if (foregroundCover != Coexist.mcMusicScreen) {
            Coexist.mcMusicScreen = foregroundCover
            Coexist.onChanged?.invoke()
        }
        if (effective != lastMusicShown) {
            lastMusicShown = effective
            HookRuntime.log("MUSIC_WATCH music screen " + if (effective) "on" else "off")
            notifyApp("music", effective)
        } else if (!effective && now - lastHealAt >= 3000L) {
            lastHealAt = now
            notifyApp("heal", false)
        }
    }

    private fun notifyApp(method: String, shown: Boolean) {
        if (method == "music") client?.music(shown) else client?.heal()
    }
}

internal object MusicScreenNames {
    fun ownsForeground(className: String): Boolean {
        val n = className.lowercase(java.util.Locale.ROOT)
        return n.contains("hypermusiccover") || n.contains("lyric")
    }
    fun matches(className: String): Boolean {
        val n = className.lowercase(java.util.Locale.ROOT)
        return n.contains("music") ||
            n.contains("lyric") ||
            n.contains("hypermusiccover") ||
            n.endsWith("miuimediaheaderview") ||
            n.contains("keyguardmedia") ||
            n.contains("mediapanel") ||
            n.contains("mediacontrol") ||
            n.contains("medianotification")
    }
}
