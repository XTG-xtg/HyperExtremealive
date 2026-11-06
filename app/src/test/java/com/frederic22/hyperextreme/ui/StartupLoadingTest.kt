package com.frederic22.hyperextreme.ui

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import com.frederic22.hyperextreme.data.MaskPrefs
import com.frederic22.hyperextreme.data.MaskRole
import com.frederic22.hyperextreme.data.MaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StartupLoadingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun image() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    private fun sources() = (MaskRole.selectable.map { MaskStore.file(context, it) } + MaskStore.parallaxFile(context))
        .onEach { it.writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun homeAndUnrelatedPagesNeverAccessPhotoStorage() = runBlocking {
        val noStorage = object : ContextWrapper(context) {
            override fun getFilesDir(): File = error("Home must not access photo files")
        }
        val loader = MaskPreviewLoader(noStorage, MaskPreviewContent.NONE) { error("Unexpected decode") }
        assertEquals(MaskPreviewLoader.Snapshot(), loader.load(1))
        assertSame(loader.load(1), loader.load(2))
    }

    @Test fun maskPageLoadsOnlyMasksAndCoalescesConcurrentResume() = runBlocking {
        sources()
        val calls = AtomicInteger()
        val loader = MaskPreviewLoader(context, MaskPreviewContent.MASK) { file ->
            assertNotSame(Looper.getMainLooper(), Looper.myLooper())
            assertNotEquals(MaskStore.PARALLAX_FILE_NAME, file.name)
            calls.incrementAndGet()
            image()
        }
        val results = List(8) { async { loader.load(1) } }.awaitAll()
        results.forEach { assertSame(results.first(), it) }
        assertEquals(MaskRole.selectable.size, calls.get())
        assertEquals(MaskRole.selectable.toSet(), results.first().masks.keys)
        assertNull(results.first().parallax)
    }

    @Test fun parallaxPageNeverDecodesMasks() = runBlocking {
        sources()
        var count = 0
        val loader = MaskPreviewLoader(context, MaskPreviewContent.PARALLAX) { file ->
            assertEquals(MaskStore.PARALLAX_FILE_NAME, file.name)
            count++
            image()
        }
        val loaded = loader.load(1)
        assertNotNull(loaded.parallax)
        assertTrue(loaded.masks.isEmpty())
        assertSame(loaded, loader.load(1))
        assertEquals(1, count)
    }

    @Test fun importRevisionInvalidatesEvenWhenSizeAndTimestampMatch() = runBlocking {
        sources()
        val loader = MaskPreviewLoader(context, MaskPreviewContent.PARALLAX) { image() }
        val old = loader.load(7)
        val file = MaskStore.parallaxFile(context)
        val stamp = file.lastModified()
        file.writeBytes(byteArrayOf(4, 5, 6))
        assertTrue(file.setLastModified(stamp))
        assertNotSame(old.parallax, loader.load(8).parallax)
    }

    @Test fun changedOrDeletedFilesInvalidateWithoutSettingsWrite() = runBlocking {
        sources()
        val loader = MaskPreviewLoader(context, MaskPreviewContent.PARALLAX) { image() }
        val old = loader.load(1)
        val file = MaskStore.parallaxFile(context)
        file.appendBytes(byteArrayOf(4))
        assertNotSame(old.parallax, loader.load(1).parallax)
        assertTrue(file.delete())
        assertNull(loader.load(1).parallax)
    }

    @Test fun preferenceListenerIsReleasedWithItsScreen() = runBlocking {
        val prefs = MaskPrefs(context)
        prefs.setRootAvailable(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val state = MaskUiState(context, prefs, scope)
        try {
            prefs.bumpRevision()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(prefs.revision, state.revision)
            prefs.bumpRevision()
            state.close()
            val closedRevision = state.revision
            shadowOf(Looper.getMainLooper()).idle()
            prefs.bumpRevision()
            shadowOf(Looper.getMainLooper()).idle()
            state.onResume()
            state.reloadPreviews()
            assertEquals(closedRevision, state.revision)
            assertTrue(state.previews.isEmpty())
        } finally {
            state.close()
            scope.cancel()
        }
    }

    @Test fun duplicateAppIconsDecodeOnceOffMainThread() = runBlocking {
        val calls = AtomicInteger()
        val loader = AppIconLoader(context, 132) {
            assertNotSame(Looper.getMainLooper(), Looper.myLooper())
            calls.incrementAndGet()
            image().asImageBitmap()
        }
        val results = List(8) { async { loader.load("same.package") } }.awaitAll()
        results.forEach { assertSame(results.first(), it) }
        assertEquals(1, calls.get())
    }

    @Test fun missingAppIconsAreCachedWithoutRepeatedBinderCalls() = runBlocking {
        var calls = 0
        val loader = AppIconLoader(context, 132) { calls++; null }
        repeat(4) { assertNull(loader.load("missing.package")) }
        assertEquals(1, calls)
    }

    @Test fun iconCacheIsBoundedAndKeepsOnlyRequestedPixelSize() = runBlocking {
        var calls = 0
        val cache = AppIconLoader(context, 132) { calls++; image().asImageBitmap() }
        repeat(9) { cache.load("package.$it") }
        cache.load("package.0")
        assertEquals(10, calls)
        shadowOf(context.packageManager).setApplicationIcon(context.packageName,
            android.graphics.drawable.ColorDrawable(android.graphics.Color.RED))
        val realIcon = AppIconLoader(context, 96).load(context.packageName)
        assertNotNull(realIcon)
        assertEquals(96, realIcon!!.width)
        assertEquals(96, realIcon.height)
    }
}
