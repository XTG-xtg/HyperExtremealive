package com.frederic22.hyperextreme.alive

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class AlivePreset(val id: String, val name: String, val variant: Int, val dark: Boolean,
    val clock: Boolean, val photo: PhotoOptions, val render: AliveRenderOptions, val created: Long = System.currentTimeMillis(),
    val vivoJson: String = "") {
    fun json(): String {
        val p = photo.bundle()
        return JSONObject().put("version", 1).put("name", name).put("variant", variant).put("dark", dark).put("clock", clock)
            .put("values", JSONArray(p.getFloatArray("values")!!.toList())).put("media", JSONArray(p.getStringArray("media")!!.toList()))
            .put("follow", photo.followLock).put("tint", photo.tint)
            .put("render", JSONArray(listOf(if(render.keepLock) 1 else 0, if(render.continuousAod) 1 else 0,
                if(render.continuousHome) 1 else 0, if(render.followClock) 1 else 0, if(render.burnIn) 1 else 0,
                if(render.stereoAod) 1 else 0, render.sensitivity, render.brightness, render.aodOffsetX, render.aodOffsetY)))
            .put("vivo", vivoJson).put("created", created).toString()
    }
    fun signature(): String {
        val normalized = JSONObject(json()).apply { remove("created"); remove("name") }.toString()
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    companion object {
        fun parse(text: String): AlivePreset {
            require(text.length < 32_768)
            val j = JSONObject(text); require(j.getInt("version") == 1)
            val variant = j.getInt("variant"); require(variant in 1..606 || variant in 701..703 || InfinityTheme.supports(variant))
            val vivo = j.optString("vivo", "")
            if(variant in 701..703) require(VivoOptions.parse(vivo)?.family == variant - 700)
            else require(vivo.isEmpty())
            val v=j.getJSONArray("values");val m=j.getJSONArray("media");val r=j.getJSONArray("render")
            require(v.length()==15 && m.length()==3 && r.length()==10)
            val b=Bundle().apply {
                putFloatArray("values", FloatArray(15){v.getDouble(it).toFloat()})
                putStringArray("media", Array(3){m.getString(it)})
                putBoolean("follow",j.getBoolean("follow"));putInt("tint",j.getInt("tint"))
            }
            val value=AlivePreset("",j.getString("name").take(80),variant,j.getBoolean("dark"),j.getBoolean("clock"),PhotoOptions.fromBundle(b),
                AliveRenderOptions(r.getInt(0)==1,r.getInt(1)==1,r.getInt(2)==1,r.getInt(3)==1,r.getInt(4)==1,r.getInt(5)==1,
                    r.getInt(6),r.getDouble(7).toFloat(),r.getDouble(8).toFloat(),r.getDouble(9).toFloat()),j.getLong("created"),vivo)
            return value.copy(id=value.signature())
        }
    }
}

/** Immutable photo references plus real lockscreen covers; never publishes wallpaper preferences. */
internal object AlivePresets {
    private fun dir(c: Context)=File(c.filesDir,"alive/presets").apply { check(isDirectory || mkdirs()) }
    fun cover(c: Context,id: String): File { require(id.matches(Regex("[a-f0-9]{64}")));return File(dir(c),"$id.png") }
    @Synchronized
    fun list(c: Context): List<AlivePreset> = dir(c).listFiles().orEmpty()
        .filter { it.name.matches(Regex("[a-f0-9]{64}\\.json")) }.mapNotNull { file ->
            runCatching { readBoundedBrightness(c,file) }
                .onFailure { android.util.Log.w("AliveClean","Unreadable preset ${file.name}",it) }.getOrNull()
        }.distinctBy { it.id }.sortedByDescending { it.created }
    /** ui31 allowed values above 5. Keep those saved presets and covers while
     * migrating their content-addressed identity to the bounded value. */
    private fun readBoundedBrightness(c: Context,file: File): AlivePreset {
        val text=read(file)
        val value=AlivePreset.parse(text)
        if(value.id==file.nameWithoutExtension)return value
        val old=JSONObject(text).getJSONArray("render").getDouble(7).toFloat()
        require(old.isFinite()&&old>AodBrightness.MAX)
        val canonical=JSONObject(value.json()).apply {
            getJSONArray("render").put(7,old)
            remove("created");remove("name")
        }
        val oldId=MessageDigest.getInstance("SHA-256").digest(canonical.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        require(oldId==file.nameWithoutExtension)
        val oldCover=cover(c,oldId);val newCover=cover(c,value.id)
        if(oldCover.isFile&&!newCover.isFile) atomic(newCover) { output -> oldCover.inputStream().use { it.copyTo(output) } }
        val target=File(dir(c),"${value.id}.json")
        if(!target.isFile)atomic(target) { it.write(value.json().toByteArray()) }
        // Retain the original record and cover for rollback; only .json files
        // participate in the active list. Retrying after interruption is safe.
        file.renameTo(File(file.parentFile,"${file.name}.ui31"))
        return value
    }
    private fun read(file: File): String = AtomicFile(file).openRead().bufferedReader().use { reader ->
        // Bound the read itself, not only JSON parsing, when recovering damaged records.
        val buffer = CharArray(32_768)
        var length = 0
        while (length < buffer.size) {
            val count = reader.read(buffer, length, buffer.size - length)
            if (count < 0) break
            length += count
        }
        require(length < buffer.size) { "Preset record is too large" }
        String(buffer, 0, length)
    }
    fun family(variant: Int): Int = when {
        variant in 701..703 -> variant
        InfinityTheme.supports(variant) -> 800
        else -> 0
    }
    private fun lastFile(c: Context,variant: Int)=File(dir(c),when(family(variant)) {
        701,702,703 -> "last-$variant.json"
        800 -> "last-infinity.json"
        else -> "last-alive.json"
    })
    @Synchronized
    fun current(c: Context, familyVariant: Int = 0): AlivePreset {
        require(familyVariant == 0 || familyVariant == 800 || familyVariant in 701..703)
        val variant=Config.variant(c)
        if(family(variant) != familyVariant) {
            // ui19 stored Infinity in last-alive.json; accept that record only for Infinity.
            val candidates = if(familyVariant == 800) listOf(lastFile(c,801),lastFile(c,0)) else listOf(lastFile(c,familyVariant))
            for(last in candidates) if(last.isFile) runCatching {
                val saved = AlivePreset.parse(read(last))
                saved.takeIf { family(it.variant) == familyVariant }
            }.onFailure { android.util.Log.w("AliveClean", "Unreadable family snapshot ${last.name}", it) }
                .getOrNull()?.let { return it }
        }
        val id=if(familyVariant in 701..703) familyVariant else aliveHomeVariant(c, familyVariant == 800)
        val name=runCatching { AliveCatalog.load(c).firstOrNull { it.id==id }?.name }.getOrNull() ?: "Alive"
        return AlivePreset("",name,id,Config.dark(c),Config.prefs(c).getBoolean("clock",false),
            if(id in 601..606) PhotoMedia.freezeLegacy(c,PhotoOptions.load(c,false),false) else PhotoOptions.defaults(),AliveRenderOptions.load(c),
            vivoJson=if(id in 701..703) VivoOptions.load(c,id,false).json() else "")
    }
    fun snapshot(c: Context,name: String,variant: Int,dark: Boolean,clock: Boolean,photo: PhotoOptions,render: AliveRenderOptions,preview: Boolean,
        vivo: VivoOptions? = null): AlivePreset {
        require(AliveCatalog.load(c).any { it.id==variant })
        val frozen=if(variant in 601..606) PhotoMedia.freezeLegacy(c,photo,preview) else PhotoOptions.defaults()
        if(variant in 601..606) PhotoMedia.validate(c,frozen,false,variant)
        val vo=if(variant in 701..703) vivo ?: VivoOptions.load(c,variant,preview) else null
        if(vo!=null){require(vo.family==variant-700);vo.validateFiles(c)}
        val value=AlivePreset("",name,variant,dark,clock,frozen,render,vivoJson=vo?.json().orEmpty())
        return value.copy(id=value.signature())
    }
    private fun atomic(file: File, writer: (java.io.FileOutputStream)->Unit) {
        val target=AtomicFile(file);val stream=target.startWrite()
        try { writer(stream);target.finishWrite(stream) } catch(e: Throwable) { target.failWrite(stream);throw e }
    }
    @Synchronized
    fun write(c: Context,preset: AlivePreset,bitmap: Bitmap) {
        require(preset.id==preset.signature())
        atomic(cover(c,preset.id)) { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        atomic(File(dir(c),"${preset.id}.json")) { it.write(preset.json().toByteArray()) }
    }
    @Synchronized
    fun markCurrent(c: Context,preset: AlivePreset) {
        atomic(lastFile(c,preset.variant)) { it.write(preset.json().toByteArray()) }
    }
    @Synchronized
    fun delete(c: Context,id: String) {
        val image=cover(c,id)
        AtomicFile(File(dir(c),"$id.json")).delete();AtomicFile(image).delete()
        // Shared immutable source photos can still belong to current or other presets.
    }
    suspend fun save(c: Context,preset: AlivePreset): AlivePreset {
        // Keep history even when a slim-APK upgrade is awaiting its resource pack.
        // Missing covers can be generated after import; this must not block other families.
        if(DynamicResources.needsPack(preset.variant) && !withContext(Dispatchers.IO) { DynamicResources.installed(c) }) {
            require(preset.id == preset.signature())
            withContext(Dispatchers.IO) { atomic(File(dir(c),"${preset.id}.json")) { it.write(preset.json().toByteArray()) } }
            return preset
        }
        val exists=withContext(Dispatchers.IO) { cover(c,preset.id).isFile && list(c).any { it.id==preset.id } }
        if(exists)return preset
        val frame=suspendCancellableCoroutine<Bitmap> { continuation ->
            val worker=AliveLayoutFrames(c,preset.variant,preset.dark,288,640,preset.render,preset.photo,VivoOptions.parse(preset.vivoJson),true) { images,error ->
                if(!continuation.isActive) images.forEach { it.recycle() }
                else if(error.isNotEmpty()) continuation.resumeWithException(IllegalStateException(error))
                else continuation.resume(images[0]) { _,bitmap,_ -> bitmap.recycle() }
            }
            continuation.invokeOnCancellation { worker.close() }
        }
        try { withContext(Dispatchers.IO) { write(c,preset,frame) } } finally { frame.recycle() }
        return preset
    }
}
