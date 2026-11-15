package com.frederic22.hyperextreme.alive

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

@Composable
internal fun VivoMaskEditor(options: VivoOptions, subject: Boolean, onSaved: (String, String, String) -> Unit, onCancel: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var canvas by remember { mutableStateOf<VivoMaskCanvas?>(null) }
    var inference by remember { mutableStateOf<VivoSubjectRecognition?>(null) }
    var source by remember { mutableStateOf(options.subjectSource.ifEmpty { "手动编辑" }) }
    var detail by remember { mutableStateOf(options.subjectDetail) }
    var working by remember { mutableStateOf(false) }
    var tool by remember { mutableIntStateOf(0) }
    var brush by remember { mutableFloatStateOf(.03f) }
    var showMask by remember { mutableStateOf(true) }
    var canUndo by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在加载照片…") }
    LaunchedEffect(Unit) {
        var photo: Bitmap?=null;var mask: Bitmap?=null
        try {
            withContext(Dispatchers.IO) {
                val runtime=VivoEngineRuntime.prepare(context).get(20,TimeUnit.SECONDS)
                VivoImages.load(context,runtime,options).use { images ->
                    photo=images.photos.removeAt(0)
                    if(subject) { mask=images.subject;images.subject=null } else { mask=images.paint;images.paint=null }
                }
            }
            canvas=VivoMaskCanvas(context,photo!!,mask).also { view ->
                view.onHistoryChanged = Runnable { canUndo = view.canUndo() }
            };photo=null;mask=null
            status=""
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { status="照片加载失败，请返回后重试" }
        finally { photo?.recycle();mask?.recycle() }
    }
    DisposableEffect(Unit) { onDispose { inference?.cancel();canvas?.close() } }
    fun cancel() { inference?.cancel(); onCancel() }
    BackHandler(onBack=::cancel)
    fun generate(full: Boolean) {
        val view=canvas?:return
        if(working) return
        if(full) view.box.set(0f,0f,1f,1f)
        val selected=RectF(view.box)
        val input=view.photo.copy(Bitmap.Config.ARGB_8888,false)
        val job=VivoSubjectRecognition();inference=job;working=true;status="正在准备主体识别…"
        scope.launch {
            var generated: Bitmap?=null
            try {
                withContext(Dispatchers.IO) {
                    val result = job.generate(context.applicationContext, input, selected, options.subjectEngine,
                        options.photos.firstOrNull()?.let { File(context.filesDir, it) }) { text -> scope.launch { status = text } }
                    generated = result.mask
                    source = result.source; detail = result.detail
                }
                view.replace(generated!!);tool=0
                status="主体已生成，可用画笔和擦除修整边缘"
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { status="识别未完成，可继续手动涂抹：${e.message}" }
            finally { input.recycle();generated?.recycle();working=false;inference=null }
        }
    }
    fun save() {
        val view=canvas?:return
        if(working) return
        working=true;status="正在保存蒙版…"
        val snapshot=view.mask.copy(Bitmap.Config.ARGB_8888,false)
        val name="vivo-${UUID.randomUUID()}-${if(subject) "subject" else "paint"}.png"
        scope.launch {
            var published=false
            try {
                withContext(Dispatchers.IO) { FileOutputStream(File(context.filesDir,name)).use { out ->
                    check(snapshot.compress(Bitmap.CompressFormat.PNG,100,out)) { "蒙版编码失败" };out.fd.sync()
                } }
                onSaved(name, "$source · 可手动修整", detail);published=true
            } catch(e: Exception) { status="保存失败：${e.message}" }
            finally { snapshot.recycle();working=false;if(!published) File(context.filesDir,name).delete() }
        }
    }
    MaskEditorContent(subject, canvas != null, working, tool, brush, showMask, canUndo, status,
        onCancel = ::cancel, onSave = ::save,
        onTool = { tool = it; showMask = true; status = "" }, onBrush = { brush = it },
        onCompare = { showMask = !showMask }, onUndo = { canvas?.undo() }, onClear = { canvas?.clear() },
        onRecognize = { generate(tool != 2) }) {
        canvas?.let { view -> AndroidView(factory = { view }, update = {
            it.isEnabled = !working && showMask; it.erase = tool == 1; it.boxMode = tool == 2
            it.brush = brush; it.showMask = showMask; it.invalidate()
        }, modifier = Modifier.fillMaxSize()) }
    }
}
