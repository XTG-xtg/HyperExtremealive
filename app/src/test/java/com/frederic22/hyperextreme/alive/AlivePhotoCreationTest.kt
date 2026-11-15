package com.frederic22.hyperextreme.alive

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlivePhotoCreationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun image(): Uri {
        val file = File(context.cacheDir, "new-photo-input.png")
        val bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return Uri.fromFile(file)
    }
    // Desktop native graphics cannot decode Android file descriptors; test the import boundary explicitly.
    private fun importFixture(context: Context, uri: Uri): String {
        val name = "photo-00000000000000000000000000000013.png"
        val target = File(context.filesDir, "alive/media/$name")
        target.parentFile!!.mkdirs()
        File(requireNotNull(uri.path)).copyTo(target, overwrite = true)
        return name
    }

    @Test fun cancelDoesNotCreateMediaOrChangePreferences() {
        val before = Config.prefs(context).all.toMap()
        val media = File(context.filesDir, "alive/media")
        val files = media.list().orEmpty().toSet()
        assertNull(newAlivePhotoPreview(context, null, 602) { _, _ -> error("Cancelled picker must not import") })
        assertEquals(files, media.list().orEmpty().toSet())
        assertEquals(before, Config.prefs(context).all)
    }

    @Test fun selectedPhotoReachesPhotoEditorWithoutChangingDraftOrAppliedState() {
        PhotoOptions.defaults().edit(3, 3f).edit(14, 0f).edit(15, 2f).save(context, true)
        PhotoOptions.defaults().edit(3, 2f).save(context, false)
        val draft = context.getSharedPreferences("alive_photo_draft", 0).all.toMap()
        val applied = context.getSharedPreferences("alive_photo_applied", 0).all.toMap()
        val settings = Config.prefs(context).all.toMap()
        val intent = newAlivePhotoPreview(context, image(), 602, ::importFixture)!!
        assertEquals(AlivePreviewActivity::class.java.name, intent.component?.className)
        assertEquals(602, intent.getIntExtra("variant", -1))
        assertEquals(1, intent.getIntExtra("mode", -1))
        val preset = AlivePreset.parse(intent.getStringExtra("preset")!!)
        assertEquals(602, preset.variant)
        assertEquals("留光", preset.name)
        assertEquals(1f, preset.photo.zoom, 0f)
        assertEquals(0, preset.photo.aodMode)
        assertTrue(preset.photo.followLock)
        val source = PhotoMedia.resolve(context, preset.photo, true, 0)
        assertTrue(source.isFile)
        assertEquals(source, PhotoMedia.resolve(context, preset.photo, true, 1))
        assertEquals(source, PhotoMedia.resolve(context, preset.photo, true, 2))
        val decoded = BitmapFactory.decodeFile(source.path)
        try { assertEquals(Color.RED, decoded.getPixel(0, 0)) } finally { decoded.recycle() }
        assertEquals(draft, context.getSharedPreferences("alive_photo_draft", 0).all)
        assertEquals(applied, context.getSharedPreferences("alive_photo_applied", 0).all)
        assertEquals(settings, Config.prefs(context).all)
        assertTrue(AlivePresets.list(context).isEmpty())
    }

    @Test fun dynamicSceneFallsBackToDefaultPhotoStyle() {
        val intent = newAlivePhotoPreview(context, image(), 1, ::importFixture)!!
        assertEquals(601, intent.getIntExtra("variant", -1))
        assertEquals("圆景", intent.getStringExtra("name"))
    }

    @Test fun invalidImageDoesNotOverwriteDraftOrAppliedPreferences() {
        val draft = context.getSharedPreferences("alive_photo_draft", 0).all.toMap()
        val applied = context.getSharedPreferences("alive_photo_applied", 0).all.toMap()
        assertThrows(IOException::class.java) {
            newAlivePhotoPreview(context, Uri.fromFile(File(context.cacheDir, "missing-image.png")), 601) { _, _ ->
                throw IOException("Invalid image")
            }
        }
        assertEquals(draft, context.getSharedPreferences("alive_photo_draft", 0).all)
        assertEquals(applied, context.getSharedPreferences("alive_photo_applied", 0).all)
    }
}
