package com.frederic22.hyperextreme.hook

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MusicViewScanTest {
    class MusicPanel(context: android.content.Context): View(context)
    @Test fun respectsHiddenAncestorsAndAttachment() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val activity=controller.get()
        val parent=FrameLayout(activity);val music=MusicPanel(activity)
        parent.addView(music);activity.setContentView(parent)
        controller.visible()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val root=activity.window.decorView
        // Simulate the window-manager visibility acknowledgement in Robolectric.
        val attachInfo=org.robolectric.util.ReflectionHelpers.getField<Any>(root,"mAttachInfo")
        org.robolectric.util.ReflectionHelpers.setField(attachInfo,"mWindowVisibility",View.VISIBLE)
        assertTrue("attached=${root.isAttachedToWindow},window=${root.windowVisibility},shown=${root.isShown},musicAttached=${music.isAttachedToWindow}",MusicViewScan.scan(listOf(root)).visible)
        parent.visibility=View.GONE
        assertFalse(MusicViewScan.scan(listOf(root)).visible)
        parent.visibility=View.VISIBLE;parent.alpha=0f
        assertFalse(MusicViewScan.scan(listOf(root)).visible)
        parent.alpha=1f;parent.removeView(music)
        assertFalse(MusicViewScan.scan(listOf(music)).visible)
        parent.addView(music)
        org.robolectric.util.ReflectionHelpers.setField(attachInfo,"mWindowVisibility",View.GONE)
        assertFalse(MusicViewScan.scan(listOf(root)).visible)
        controller.pause().stop().destroy()
    }
}
