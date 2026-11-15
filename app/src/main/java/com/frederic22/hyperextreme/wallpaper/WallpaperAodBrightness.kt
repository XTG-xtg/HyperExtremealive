package com.frederic22.hyperextreme.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import com.frederic22.hyperextreme.alive.AodBrightness

/** One bounded exposure transition; no timers or sensor work after the scene settles. */
internal class WallpaperAodBrightness(
    private val context: Context, private val handler: Handler, private val redraw: () -> Unit
) : AutoCloseable, DisplayManager.DisplayListener {
    private val power=context.getSystemService(PowerManager::class.java)
    private val displays=context.getSystemService(DisplayManager::class.java)
    private var shown=false
    @Volatile private var closed=false
    private var target=if(asleep()) 1f else 0f
    private var from=target
    private var began=SystemClock.uptimeMillis()
    private val watch=AodBrightness.Watch(context,handler) { request() }
    private fun asleep(): Boolean {
        // DOZE can lag SCREEN_ON. A stale display notification must not restore the boost.
        power?.let { return !it.isInteractive }
        val state=displays?.getDisplay(Display.DEFAULT_DISPLAY)?.state
        return state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND
    }
    private fun weight(): Float {
        val t=((SystemClock.uptimeMillis()-began)/700f).coerceIn(0f,1f)
        return from+(target-from)*t*t*(3-2*t)
    }
    val exposure: Float get()=AodBrightness.frameGain(watch.value,weight(),target==1f)
    private val frame=object:Runnable {
        override fun run() {
            if(closed || !shown || displays?.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_OFF) return
            redraw()
            if(from!=target && SystemClock.uptimeMillis()-began<700) handler.postDelayed(this,16)
        }
    }
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(c:Context?,intent:Intent?) {
            ambient(intent?.action == Intent.ACTION_SCREEN_OFF)
        }
    }
    init {
        context.registerReceiver(receiver,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON)
        },null,handler,Context.RECEIVER_NOT_EXPORTED)
        displays?.registerDisplayListener(this,handler)
    }
    private fun request(){handler.removeCallbacks(frame);if(!closed && shown)handler.post(frame)}
    fun visible(value:Boolean){handler.post {
        if(closed)return@post
        shown=value
        if(value){watch.refresh();update(asleep())}
        request()
    }}
    fun ambient(value:Boolean){handler.post { if(!closed)update(value) }}
    private fun update(sleeping:Boolean) {
        val next=if(sleeping) 1f else 0f
        // DOZE/SUSPEND and refresh-rate notifications must not keep a settled wallpaper awake.
        if(next==target)return
        from=weight();target=next;began=SystemClock.uptimeMillis()
        request()
    }
    override fun onDisplayChanged(id:Int){if(id==Display.DEFAULT_DISPLAY && !closed)update(asleep())}
    override fun onDisplayAdded(id:Int){}
    override fun onDisplayRemoved(id:Int){}
    override fun close(){
        closed=true;handler.removeCallbacks(frame)
        context.unregisterReceiver(receiver);displays?.unregisterDisplayListener(this);watch.close()
    }
}
