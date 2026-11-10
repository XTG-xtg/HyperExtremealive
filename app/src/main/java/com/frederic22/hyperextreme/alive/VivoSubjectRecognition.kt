package com.frederic22.hyperextreme.alive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.frederic22.hyperextreme.aicr.AicrVision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Same AICR client as raster; only a failed automatic route falls back to U2-Net. */
internal class VivoSubjectRecognition(
    private val system: (Context, Bitmap, File?) -> AicrVision.Result = { context, bitmap, file ->
        if (file != null) AicrVision.segmentFile(context, file) else AicrVision.segment(context, bitmap)
    },
    private val local: ((Context, Bitmap, RectF) -> Bitmap)? = null,
) {
    private val localModel = VivoSubjectMask()
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; localModel.cancel() }
    private fun checkCancelled() { if (cancelled) throw CancellationException("主体识别已取消") }

    data class Result(val mask: Bitmap, val source: String, val detail: String)

    fun generate(context: Context, photo: Bitmap, selection: RectF, engine: Int,
        original: File? = null, progress: (String) -> Unit = {}): Result {
        checkCancelled()
        require(engine in 0..2)
        val box = Rect(
            (selection.left * photo.width).toInt().coerceIn(0, photo.width - 1),
            (selection.top * photo.height).toInt().coerceIn(0, photo.height - 1),
            (selection.right * photo.width).toInt().coerceIn(1, photo.width),
            (selection.bottom * photo.height).toInt().coerceIn(1, photo.height),
        )
        require(box.width() > 0 && box.height() > 0) { "请框选有效区域" }
        val full = box == Rect(0, 0, photo.width, photo.height)
        var reason = ""
        if (engine != 2) {
            progress("正在连接系统 AI；首次使用可能需要下载模型…")
            val input = if (full) photo else Bitmap.createBitmap(photo, box.left, box.top, box.width(), box.height())
            var response: AicrVision.Result? = null
            try {
                response = system(context, input, if (full) original else null)
                checkCancelled()
                val mask = response.mask ?: response.bitmap?.let(AicrVision::deriveMask)
                try {
                    if (mask != null) {
                        check(abs(mask.width.toFloat() / mask.height - input.width.toFloat() / input.height) < .02f) {
                            "系统返回的主体与原图比例不一致，未拉伸套用"
                        }
                        checkUsefulMask(mask)
                        val aligned = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
                        aligned.eraseColor(Color.BLACK)
                        Canvas(aligned).drawBitmap(mask, null, box, Paint(Paint.FILTER_BITMAP_FLAG))
                        return Result(aligned, "系统 AI · AICR", response.detail)
                    }
                    reason = response.detail
                } finally {
                    if (mask !== response.mask && mask !== response.bitmap) mask?.recycle()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reason = e.message ?: "系统 AI 无可用结果" }
            finally {
                response?.mask?.let { if (it !== response.bitmap) it.recycle() }
                response?.bitmap?.recycle()
                if (input !== photo) input.recycle()
            }
            if (engine == 1) throw IllegalStateException("系统 AI 识别失败：$reason。可切换到本地模型或手动修整。")
        }
        checkCancelled()
        progress(if (reason.isEmpty()) "正在用本地 U²-Net 识别主体…" else "系统 AI 未完成，正在用本地 U²-Net 识别…")
        val mask = local?.invoke(context, photo, selection) ?: localModel.generate(context, photo, selection)
        try {
            checkCancelled()
            checkUsefulMask(mask)
            return Result(mask, "本地 AI · U²-Net", if (reason.isEmpty()) "本机离线识别" else "系统 AI：$reason；已使用本地模型")
        } catch (e: Exception) { mask.recycle(); throw e }
    }

    companion object {
        internal fun checkUsefulMask(mask: Bitmap) {
            var low = 255
            var high = 0
            val row = IntArray(mask.width)
            for (y in 0 until mask.height) {
                mask.getPixels(row, 0, mask.width, 0, y, mask.width, 1)
                for (pixel in row) { val v = Color.red(pixel); low = minOf(low, v); high = maxOf(high, v) }
            }
            check(high - low >= 16) { "未找到可分离主体，请框选主体或手动涂抹" }
        }
    }
}

/** Immutable, uncommitted result. Cancelled work cannot publish a mask or replace a draft. */
internal suspend fun recognizeVivoDraft(context: Context, snapshot: VivoOptions, area: Int,
    task: VivoSubjectRecognition, progress: (String) -> Unit): VivoOptions {
    var output: File? = null
    var delivered = false
    try {
        val result = withContext(Dispatchers.IO) {
            val runtime = VivoEngineRuntime.prepare(context).get(20, TimeUnit.SECONDS)
            VivoImages.load(context, runtime, snapshot).use { images ->
                val source = snapshot.photos.firstOrNull()?.let { File(context.filesDir, it) }
                val generated = task.generate(context, images.photos.first(), RectF(0f, 0f, 1f, 1f),
                    snapshot.subjectEngine, source, progress)
                try {
                    val file = File(context.filesDir, "vivo-${UUID.randomUUID()}-subject.png")
                    output = file
                    FileOutputStream(file).use { stream ->
                        check(generated.mask.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "主体蒙版保存失败" }
                        stream.fd.sync()
                    }
                    VivoOptions.parse(snapshot.json()).apply {
                        subject = file.name; subjectSource = generated.source; subjectDetail = generated.detail
                        this.area = area.coerceIn(2, 3)
                    }
                } finally { generated.mask.recycle() }
            }
        }
        delivered = true
        return result
    } finally { if (!delivered) output?.delete() }
}
