package com.frederic22.hyperextreme.alive

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

@Composable
internal fun VivoMaskEditor(options: VivoOptions, subject: Boolean, onSaved: (String) -> Unit, onCancel: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var canvas by remember { mutableStateOf<VivoMaskCanvas?>(null) }
    var inference by remember { mutableStateOf<VivoSubjectMask?>(null) }
    var working by remember { mutableStateOf(false) }
    var tool by remember { mutableIntStateOf(0) }
    var brush by remember { mutableFloatStateOf(.03f) }
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
            canvas=VivoMaskCanvas(context,photo!!,mask);photo=null;mask=null
            status=if(subject) "青色区域为主体，可自动识别后手动修整；背景效果会自动反选" else "青色区域启用动态效果，黑色蒙版区域保持不动"
        } catch(e: Exception) { status="素材加载失败：${e.message}" }
        finally { photo?.recycle();mask?.recycle() }
    }
    DisposableEffect(Unit) { onDispose { inference?.cancel();canvas?.close() } }
    BackHandler(onBack=onCancel)
    fun generate(full: Boolean) {
        val view=canvas?:return
        if(working) return
        if(full) view.box.set(0f,0f,1f,1f)
        val selected=RectF(view.box)
        val input=view.photo.copy(Bitmap.Config.ARGB_8888,false)
        val job=VivoSubjectMask();inference=job;working=true;status="正在本机识别主体…"
        scope.launch {
            var generated: Bitmap?=null
            try {
                withContext(Dispatchers.IO) { generated=job.generate(context.applicationContext,input,selected) }
                view.replace(generated!!);tool=0
                status="主体已生成，请检查边缘；画笔添加，擦除修边"
            } catch(e: Exception) { status="识别未完成，可继续手动涂抹：${e.message}" }
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
                onSaved(name);published=true
            } catch(e: Exception) { status="保存失败：${e.message}" }
            finally { snapshot.recycle();working=false;if(!published) File(context.filesDir,name).delete() }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xff101114)).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            TextButton("取消",onClick=onCancel)
            Text(if(subject) "主体蒙版" else "涂抹范围",style=MiuixTheme.textStyles.headline2)
            TextButton("完成",enabled=canvas!=null&&!working,onClick=::save)
        }
        Text(status,style=MiuixTheme.textStyles.body2,modifier=Modifier.padding(vertical=12.dp))
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            canvas?.let { view -> AndroidView(factory={view},update={it.isEnabled=!working;it.erase=tool==1;it.boxMode=tool==2;it.brush=brush;it.invalidate()},modifier=Modifier.fillMaxSize()) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            TextButton("画笔",enabled=!working,onClick={tool=0})
            TextButton("擦除",enabled=!working,onClick={tool=1})
            TextButton("撤销",enabled=!working,onClick={canvas?.undo()})
            TextButton("清空",enabled=!working,onClick={canvas?.clear()})
        }
        if(subject) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            TextButton("框选",enabled=!working,onClick={tool=2;status="拖出包含主体的选框，再点识别框选"})
            TextButton("自动识别",enabled=canvas!=null&&!working,onClick={generate(true)})
            TextButton("识别框选",enabled=canvas!=null&&!working,onClick={generate(false)})
        }
        Text("画笔大小",style=MiuixTheme.textStyles.body2)
        Slider(value=brush,valueRange=.005f.. .1f,onValueChange={brush=it})
    }
}
