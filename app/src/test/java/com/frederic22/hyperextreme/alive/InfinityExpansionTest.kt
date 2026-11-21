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
class InfinityExpansionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun newThemesHaveValidAssetsAndIndependentFamilyIdentity() {
        val scenes=AliveCatalog.load(context)
        for(id in 807..820) {
            val data=InfinityExpansionTheme(context.assets,id)
            val scene=scenes.single { it.id==id }
            assertEquals(7,scene.group);assertEquals(800,AlivePresets.family(id))
            assertTrue(isWallpaperLibraryScene(scene,true));assertFalse(isWallpaperLibraryScene(scene,false))
            assertFalse(isAliveDynamicScene(scene))
            assertTrue(CompanionPreparation.allowed(false,id,false,2,Display.STATE_ON,true))
            assertFalse(CompanionPreparation.allowed(false,id,false,0,Display.STATE_DOZE,false))
            val images=if(data.polygon) listOf(data.atlas+"lock.webp",data.atlas+"home.webp") else listOf(data.atlas)
            for(path in images+listOf(data.directory+"preview.jpg")) {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it,null,bounds) }
                assertTrue(path,bounds.outWidth>0&&bounds.outHeight>0)
                assertTrue(path,bounds.outWidth<=4096&&bounds.outHeight<=4096)
            }
            if(data.polygon) {
                assertTrue(data.points.isNotEmpty());assertTrue(data.faces.isNotEmpty());assertTrue(data.lines.isNotEmpty())
                assertEquals(data.lockMode+1,data.homeMode)
            } else {
                assertEquals(InfinityTheme.LAYERS[id-801],data.layers.size)
                assertTrue(data.layers.any { it.components.any { c -> c.masked } })
                assertTrue(data.layers.any { it.components.any { c -> c.role>=2 } })
            }
        }
        assertFalse(InfinityTheme.supports(800));assertFalse(InfinityTheme.supports(821))
    }
    @Test fun allNewSceneTransitionsSettleAndAodIgnoresInteraction() {
        for(id in 807..820) {
            val motion=InfinityMotion.create(id,2)
            var t=1_000_000_000L;motion.advance(t,1f)
            for(scene in listOf(0,1,2,1,0)) {
                motion.change(scene,true)
                repeat(330){t+=16_666_667L;motion.advance(t,1f)}
                assertFalse("variant $id scene $scene",motion.active())
                assertTrue(motion.values.all { it.isFinite()&&it in 0f..1f })
                if(motion is InfinityGeometryMotion) assertTrue(motion.geometry.all { it.isFinite() })
            }
            val before=motion.rotation();motion.swipe(.5f);motion.follow(.5f,0f)
            motion.gyroscope(0f,1f,t+1,false,0);motion.advance(t+2,1f)
            assertEquals(before,motion.rotation(),.0001f);assertFalse(motion.active())
        }
    }
    @Test fun geometryProfilesPreserveSourceScenesAndGestureAnimation() {
        for(id in 811..820) {
            val m=InfinityGeometryMotion(id,1)
            val star=(id-811)%2==0
            assertEquals(if(star)35f else 0f,m.geometry[InfinityGeometryMotion.ROTATE],0f)
            assertEquals(if(star)1f else 0f,m.geometry[InfinityGeometryMotion.STARS],0f)
            m.swipe(.4f);m.advance(1_000_000_000L,1f);m.advance(2_100_000_000L,1f)
            assertEquals(20f,m.rotationY(),.001f)
            m.change(2,true);m.advance(2_200_000_000L,1f);m.advance(2_900_000_000L,1f)
            assertTrue(m.pulseDots()>0)
            m.change(0,true)
            repeat(270){m.advance(3_000_000_000L+it*16_666_667L,1f)}
            assertFalse(m.active());assertEquals(1f,m.geometry[InfinityGeometryMotion.STARS],0f)
        }
    }
}
