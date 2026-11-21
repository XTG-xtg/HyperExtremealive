package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.BitmapFactory
import android.view.Display
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InfinityIntegrationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun sixThemesLoadWithCompleteMeshAndTextureData() {
        val scenes = AliveCatalog.load(context)
        assertEquals(20, scenes.count { it.group == 7 })
        assertEquals(scenes.size, scenes.map { it.id }.toSet().size)
        for(id in 801..806) {
            val scene = scenes.single { it.id == id }
            assertFalse(isAliveDynamicScene(scene))
            assertFalse(scene.group in aliveDynamicGroups)
            assertTrue(isWallpaperLibraryScene(scene, true))
            assertFalse(isWallpaperLibraryScene(scene, false))
            val theme = InfinityTheme(context.assets, id)
            assertEquals(InfinityTheme.LAYERS[id-801], theme.vertices.size)
            for(file in listOf("lock-background.png", "lock-effect.png", "preview.jpg")) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(theme.directory + file).use { BitmapFactory.decodeStream(it, null, bounds) }
                assertTrue(file, bounds.outWidth > 0 && bounds.outHeight > 0)
            }
            assertTrue(CompanionPreparation.allowed(false,id,false,2,Display.STATE_ON,true))
            assertFalse(CompanionPreparation.allowed(false,id,false,0,Display.STATE_DOZE,false))
        }
    }
    @Test fun infinityPresetsSurviveSerializationAndRestoreAfterVivo() {
        for(id in 801..820) {
            val original = AlivePreset("", InfinityTheme.NAMES[id-801], id, false, false,
                PhotoOptions.defaults(), AliveRenderOptions.load(context))
            val restored = AlivePreset.parse(original.json())
            assertEquals(id,restored.variant);assertEquals(original.signature(),restored.signature())
            com.frederic22.hyperextreme.alive.Config.prefs(context).edit().putInt("variant",702).putInt("alive_last_variant",id).commit()
            assertEquals(1,aliveHomeVariant(context))
            assertEquals(id,aliveHomeVariant(context, true))
        }
    }

    @Test fun aliveInfinityAndVivoKeepSeparateAppliedSnapshots() {
        val alive = AlivePreset("", "启航", 1, false, false, PhotoOptions.defaults(), AliveRenderOptions.load(context))
        val infinity = alive.copy(name="蓝色",variant=803,dark=true,render=alive.render.position(.1f,.2f))
        AlivePresets.markCurrent(context,alive)
        AlivePresets.markCurrent(context,infinity)
        com.frederic22.hyperextreme.alive.Config.prefs(context).edit().putInt("variant",702).commit()
        assertEquals(alive.signature(),AlivePresets.current(context).signature())
        assertEquals(infinity.signature(),AlivePresets.current(context,800).signature())
        assertEquals(0,AlivePresets.family(1))
        assertEquals(800,AlivePresets.family(806))
        assertEquals(702,AlivePresets.family(702))
    }

    @Test fun ui19InfinitySnapshotMigratesWithoutBecomingAliveCurrent() {
        val original = AlivePreset("", "金色", 802, true, false, PhotoOptions.defaults(), AliveRenderOptions.load(context))
        val directory = java.io.File(context.filesDir,"alive/presets").apply { mkdirs() }
        java.io.File(directory,"last-alive.json").writeText(original.json())
        com.frederic22.hyperextreme.alive.Config.prefs(context).edit().putInt("variant",703).putInt("alive_last_variant",301).commit()
        assertEquals(original.signature(),AlivePresets.current(context,800).signature())
        assertEquals(301,AlivePresets.current(context).variant)
    }

    @Test fun familyToggleCannotChangeAnotherFamily() {
        val prefs=com.frederic22.hyperextreme.alive.Config.prefs(context)
        for(active in listOf(1,702,803)) {
            prefs.edit().putInt("variant",active).putBoolean("enabled",true).commit()
            val before=HashMap(prefs.all)
            assertThrows(IllegalStateException::class.java) {
                AliveSettings.setFamilyEnabled(context,false,active!=803)
            }
            assertEquals(before,prefs.all)
        }
    }
}
