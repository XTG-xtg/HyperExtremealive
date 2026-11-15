package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VivoMaskExportTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun fixture(): VivoOptions {
        fun image(name: String, w: Int, h: Int, mask: Boolean) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if (mask) 0xff000000.toInt() else 0xff477a88.toInt())
            if (mask) for (y in 0 until h) for (x in 0 until w / 2) bitmap.setPixel(x, y, -1)
            File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        image("vivo-export-source.png", 100, 200, false)
        image("vivo-export-subject.png", 50, 100, true)
        image("vivo-export-paint.png", 50, 100, true)
        return VivoOptions(1).apply {
            photos.add("vivo-export-source.png"); subject = "vivo-export-subject.png"; paint = "vivo-export-paint.png"
            area = 3; x = .8f; y = .7f; zoom = 3f
        }
    }
    @Test fun exportsFullOriginalDimensionsWithoutCropOrBackgroundInversion() {
        val options = fixture()
        val before = File(context.filesDir, options.subject).readBytes()
        val snapshot = options.json()
        val result = VivoMaskExport.prepare(context, options, true)
        try {
            assertEquals(100, result.width); assertEquals(200, result.height)
            val bitmap = BitmapFactory.decodeFile(result.file.path)
            assertEquals(-1, bitmap.getPixel(10, 100)); assertEquals(0xff000000.toInt(), bitmap.getPixel(90, 100))
            bitmap.recycle()
            assertArrayEquals(before, File(context.filesDir, options.subject).readBytes())
            assertEquals(snapshot, options.json())
        } finally { result.file.delete() }
    }
    @Test fun paintSnapshotWritesValidPngWithoutChangingSources() {
        val options = fixture()
        val result = VivoMaskExport.prepare(context, options, false)
        val target = File(context.cacheDir, "export-test.png")
        try {
            val count = VivoMaskExport.write(context, result.file.name, Uri.fromFile(target))
            assertEquals(result.file.length(), count)
            assertArrayEquals(result.file.readBytes(), target.readBytes())
            assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), target.readBytes().take(8).toByteArray())
            assertTrue(File(context.filesDir, options.paint).isFile)
        } finally { result.file.delete(); target.delete() }
    }
    @Test fun missingMaskNeverExportsFullImageOrRunsAi() {
        val options = fixture().apply { subject = ""; paint = "" }
        assertFalse(VivoMaskExport.available(options, true)); assertFalse(VivoMaskExport.available(options, false))
        assertThrows(IllegalStateException::class.java) { VivoMaskExport.prepare(context, options, true) }
    }
    @Test fun rejectsTraversalAndExpiredSnapshot() {
        assertThrows(IllegalArgumentException::class.java) { VivoMaskExport.prepared(context, "../vivo-export-a.png") }
        assertThrows(IllegalStateException::class.java) { VivoMaskExport.write(context, "vivo-export-0000.png", Uri.EMPTY) }
    }
    @Test fun cancellingExportOnlyRemovesSnapshot() {
        val options = fixture(); val source = File(context.filesDir, options.subject).readBytes()
        val result = VivoMaskExport.prepare(context, options, true)
        assertTrue(VivoMaskExport.prepared(context, result.file.name).delete())
        assertArrayEquals(source, File(context.filesDir, options.subject).readBytes())
    }
    @Test fun factorySquareSubjectUsesSameFullPhotoUvMappingAsRenderer() {
        val texture = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff000000.toInt())
            for(y in 16 until 48) for(x in 16 until 48) setPixel(x,y,-1)
        }
        val result = VivoMaskExport.normalizeMask(VivoOptions(1).apply { area=3 },true,texture,108,240)
        try {
            assertEquals(108,result.width);assertEquals(240,result.height)
            assertEquals(-1,result.getPixel(54,120))
            assertEquals(0xff000000.toInt(),result.getPixel(54,20))
            assertEquals(0xff000000.toInt(),result.getPixel(5,120))
        } finally { texture.recycle();result.recycle() }
    }
    @Test fun importedSquareMaskCannotBypassAspectValidation() {
        val texture=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        try {
            val options=VivoOptions(1).apply { subject="vivo-imported.png" }
            assertThrows(java.io.IOException::class.java) { VivoMaskExport.normalizeMask(options,true,texture,108,240) }
        } finally { texture.recycle() }
    }
}
