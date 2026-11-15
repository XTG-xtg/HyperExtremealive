package com.frederic22.hyperextreme.alive

import android.app.Application
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35], application=Application::class)
class AodBrightnessTest {
    @Test fun boundedValuesSurvivePreferencesPresetAndRecreation() {
        val context=RuntimeEnvironment.getApplication()
        val saverScope=object:SaverScope {override fun canBeSaved(value:Any)=true}
        for(old in listOf(0f,.25f,2.5f,5f,100f,1e20f,Float.MAX_VALUE)) {
            val value=old.coerceIn(0f,5f)
            // Exercise migration of an already-stored ui31 preference too.
            Config.prefs(context).edit().putFloat(AodBrightness.KEY,old).commit()
            assertEquals(value,AodBrightness.load(context),0f)
            val options=AliveRenderOptions.load(context)
            assertEquals(value,options.brightness,0f)
            val saved=with(RenderSaver){saverScope.save(options)}
            assertEquals(value,RenderSaver.restore(checkNotNull(saved))!!.brightness,0f)
            for(id in listOf(1,201,301,401,501,601,701,702,703,801,810,820)) {
                val vivo=if(id in 701..703)VivoOptions(id-700).json() else ""
                val preset=AlivePreset("","brightness",id,false,false,PhotoOptions.defaults(),options,vivoJson=vivo)
                assertEquals(value,AlivePreset.parse(preset.json()).render.brightness,0f)
            }
        }
    }
    @Test fun invalidNumbersNeverReachShaders() {
        for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY))assertEquals(0f,AodBrightness.normalize(value),0f)
        assertEquals(0f,AodBrightness.normalize(-10f),0f)
        assertEquals(5f,AodBrightness.normalize(1000f),0f)
    }
    @Test fun exposureDoesNotTouchAwakeFramesAndStaysFiniteThroughTransitions() {
        for(value in listOf(0f,.25f,1f,5f,100f,Float.MAX_VALUE)) {
            assertEquals(1f,AodBrightness.gain(value,0f),0f)
            assertEquals(if(value==0f)1f else value.coerceAtMost(5f),AodBrightness.gain(value,1f),0f)
            for(step in 0..100)assertTrue(AodBrightness.gain(value,step/100f).isFinite())
        }
        assertEquals(0f,AodBrightness.dimWeight(1f,.5f),0f)
        assertEquals(.5f,AodBrightness.dimWeight(.75f,.5f),0f)
        assertEquals(1f,AodBrightness.dimWeight(.5f,.5f),0f)
    }
    @Test fun zeroBypassesExposureAndTheWallpaperMaskDuringEveryFrame() {
        for(step in 0..100) {
            val weight=step/100f
            assertEquals(1f,AodBrightness.gain(0f,weight),0f)
            assertEquals(weight,AodBrightness.wallpaperBlack(weight,0f),0f)
        }
    }
    @Test fun holdKeepsEntryBrightnessUntilWakeHideOrZeroIsSelected() {
        val hold=AodBrightness.Hold()
        for(level in listOf(.25f,1f,2.5f,5f)) {
            hold.clear()
            assertEquals(.5f,hold.black(.5f,level),0f)
            repeat(60) {
                for(systemMask in listOf(.3f,.8f,.43f,.6f)) assertEquals(.5f,hold.black(systemMask,level),0f)
                assertEquals(level,AodBrightness.gain(level,1f),0f)
            }
            assertEquals(1f,hold.black(1f,level),0f)
            assertEquals(.3f,hold.black(.3f,level),0f)
        }
        for(systemMask in listOf(.8f,.5f,.3f)) assertEquals(systemMask,hold.black(systemMask,0f),0f)
    }
}
