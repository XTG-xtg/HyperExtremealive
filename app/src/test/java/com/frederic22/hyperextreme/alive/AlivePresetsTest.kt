package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlivePresetsTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun snapshot(variant: Int=1,vivo: VivoOptions?=null) = AlivePresets.snapshot(app,"测试预设",variant,true,false,
        PhotoOptions.defaults().edit(0,2f).edit(1,.7f),AliveRenderOptions.load(app).position(.1f,.2f),true,vivo)
    private fun write(value: AlivePreset) {
        val image=Bitmap.createBitmap(24,50,Bitmap.Config.ARGB_8888).apply { eraseColor(-1) }
        try { AlivePresets.write(app,value,image) } finally { image.recycle() }
    }
    @Test fun roundTripAllSettingsAndStableIdentity() {
        val value=snapshot(601)
        val parsed=AlivePreset.parse(value.json())
        assertEquals(value.signature(),parsed.signature());assertEquals(value.photo.lockStyle,parsed.photo.lockStyle)
        assertEquals(value.photo.x,parsed.photo.x,0f);assertEquals(.1f,parsed.render.aodOffsetX,0f)
        assertEquals(value.signature(),snapshot(601).signature())
        assertEquals(value.signature(),value.copy(created=1,name="重命名").signature())
        assertNotEquals(value.signature(),value.copy(photo=value.photo.edit(0,1f)).signature())
    }
    @Test fun flashPresetsKeepPhotoMaskPatternCropAndAllSettings() {
        val mask=File(app.filesDir,"vivo-mask-a.png").apply { writeBytes(byteArrayOf(1)) }
        val photo=File(app.filesDir,"vivo-photo-a.png").apply { writeBytes(byteArrayOf(2)) }
        val options=VivoOptions(3).apply { style=3;pattern="d5_pattern_star";area=1;paint=mask.name;subject=mask.name;photos.add(photo.name);zoom=2.1f;x=.65f;sensitivity=30 }
        val value=snapshot(703,options);write(value)
        val read=AlivePresets.list(app).single { it.id==value.id }
        assertEquals(options.json(),read.vivoJson)
        options.style=5;options.paint=""
        assertEquals(3,VivoOptions.parse(read.vivoJson).style)
        assertEquals(mask.name,VivoOptions.parse(read.vivoJson).paint)
    }
    @Test fun savingAndDeletingDoNotPublishOrRemoveSharedSources() {
        val before=HashMap(com.frederic22.hyperextreme.alive.Config.prefs(app).all)
        val value=snapshot(601);val source=PhotoMedia.resolve(app,value.photo,false,0);val bytes=source.readBytes()
        write(value);write(value)
        assertEquals(1,AlivePresets.list(app).count { it.id==value.id })
        AlivePresets.delete(app,value.id)
        assertFalse(AlivePresets.cover(app,value.id).exists());assertArrayEquals(bytes,source.readBytes())
        assertEquals(before,com.frederic22.hyperextreme.alive.Config.prefs(app).all)
    }
    @Test fun eachFamilyRetainsItsOwnLastAppliedSnapshot() {
        val alive=snapshot();val flash=snapshot(703,VivoOptions(3).apply { style=5 })
        AlivePresets.markCurrent(app,alive);AlivePresets.markCurrent(app,flash)
        com.frederic22.hyperextreme.alive.Config.prefs(app).edit().putInt("variant",701).commit()
        assertEquals(alive.signature(),AlivePresets.current(app).signature())
        assertEquals(flash.signature(),AlivePresets.current(app,703).signature())
    }
    @Test fun changedFlashConfigurationAddsHistoryInsteadOfOverwriting() {
        val first=snapshot(703,VivoOptions(3).apply { style=2 });val second=snapshot(703,VivoOptions(3).apply { style=3 })
        write(first);write(second)
        assertNotEquals(first.id,second.id)
        assertTrue(AlivePresets.list(app).map { it.id }.containsAll(listOf(first.id,second.id)))
        assertTrue(AlivePresets.cover(app,first.id).isFile);assertTrue(AlivePresets.cover(app,second.id).isFile)
    }
    @Test fun invalidPathsAndCrossFamilyPayloadsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AlivePresets.cover(app,"../photo") }
        val value=snapshot(703,VivoOptions(3))
        val invalid=org.json.JSONObject(value.json()).put("vivo",VivoOptions(1).json()).toString()
        assertThrows(IllegalArgumentException::class.java) { AlivePreset.parse(invalid) }
    }
    @Test fun damagedRecordsDoNotHideGoodHistoryOrCrossFamilyCurrent() {
        val good = snapshot(703,VivoOptions(3).apply { style=3 })
        write(good)
        val dir = AlivePresets.cover(app,good.id).parentFile!!
        File(dir,"${"a".repeat(64)}.json").writeText(good.json())
        File(dir,"${"b".repeat(64)}.json").writeText("x".repeat(40_000))
        File(dir,"last-701.json").writeText(good.json())
        com.frederic22.hyperextreme.alive.Config.prefs(app).edit().putInt("variant",1).commit()
        assertEquals(listOf(good.id),AlivePresets.list(app).map { it.id })
        assertEquals(701,AlivePresets.current(app,701).variant)
        assertThrows(IllegalArgumentException::class.java) { AlivePresets.current(app,606) }
    }
}
