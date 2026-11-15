package com.frederic22.hyperextreme.alive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Exports saved coverage, not a screenshot, cropped preview, or a new AI inference. */
internal object VivoMaskExport {
    data class Prepared(val file: File, val width: Int, val height: Int)

    fun available(options: VivoOptions, subject: Boolean): Boolean =
        if (subject) options.subject.isNotEmpty() || options.photos.isEmpty() && options.family != VivoOptions.RASTER
        else options.paint.isNotEmpty() || options.photos.isEmpty() && options.family == VivoOptions.FLASH

    fun prepare(context: Context, options: VivoOptions, subject: Boolean): Prepared {
        check(available(options, subject)) { if (subject) "请先识别或导入主体蒙版" else "请先涂抹或导入效果区域" }
        var mask: Bitmap? = null
        var normalized: Bitmap? = null
        var output: File? = null
        try {
            val name = if (subject) options.subject else options.paint
            val width: Int
            val height: Int
            if (options.photos.isNotEmpty()) {
                val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(media(context, options.photos.first()).path, info)
                check(info.outWidth > 0 && info.outHeight > 0) { "原图无法读取，未导出蒙版" }
                width = info.outWidth; height = info.outHeight
                mask = BitmapFactory.decodeFile(media(context, name).path)
                    ?: throw IOException("蒙版无法读取，请重新生成或导入")
            } else {
                val runtime = VivoEngineRuntime.prepare(context).get(20, TimeUnit.SECONDS)
                VivoImages.load(context, runtime, options).use { images ->
                    width = images.photos.first().width; height = images.photos.first().height
                    mask = if (subject) images.subject.also { images.subject = null }
                        else images.paint.also { images.paint = null }
                }
            }
            normalized = normalizeMask(options, subject, checkNotNull(mask) { "尚无可导出的蒙版" }, width, height)
            val file = File(context.cacheDir, "vivo-export-${UUID.randomUUID()}.png"); output = file
            FileOutputStream(file).use { stream ->
                check(normalized.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG 编码失败" }
                stream.fd.sync()
            }
            check(file.length() > 0) { "蒙版文件为空" }
            return Prepared(file, width, height)
        } catch (e: Exception) { output?.delete(); throw e }
        finally { normalized?.recycle(); mask?.recycle() }
    }

    internal fun normalizeMask(options: VivoOptions, subject: Boolean, mask: Bitmap, width: Int, height: Int): Bitmap =
        if (subject && options.photos.isEmpty() && options.subject.isEmpty())
            VivoMaskGeometry.normalizeTexture(mask, width, height)
        else VivoMaskGeometry.normalize(mask, width, height)

    private fun media(context: Context, name: String): File {
        require(name.matches(Regex("vivo-[A-Za-z0-9._-]+"))) { "无效的蒙版来源" }
        return File(context.filesDir, name).also { check(it.isFile && it.length() > 0) { "原图或蒙版文件不存在" } }
    }

    fun prepared(context: Context, name: String): File {
        require(name.matches(Regex("vivo-export-[a-f0-9-]+\\.png"))) { "无效的导出文件" }
        return File(context.cacheDir, name)
    }

    fun write(context: Context, name: String, uri: Uri): Long {
        val file = prepared(context, name)
        check(file.isFile && file.length() > 0) { "导出缓存已失效，请重新导出" }
        val expected = file.length()
        val copied = context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }.also { out.flush() }
        } ?: throw IOException("无法打开保存位置，请选择其他文件夹")
        check(copied == expected) { "文件写入不完整，请重新导出" }
        return copied
    }
}

@Composable
internal fun rememberVivoMaskExport(options: VivoOptions, message: (String) -> Unit): (Boolean) -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(options)
    val report by rememberUpdatedState(message)
    var pending by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var preparing by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val name = pending
        if (name.isNotEmpty()) scope.launch {
            try {
                if (uri == null) report("已取消导出，原有蒙版未改变")
                else {
                    withContext(Dispatchers.IO) { VivoMaskExport.write(context, name, uri) }
                    report("已导出 $description · PNG · 白色选中 / 黑色未选中")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report("导出失败：${e.message}；原有蒙版仍保留，可重新选择保存位置") }
            finally {
                withContext(NonCancellable + Dispatchers.IO) { VivoMaskExport.prepared(context, name).delete() }
                pending = ""
            }
        }
    }
    return { subject ->
        if (!preparing && pending.isEmpty()) {
            val snapshot = VivoOptions.parse(current.json())
            preparing = true; report("正在准备${if (subject) "主体" else "涂抹"}蒙版…")
            scope.launch {
                var file: File? = null
                var launched = false
                try {
                    val result = withContext(Dispatchers.IO) {
                        VivoMaskExport.prepare(context, snapshot, subject).also { file = it.file }
                    }
                    pending = result.file.name
                    description = "${if (subject) "主体" else "涂抹"}蒙版 ${result.width}×${result.height}"
                    save.launch("HyperExtreme-${if (subject) "subject" else "paint"}-mask-${System.currentTimeMillis()}.png")
                    launched = true
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { report("导出失败：${e.message}") }
                finally { preparing = false; if (!launched) { file?.delete(); pending = "" } }
            }
        }
    }
}
