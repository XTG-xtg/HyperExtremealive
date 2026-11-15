package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.frederic22.hyperextreme.aicr.AicrVision
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VivoSubjectRecognitionTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val full get() = RectF(0f, 0f, 1f, 1f)
    private fun mask(w: Int = 20, h: Int = 40) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLACK)
        for (y in h / 4 until h * 3 / 4) for (x in w / 4 until w * 3 / 4) setPixel(x, y, Color.WHITE)
    }
    private fun failed() = AicrVision.Result(false, -1, null, null, 8, "AICR 不可用")

    @Test fun systemRoutePreservesOriginalFileAndRecyclesServiceBuffers() {
        val photo = mask(); val returned = mask(); val rgba = mask()
        val file = File(app.filesDir, "vivo-original.png")
        val task = VivoSubjectRecognition(system = { _, image, original ->
            assertSame(photo, image); assertEquals(file, original)
            AicrVision.Result(true, 0, rgba, returned, 10, "系统成功")
        }, local = { _, _, _ -> error("Must not fall back after system success") })
        try {
            val result = task.generate(app, photo, full, 0, file)
            assertEquals("系统 AI · AICR", result.source)
            assertEquals(Color.WHITE, result.mask.getPixel(10, 20))
            assertEquals(Color.BLACK, result.mask.getPixel(0, 0))
            assertTrue(returned.isRecycled); assertTrue(rgba.isRecycled); assertFalse(photo.isRecycled)
            result.mask.recycle()
        } finally { photo.recycle() }
    }
    @Test fun automaticFallbackReportsWhyItUsedLocalModel() {
        val photo = mask()
        try {
            val task = VivoSubjectRecognition(system = { _, _, _ -> failed() }, local = { _, _, _ -> mask() })
            val result = task.generate(app, photo, full, 0)
            assertEquals("本地 AI · U²-Net", result.source)
            assertTrue(result.detail.contains("AICR 不可用")); result.mask.recycle()
        } finally { photo.recycle() }
    }
    @Test fun strictSystemAndLocalOnlyHonorUserChoice() {
        val photo = mask()
        try {
            assertThrows(IllegalStateException::class.java) {
                VivoSubjectRecognition(system = { _, _, _ -> failed() }, local = { _, _, _ -> error("No fallback") })
                    .generate(app, photo, full, 1)
            }
            val local = VivoSubjectRecognition(system = { _, _, _ -> error("No system call") }, local = { _, _, _ -> mask() })
                .generate(app, photo, full, 2)
            assertEquals("本机离线识别", local.detail); local.mask.recycle()
        } finally { photo.recycle() }
    }
    @Test fun selectionIsPlacedBackInSourceCoordinates() {
        val photo = mask(40, 80)
        try {
            val result = VivoSubjectRecognition(system = { _, input, file ->
                assertNull(file); assertEquals(20, input.width); assertEquals(40, input.height)
                AicrVision.Result(true, 0, null, mask(), 0, "框选")
            }).generate(app, photo, RectF(.25f, .25f, .75f, .75f), 1, File("not-used"))
            assertEquals(40, result.mask.width); assertEquals(80, result.mask.height)
            assertEquals(Color.WHITE, result.mask.getPixel(20, 40))
            assertEquals(Color.BLACK, result.mask.getPixel(5, 5)); result.mask.recycle()
        } finally { photo.recycle() }
    }
    @Test fun cancellationDiscardsSystemResultAndNeverFallsBack() {
        val photo = mask(); val returned = mask()
        lateinit var task: VivoSubjectRecognition
        task = VivoSubjectRecognition(system = { _, _, _ ->
            task.cancel(); AicrVision.Result(true, 0, null, returned, 0, "late result")
        }, local = { _, _, _ -> error("Cancelled") })
        try {
            assertThrows(CancellationException::class.java) { task.generate(app, photo, full, 0) }
            assertTrue(returned.isRecycled)
        } finally { photo.recycle() }
    }
    @Test fun invalidSystemMaskCannotBecomeStretchedOrEmptySubject() {
        val photo = mask()
        try {
            for (invalid in listOf(mask(10, 10), Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })) {
                val result = VivoSubjectRecognition(system = { _, _, _ -> AicrVision.Result(true, 0, null, invalid, 0, "invalid") },
                    local = { _, _, _ -> mask() }).generate(app, photo, full, 0)
                assertEquals("本地 AI · U²-Net", result.source); assertTrue(invalid.isRecycled); result.mask.recycle()
            }
        } finally { photo.recycle() }
    }
    @Test fun importedAlphaUsesCoverageNotPhotoLuminance() {
        val rgba = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT); setPixel(2, 4, Color.BLACK)
        }
        try {
            val normalized = VivoMaskGeometry.normalize(rgba, 4, 8)
            assertEquals(Color.WHITE, normalized.getPixel(2, 4)); assertEquals(Color.BLACK, normalized.getPixel(0, 0))
            normalized.recycle()
            assertThrows(IOException::class.java) { VivoMaskGeometry.normalize(rgba, 8, 8) }
        } finally { rgba.recycle() }
    }
    @Test fun recognitionPreferencesAndCropDoNotRebuildPreview() {
        val original = VivoOptions(1)
        val next = original.edited { subjectEngine = 2; autoSubject = false; subjectSource = "本地"; x = .7f; zoom = 2f }
        assertEquals(previewVivoSceneKey(original), previewVivoSceneKey(next))
        assertNotEquals(previewVivoSceneKey(original), previewVivoSceneKey(next.edited { subject = "vivo-mask.png" }))
        next.saveDraft(app)
        assertEquals(next.json(), VivoOptions.load(app, 701, true).json())
        assertEquals(0, VivoOptions.load(app, 703, true).subjectEngine)
        assertEquals(2, VivoMediaSelection.defaults(next).subjectEngine)
        assertFalse(VivoMediaSelection.defaults(next).autoSubject)
    }
    @Test fun independentFamiliesAreNeverClassifiedAsDynamicAlive() {
        val scenes = AliveCatalog.load(app)
        assertTrue(scenes.filter(::isAliveDynamicScene).isNotEmpty())
        assertTrue(scenes.filter(::isAliveDynamicScene).none { it.id in 601..703 })
        for (id in 701..703) assertFalse(isAliveDynamicScene(scenes.single { it.id == id }))
        Config.prefs(app).edit().putInt("variant", 701).putInt("alive_last_variant", 603).commit()
        assertEquals(603, aliveHomeVariant(app)); assertEquals(701, Config.variant(app))
        assertThrows(IllegalStateException::class.java) { AliveSettings.disableVariant(app, 703) }
        assertEquals(701, Config.variant(app))
    }
}
