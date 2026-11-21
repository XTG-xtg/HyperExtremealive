package com.frederic22.hyperextreme.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicScreenNamesTest {
    @Test fun matchesMusicLyricCover() {
        assertTrue(MusicScreenNames.matches("com.github.zyl6932.hypermusiccover.MusicBgView"))
        assertTrue(MusicScreenNames.matches("com.miui.keyguard.lyric.LyricView"))
        assertTrue(MusicScreenNames.matches("com.android.systemui.media.KeyguardMediaPanel"))
        assertTrue(MusicScreenNames.matches("com.android.systemui.media.MediaControlPanel"))
        assertTrue(MusicScreenNames.matches("com.android.systemui.statusbar.notification.row.MiuiMediaHeaderView"))
    }

    @Test fun ignoresUnrelatedViews() {
        assertFalse(MusicScreenNames.matches("com.android.keyguard.KeyguardClockContainer"))
        assertFalse(MusicScreenNames.matches("android.widget.FrameLayout"))
        assertFalse(MusicScreenNames.matches("com.miui.aod.AODView"))
        assertFalse(MusicScreenNames.matches("com.android.systemui.CoverView"))
        assertFalse(MusicScreenNames.matches("com.android.systemui.CoverScreenClock"))
    }

    @Test fun nativeControlsDoNotTakeOverTheDepthForeground() {
        assertFalse(MusicScreenNames.ownsForeground("com.android.systemui.MiuiMediaHeaderView"))
        assertFalse(MusicScreenNames.ownsForeground("com.android.systemui.media.KeyguardMediaPanel"))
        assertTrue(MusicScreenNames.ownsForeground("com.github.zyl6932.hypermusiccover.MusicBgView"))
        assertTrue(MusicScreenNames.ownsForeground("com.miui.keyguard.lyric.LyricView"))
    }
}
