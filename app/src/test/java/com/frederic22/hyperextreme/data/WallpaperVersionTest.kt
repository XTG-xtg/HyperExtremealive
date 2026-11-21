package com.frederic22.hyperextreme.data

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class WallpaperVersionTest {
    private val context = RuntimeEnvironment.getApplication()
    private fun template(path: String, hash: String = "a".repeat(40), video: String = ""): String =
        JSONObject().put("lockscreenInfo",JSONObject().put("wallpaperInfo",JSONObject()
            .put("source",path).put("imagePath",path).put("sha1",hash).put("videoName",video))).toString()
    private fun fp(json: String) = WallpaperVersion.computeFrom(context,json).value
    @Test fun recopyingSameArtworkKeepsContentIdentity() {
        assertEquals(fp(template("/gallery/a.jpg")),fp(template("/templates/current/wallpaper", "A".repeat(40))))
    }
    @Test fun cropSubjectAndClockEditsDoNotChangeWallpaperIdentity() {
        for(hash in listOf("", "b".repeat(40))) {
            val original=template("/wallpaper",hash)
            val edited=JSONObject(original)
            val lock=edited.getJSONObject("lockscreenInfo")
            lock.put("clockInfo",JSONObject().put("style",7))
            lock.getJSONObject("wallpaperInfo").put("cropSource","/newcrop").put("cropSubject","/newsubject")
                .put("positionInfo",JSONObject().put("left",123))
            assertEquals(fp(original),fp(edited.toString()))
        }
    }
    @Test fun realContentAndVideoChangesRemainDetectable() {
        assertNotEquals(fp(template("/same-path","a".repeat(40))),fp(template("/same-path","b".repeat(40))))
        assertNotEquals(fp(template("/same-path",video="one.mp4")),fp(template("/same-path",video="two.mp4")))
        assertNotEquals(fp(template("/a","")),fp(template("/b","")))
    }
    @Test fun legacyAndHashAvailabilityChangesMigrateInsteadOfDisabling() {
        assertNotEquals(WallpaperVersion.kindOf("tpl:123"),WallpaperVersion.kindOf(fp(template("/a"))))
        assertNotEquals(WallpaperVersion.kindOf("files:123"),WallpaperVersion.kindOf("files2:original:123"))
        assertNotEquals(WallpaperVersion.kindOf(fp(template("/a",""))),WallpaperVersion.kindOf(fp(template("/a"))))
    }
    @Test fun fileFallbackIgnoresCropsAndDuplicateOriginals() {
        val dir=File(context.filesDir,"templates/current").apply { mkdirs() }
        val original=File(dir,"wallpaper").apply { writeBytes(ByteArray(60000) { 3 }) }
        val before=WallpaperVersion.computeFrom(context,"{}").value
        assertTrue(before.startsWith(WallpaperVersion.PREFIX_FILES))
        File(dir,"wallpaper_cropped").writeBytes(ByteArray(70000) { 7 })
        File(dir,"wallpaper_home").writeBytes(ByteArray(80000) { 8 })
        File(dir,"wallpaper_copied").writeBytes(original.readBytes())
        assertEquals(before,WallpaperVersion.computeFrom(context,"{}").value)
        original.delete()
        assertEquals(before,WallpaperVersion.computeFrom(context,"{}").value)
        File(dir,"wallpaper_copied").writeBytes(ByteArray(60000) { 5 })
        assertNotEquals(before,WallpaperVersion.computeFrom(context,"{}").value)
    }
}
