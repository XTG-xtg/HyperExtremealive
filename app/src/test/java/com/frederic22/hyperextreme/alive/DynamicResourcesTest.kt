package com.frederic22.hyperextreme.alive

import android.app.Application
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object DynamicPackFixture {
    val file get() = File("../build/dynamic-wallpapers/HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip")
    fun storage(context: Context) = org.robolectric.shadows.ShadowStatFs.registerStats(context.filesDir.path,1_000_000,900_000,900_000)
    fun install(context: Context) {
        storage(context)
        file.inputStream().use { DynamicResources.importPack(context,it) {} }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class DynamicResourcesTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun storage() = DynamicPackFixture.storage(app)
    private val manifest get() = app.assets.open("dynamic-wallpapers.json").use { it.readBytes() }
    private val root get() = File(app.filesDir,"alive/dynamic-packs")
    private fun zip(vararg entries: Pair<String,ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out -> entries.forEach { (name,data) ->
            out.putNextEntry(ZipEntry(name));out.write(data);out.closeEntry()
        } }
    }.toByteArray()
    private fun import(bytes: ByteArray) = ByteArrayInputStream(bytes).use { DynamicResources.importPack(app,it) {} }
    private fun noStaging() = assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("stage-") })
    private fun digest(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    @Test fun completePackOpensEveryExternalAssetAndVideoDescriptorWithoutChangingSettings() {
        assertFalse(DynamicResources.installed(app))
        val before=HashMap(com.frederic22.hyperextreme.alive.Config.prefs(app).all)
        val progress=mutableListOf<Int>()
        DynamicPackFixture.file.inputStream().use { DynamicResources.importPack(app,it) { p -> progress.add(p) } }
        assertTrue(DynamicResources.installed(app));assertEquals(100,progress.last())
        assertTrue(progress.zipWithNext().all { (a,b) -> a<=b })
        val files=JSONObject(String(manifest,Charsets.UTF_8)).getJSONObject("files")
        assertEquals(48,files.length())
        for(name in files.keys()) {
            val expected=files.getJSONObject(name)
            assertThrows(IOException::class.java) { app.assets.open(name).close() }
            val bytes=AssetGl.bytes(app.assets,name)
            assertEquals(expected.getLong("bytes"),bytes.size.toLong())
            assertEquals(expected.getString("sha256"),digest(bytes))
            if(name.endsWith(".mp4")) DynamicResources.openFd(app.assets,name).use { fd ->
                assertEquals(0L,fd.startOffset);assertEquals(bytes.size.toLong(),fd.length)
                fd.createInputStream().use { assertEquals(digest(bytes),digest(it.readBytes())) }
            }
        }
        assertEquals(before,com.frederic22.hyperextreme.alive.Config.prefs(app).all)
        DynamicResources.require(app,1);noStaging()
    }

    @Test fun malformedArchivesCannotPublishOrEscapeTheirDirectory() {
        val manifest=manifest
        val rows=JSONObject(String(manifest,Charsets.UTF_8)).getJSONObject("files")
        val first=rows.keys().asSequence().minBy { rows.getJSONObject(it).getLong("bytes") }
        val corrupt=ByteArray(rows.getJSONObject(first).getInt("bytes"))
        val bad=listOf(
            zip("manifest.json" to "{}".toByteArray()),
            zip("manifest.json" to manifest),
            zip("manifest.json" to manifest,"assets/$first" to corrupt),
            zip("manifest.json" to manifest,"assets/extra.dex" to byteArrayOf(1)),
            zip("../escaped" to byteArrayOf(1)),
            zip("assets/../../escaped" to byteArrayOf(1)),
            zip("/absolute" to byteArrayOf(1)),
            zip("C:/drive" to byteArrayOf(1)),
            zip("assets\\escape" to byteArrayOf(1)),
            zip("assets//escape" to byteArrayOf(1)),
            zip("assets/" to byteArrayOf())
        )
        for(bytes in bad) {
            assertThrows(IOException::class.java) { import(bytes) }
            assertFalse(DynamicResources.installed(app));noStaging()
        }
        assertFalse(File(root.parentFile,"escaped").exists())
    }

    @Test fun duplicateManifestIsRejected() {
        // ZIP libraries reject duplicate names when writing, so replace the equal-length header name.
        val bytes=zip("manifest.json" to manifest,"manifesx.json" to manifest)
        val search="manifesx.json".toByteArray();val replacement="manifest.json".toByteArray()
        for(i in 0..bytes.size-search.size) if(search.indices.all { bytes[i+it]==search[it] })
            replacement.copyInto(bytes,i)
        assertThrows(IOException::class.java) { import(bytes) }
        assertFalse(DynamicResources.installed(app));noStaging()
    }

    @Test fun interruptedAndFailedImportsLeaveAnInstalledPackIntact() {
        DynamicPackFixture.install(app)
        val generation=root.listFiles()!!.single()
        val marker=File(generation,"complete").readBytes()
        assertThrows(CancellationException::class.java) {
            DynamicPackFixture.file.inputStream().use { DynamicResources.importPack(app,it) { p ->
                if(p>=1) throw CancellationException("test cancellation")
            } }
        }
        assertThrows(IOException::class.java) { import(zip("manifest.json" to "{}".toByteArray())) }
        assertTrue(DynamicResources.installed(app));assertArrayEquals(marker,File(generation,"complete").readBytes());noStaging()
        DynamicPackFixture.install(app)
        assertEquals(listOf(generation),root.listFiles()!!.toList());noStaging()
    }

    @Test fun interruptedFirstImportDoesNotBecomeAvailable() {
        assertThrows(CancellationException::class.java) {
            DynamicPackFixture.file.inputStream().use { DynamicResources.importPack(app,it) { p ->
                if(p>=1) throw CancellationException("test cancellation")
            } }
        }
        assertFalse(DynamicResources.installed(app));noStaging()
    }

    @Test fun missingPackPreventsApplyingOrEnablingBeforePreferencesAreChanged() {
        val prefs=com.frederic22.hyperextreme.alive.Config.prefs(app)
        prefs.edit().putInt("variant",1).putBoolean("enabled",false).commit()
        val before=HashMap(prefs.all)
        assertEquals(DynamicResources.MISSING,assertThrows(IOException::class.java) { AliveSettings.apply(app,1,false,false) }.message)
        assertEquals(DynamicResources.MISSING,assertThrows(IOException::class.java) { AliveSettings.setEnabled(app,true) }.message)
        assertEquals(before,prefs.all)
        for(variant in listOf(601,606,701,702,703,801,806)) DynamicResources.require(app,variant)
        assertTrue(AliveCatalog.load(app).any { it.id==803 })
    }

    @Test fun missingPackStillPreservesDynamicPresetHistoryWithoutFakeCover() = runBlocking {
        val value=AlivePresets.snapshot(app,"星云绿",1,false,false,PhotoOptions.defaults(),AliveRenderOptions.load(app),false)
        val before=HashMap(com.frederic22.hyperextreme.alive.Config.prefs(app).all)
        AlivePresets.save(app,value)
        assertEquals(value.signature(),AlivePresets.list(app).single().signature())
        assertFalse(AlivePresets.cover(app,value.id).exists())
        assertEquals(before,com.frederic22.hyperextreme.alive.Config.prefs(app).all)
    }
}
