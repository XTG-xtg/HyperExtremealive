package com.frederic22.hyperextreme.alive

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.ui.AppToast
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal object DynamicPackDownloads {
    const val github = "https://github.com/XTG-xtg/HyperExtremealive"
    const val quarkShare = "我用夸克网盘给你分享了「HyperExt...live」，点击链接或复制整段内容，打开「夸克网盘APP」即可获取。\n亝五五二另并词哎卡品卡忛\n/~30d93cIJmg~:/\n链接：https://pan.quark.cn/s/d26a84a303fc"
    fun copyQuark(context: Context) {
        val clip=ClipData.newPlainText("动态壁纸资源包",quarkShare)
        // Also hide the share text in Android's system clipboard preview.
        clip.description.extras=PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
    fun githubIntent()=Intent(Intent.ACTION_VIEW,Uri.parse(github))
}

@Composable
internal fun DynamicPackControls(onReady: (Boolean)->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext
    val scope=rememberCoroutineScope()
    val readyCallback by rememberUpdatedState(onReady)
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var downloads by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { readyCallback(withContext(Dispatchers.IO) { DynamicResources.installed(app) }) }
    val contract=remember { object: ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context,input: Array<String>) =
            super.createIntent(context,input).addCategory(Intent.CATEGORY_OPENABLE)
    } }
    val picker=rememberLauncherForActivityResult(contract) { uri ->
        if(uri!=null && !busy) scope.launch {
            busy=true;progress=0;message=""
            try {
                val operation=currentCoroutineContext()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        DynamicResources.importPack(app,input) { percent ->
                            operation.ensureActive()
                            progress=percent
                        }
                    } ?: error("无法读取所选资源包")
                }
                ThumbnailCache.evictAll();LayoutCache.evictAll()
                // Refresh only an already-bound dynamic wallpaper; never activate a component.
                if(DynamicResources.needsPack(Config.variant(app))) EngineRegistry.reload()
                readyCallback(true);message="动态壁纸已导入，可以预览和应用"
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { message=e.message ?: "导入失败，请重新选择完整资源包" }
            finally { busy=false }
        }
    }
    BackHandler(busy) {}
    Column(Modifier.fillMaxWidth().testTag("dynamic-pack-controls").padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton("导入动态壁纸",modifier=Modifier.fillMaxWidth(),enabled=!busy,
                colors=ButtonDefaults.textButtonColorsPrimary(),onClick={picker.launch(arrayOf("application/zip","application/x-zip-compressed","application/octet-stream"))})
            TextButton("去下载",modifier=Modifier.fillMaxWidth(),enabled=!busy,onClick={downloads=true})
        }
        if(message.isNotEmpty()) Text(message,style=MiuixTheme.textStyles.footnote1,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
    WindowDialog(show=busy,title="正在导入动态壁纸",summary="正在校验并保存资源… $progress%",onDismissRequest={}) {}
    WindowDialog(show=downloads,title="选择下载方式",onDismissRequest={downloads=false}) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton("夸克网盘",modifier=Modifier.fillMaxWidth(),onClick={
                downloads=false
                runCatching { DynamicPackDownloads.copyQuark(context) }
                    .onSuccess { AppToast.show(context,"分享内容已复制，请打开夸克网盘") }
                    .onFailure { AppToast.show(context,"复制失败，请重试") }
            })
            TextButton("GitHub",modifier=Modifier.fillMaxWidth(),onClick={
                downloads=false
                runCatching { context.startActivity(DynamicPackDownloads.githubIntent()) }
                    .onFailure { AppToast.show(context,"没有可打开 GitHub 的应用") }
            })
        }
    }
}
