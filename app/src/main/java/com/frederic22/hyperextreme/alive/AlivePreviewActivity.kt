package com.frederic22.hyperextreme.alive

import android.os.Bundle
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frederic22.hyperextreme.R
import com.frederic22.hyperextreme.applyAppSystemBars
import com.frederic22.hyperextreme.requestHighRefreshRate
import com.frederic22.hyperextreme.ui.AppToast
import com.frederic22.hyperextreme.ui.HyperTextStyles
import com.frederic22.hyperextreme.ui.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/** One scene editor; no live registry registration, and no applied writes before confirmation. */
class AlivePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppSystemBars(true)
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
        val variant = intent.getIntExtra("variant", Config.variant(this))
        val name = intent.getStringExtra("name") ?: "Alive"
        val mode = intent.getIntExtra("mode", 0).coerceIn(0, 2)
        setContent {
            val controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark) }
            MiuixTheme(controller = controller, textStyles = HyperTextStyles) { Preview(name, variant, mode, onBack = { finish() }) }
        }
    }
}

private val PhotoSaver = Saver<PhotoOptions, Bundle>(save = { it.bundle() }, restore = { PhotoOptions.fromBundle(it) })
private val VivoSaver = Saver<VivoOptions, String>(save = { it.json() }, restore = { VivoOptions.parse(it) })

private val RenderSaver = listSaver<AliveRenderOptions, Float>(
    save = { listOf(if(it.keepLock) 1f else 0f, if(it.continuousAod) 1f else 0f, if(it.continuousHome) 1f else 0f,
        if(it.followClock) 1f else 0f, if(it.burnIn) 1f else 0f, if(it.stereoAod) 1f else 0f, it.sensitivity.toFloat(), it.brightness) },
    restore = { AliveRenderOptions(it[0] == 1f, it[1] == 1f, it[2] == 1f, it[3] == 1f, it[4] == 1f, it[5] == 1f, it[6].toInt(), it[7]) })

@Composable
private fun Preview(name: String, initialVariant: Int, initialMode: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val worker = remember { EglWorker(context, true) }
    val transition = remember { PreviewSceneTransition() }
    var expectedGeneration by remember { mutableLongStateOf(-1) }
    var attachedVivoKey by remember { mutableStateOf<String?>(null) }
    var variant by rememberSaveable { mutableIntStateOf(initialVariant) }
    var sceneName by rememberSaveable { mutableStateOf(name) }
    var dark by rememberSaveable { mutableStateOf(Config.dark(app)) }
    var clock by rememberSaveable { mutableStateOf(Config.prefs(app).getBoolean("clock", false)) }
    var mode by rememberSaveable { mutableIntStateOf(initialMode) }
    var previous by remember { mutableIntStateOf(initialMode) }
    var demo by remember { mutableStateOf(false) }
    var showClockGuide by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable(stateSaver = PhotoSaver) { mutableStateOf(PhotoOptions.load(app, true)) }
    var vivo by rememberSaveable(stateSaver = VivoSaver) { mutableStateOf(VivoOptions.load(app, if(initialVariant in 701..703) initialVariant else 701, true)) }
    var vivoImport by rememberSaveable { mutableIntStateOf(VivoMediaSelection.WALLPAPER) }
    var maskArea by rememberSaveable { mutableIntStateOf(2) }
    var renderOptions by rememberSaveable(stateSaver = RenderSaver) { mutableStateOf(AliveRenderOptions.load(app)) }
    var revision by remember { mutableIntStateOf(0) }
    var sequence by remember { mutableLongStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var rendered by remember { mutableStateOf(false) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var importSlot by rememberSaveable { mutableIntStateOf(0) }
    var cropTarget by rememberSaveable { mutableIntStateOf(0) }
    val isPhoto = variant in 601..606
    val isVivo = variant in 701..703
    val hasPhoto = remember(revision, photo.lockPhoto) { PhotoMedia.resolve(app, photo, true, 0).isFile }
    val currentPhoto by rememberUpdatedState(photo)
    val currentVivo by rememberUpdatedState(vivo)
    val currentVariant by rememberUpdatedState(variant)
    val currentDark by rememberUpdatedState(dark)
    val currentBusy by rememberUpdatedState(busy)
    val currentSwitching by rememberUpdatedState(transition.running)
    val currentPanel by rememberUpdatedState(panel)
    val currentOptions by rememberUpdatedState(renderOptions)
    val title = if(isPhoto) listOf("圆景", "留光", "星月", "轻启·照片", "山脉", "银河")[variant-601] else sceneName

    fun attach(surface: SurfaceHolder) {
        val rect = surface.surfaceFrame
        worker.options(currentOptions)
        if (currentVariant in 601..606) worker.photoOptions(currentPhoto)
        if (currentVariant in 701..703) {
            worker.vivoOptions(currentVivo)
            attachedVivoKey = previewVivoSceneKey(currentVivo)
        }
        expectedGeneration = worker.attach(surface.surface, rect.width(), rect.height(), currentVariant, currentDark)
        worker.visible(currentPanel != "mask" && !currentBusy && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LaunchedEffect(variant, dark, revision, renderOptions.keepLock, photo.lockPhoto, photo.homePhoto, photo.framePhoto, photo.followLock) { rendered = false; error = ""; surfaceHolder?.takeIf { it.surface.isValid }?.let { attach(it) } }
    LaunchedEffect(renderOptions) { worker.options(renderOptions) }
    LaunchedEffect(panel) { worker.visible(panel!="mask" && !busy && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    LaunchedEffect(mode) {
        worker.state(EngineRegistry.Snapshot(previous, mode, SystemClock.uptimeMillis(), ++sequence)); previous = mode
    }
    LaunchedEffect(variant, photo) { if (isPhoto) { worker.photoOptions(photo); delay(250); photo.save(app, true) } }
    val vivoKey = previewVivoSceneKey(vivo)
    LaunchedEffect(variant, vivoKey) { if(isVivo) { delay(180); vivo.saveDraft(app); if(attachedVivoKey != vivoKey) revision++ } }
    LaunchedEffect(variant, vivo) { if(isVivo) { worker.vivoOptions(vivo); delay(250); vivo.saveDraft(app) } }
    LaunchedEffect(demo) {
        if (demo) { mode = 0; delay(1800); mode = 1; delay(2000); mode = 2; delay(2000); demo = false }
    }
    LaunchedEffect(worker) {
        while (true) {
            error = if(worker.activeGeneration == expectedGeneration) worker.error else ""
            rendered = expectedGeneration > 0 && worker.renderedGeneration == expectedGeneration && error.isEmpty()
            delay(50)
        }
    }
    DisposableEffect(owner, worker) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) worker.visible(currentPanel != "mask" && !currentBusy)
            if (event == Lifecycle.Event.ON_PAUSE) { worker.visible(false); demo = false; currentPhoto.save(app, true); if(currentVariant in 701..703) currentVivo.saveDraft(app) }
        }
        owner.lifecycle.addObserver(observer)
        worker.visible(currentPanel != "mask" && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { owner.lifecycle.removeObserver(observer); worker.close() }
    }
    BackHandler(enabled = busy || transition.running) { }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) { runCatching { AliveSettings.importPhoto(app, uri, importSlot, currentPhoto) } }
                if (result.isSuccess) { photo = result.getOrThrow(); revision++; message = "照片已导入草稿" }
                else message = "照片读取失败：${result.exceptionOrNull()?.message}"
            } finally { busy = false; worker.visible(currentPanel != "mask" && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        }
    }
    val vivoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri != null) scope.launch {
            busy = true; worker.visible(false)
            try {
                val result = withContext(Dispatchers.IO) { runCatching {
                    val selection = VivoMediaSelection(app, currentVivo)
                    try {
                        selection.importUris(VivoEngineRuntime.prepare(app).get(20, java.util.concurrent.TimeUnit.SECONDS), vivoImport, listOf(uri))
                        selection.result.validateFiles(app); selection.result.saveDraft(app); selection.result
                    } catch(e: Exception) { selection.discard(); throw e }
                } }
                result.onSuccess { vivo = it; message = "素材已导入草稿" }.onFailure { message = "导入失败：${it.message}" }
            } finally { busy = false; worker.visible(currentPanel != "mask" && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        }
    }
    fun pickVivo(kind: Int) {
        vivoImport = kind; vivo.saveDraft(app)
        vivoPicker.launch(PickVisualMediaRequest(if(kind in VivoMediaSelection.SLOT_VIDEO..VivoMediaSelection.SLOT_VIDEO+2) ActivityResultContracts.PickVisualMedia.VideoOnly else ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun pickPhoto(slot: Int) { importSlot = slot; photo.save(app, true); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    fun openPanel(value: String) {
        demo = false; panel = value
        if(value == "crop") { cropTarget = photoSourceSlot(mode,variant,photo); mode = if(cropTarget == 2) 0 else cropTarget + 1 }
    }
    fun selectScene(scene: AliveCatalog.Scene) {
        if(busy || transition.running) return
        if(scene.id == variant && (!isPhoto || photo.aodMode == 0)) { panel = ""; return }
        demo = false; panel = ""
        scope.launch {
            try {
                transition.switch {
                    photo.save(app, true)
                    val nextVivo = switchPreviewVivo(app, variant, scene.id, vivo)
                    val oldGeneration = expectedGeneration
                    message = ""; rendered = false; error = ""
                    worker.state(EngineRegistry.Snapshot(mode, mode, SystemClock.uptimeMillis(), ++sequence))
                    previous = mode; vivo = nextVivo; variant = scene.id; sceneName = scene.name
                    if(scene.id in 601..606) photo = photo.edit(15, 0f)
                    revision++
                    // SurfaceView buffers are separate from Compose. Do not reveal a previous engine's frame.
                    val ready = withTimeoutOrNull(30_000) {
                        while(expectedGeneration <= oldGeneration ||
                            (worker.renderedGeneration != expectedGeneration &&
                                !(worker.activeGeneration == expectedGeneration && worker.error.isNotEmpty()))) delay(16)
                        true
                    }
                    if(ready == null) message = "预览加载较慢，请稍候或重新选择样式"
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { AppToast.show(context, "切换失败：${e.message}", long = true) }
        }
    }
    fun option(key: Int, value: Float) {
        val o = renderOptions
        renderOptions = AliveRenderOptions(if(key == 0) value == 1f else o.keepLock,
            if(key == 1) value == 1f else o.continuousAod, if(key == 2) value == 1f else o.continuousHome,
            if(key == 3) value == 1f else o.followClock, if(key == 4) value == 1f else o.burnIn,
            if(key == 5) value == 1f else o.stereoAod, if(key == 6) value.toInt() else o.sensitivity,
            if(key == 7) value else o.brightness)
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { _ ->
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                SurfaceView(ctx).apply { this.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(surface: SurfaceHolder) = Unit
                    override fun surfaceChanged(surface: SurfaceHolder, format: Int, width: Int, height: Int) { surfaceHolder = surface; attach(surface) }
                    override fun surfaceDestroyed(surface: SurfaceHolder) { surfaceHolder = null; expectedGeneration = -1; worker.detach() }
                }); setOnTouchListener { view, event ->
                    if(currentVariant in 701..703 && !currentBusy && !currentSwitching) { worker.touch(event.actionMasked,event.x/view.width.coerceAtLeast(1),event.y/view.height.coerceAtLeast(1)); true } else false
                } }
            })
            Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black, alpha = transition.shade.value) }
            if(rendered && !transition.running && error.isEmpty() && showClockGuide && panel != "crop" && panel != "mask") AliveSceneOverlay(mode, variant = variant)
            if (!transition.running && isPhoto && !hasPhoto) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("用一张照片，连接息屏与桌面", color = Color.White)
                TextButton(text = stringResource(R.string.alive_photo), enabled = !busy,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { pickPhoto(0) })
            }
            else if (!transition.running && (error.isNotEmpty() || !rendered)) Text(if (error.isNotEmpty()) stringResource(R.string.alive_preview_error, error)
                else stringResource(R.string.alive_preview_starting), color = Color.White,
                modifier = Modifier.padding(24.dp).background(Color(0xBB161616)).padding(16.dp))
            val tools = buildList {
                add(AliveEditorTool("样式", AliveToolIcon.STYLE) { openPanel("styles") })
                if (isPhoto) {
                    add(AliveEditorTool("照片", AliveToolIcon.PHOTO) {
                        openPanel("photos")
                    })
                    if (mode == 0) add(AliveEditorTool("位置", AliveToolIcon.POSITION, hasPhoto) { openPanel("position") })
                    add(AliveEditorTool("取景", AliveToolIcon.CROP, hasPhoto) { openPanel("crop") })
                    if (mode != 0) add(AliveEditorTool("纹理", AliveToolIcon.TEXTURE, hasPhoto) { openPanel("texture") })
                }
                if(isVivo) add(AliveEditorTool("效果与素材", AliveToolIcon.TEXTURE) { openPanel("vivo") })
                add(AliveEditorTool("选项", AliveToolIcon.OPTIONS) { openPanel("options") })
                add(AliveEditorTool(if (demo) "停止" else "演示", if (demo) AliveToolIcon.STOP else AliveToolIcon.PLAY,
                    rendered && error.isEmpty()) { panel = ""; demo = !demo })
            }
            if(panel == "mask") VivoMaskEditor(vivo, subject=maskArea!=1,
                onSaved={ file -> vivo=VivoOptions.parse(vivo.json()).apply { if(maskArea==1) paint=file else subject=file;area=maskArea };vivo.saveDraft(app);panel="vivo" },
                onCancel={panel="vivo"})
            else if(panel == "crop") PhotoCropEditor(variant, photo, cropTarget,
                onTarget = { cropTarget = it; mode = if(it == 2) 0 else it + 1 },
                onChange = { photo = it }, onDone = { panel = "" })
            else AliveEditorChrome(title = title, mode = mode, busy = busy || transition.running,
                canApply = rendered && error.isEmpty() && (!isPhoto || hasPhoto),
                message = message.ifEmpty { if(showClockGuide) "示意时钟 · 非系统真实样式" else "仅预览壁纸 · 时钟以系统实际样式为准" },
                tools = tools, onCancel = { photo.save(app, true); if(isVivo) vivo.saveDraft(app); onBack() },
                onApply = { demo = false; confirmation = true }, onMode = { demo = false; mode = it })
        }
    }
    WindowBottomSheet(show = panel.isNotEmpty() && panel != "crop" && panel != "mask", title = when (panel) { "styles" -> "壁纸样式"; "position" -> "息屏布局"; "photos" -> photoSourceTitle(photoSourceSlot(mode,variant,photo)); "texture" -> if(mode == 2) "桌面纹理" else "锁屏纹理"; "vivo" -> "Vivo 效果与素材"; else -> "壁纸选项" },
        enableWindowDim = false, onDismissRequest = { panel = "" },
        endAction = { val dismiss = LocalDismissState.current; TextButton(text = "完成", onClick = { dismiss?.invoke() }) }) {
        if(panel == "styles") AliveWallpaperPicker(variant, photo, !busy, ::selectScene)
        else Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (panel) {
                "vivo" -> VivoControls(vivo, onChange = { vivo = it }, onImport = ::pickVivo, onMask={maskArea=it;panel="mask"}, onSwap = { a,b ->
                    if(!busy) scope.launch {
                        busy=true;worker.visible(false)
                        try {
                            val result=withContext(Dispatchers.IO) { runCatching {
                                val selection=VivoMediaSelection(app,currentVivo)
                                try { selection.swapSlots(VivoEngineRuntime.prepare(app).get(20,java.util.concurrent.TimeUnit.SECONDS),a,b); selection.result.saveDraft(app); selection.result }
                                catch(e:Exception){selection.discard();throw e}
                            } }
                            result.onSuccess { vivo=it }.onFailure { message="交换失败：${it.message}" }
                        } finally { busy=false;worker.visible(currentPanel != "mask" && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
                    }
                })
                "photos" -> PhotoSourcePanel(photoSourceSlot(mode,variant,photo),photo,!busy,onChange={photo=it},onPick=::pickPhoto)
                "position", "crop", "texture" -> {
                    PhotoControls(variant, photo, panel, mode, onPreviewMode = { mode = it }) { photo = it }
                }
                "options" -> {
                    if(isPhoto) {
                        Text("照片息屏样式", style = MiuixTheme.textStyles.body2)
                        AliveSegments(listOf("动态造型", "全屏照片", "仅系统时钟"), photo.aodMode) { photo = photo.edit(15, it.toFloat()); mode = 0 }
                        Text("全屏照片会增加息屏发光面积；仅系统时钟会将壁纸置黑，不关闭系统 AOD。", style = MiuixTheme.textStyles.body2)
                    }
                    if (!isPhoto) SwitchRow(title = stringResource(R.string.alive_dark), summary = stringResource(R.string.alive_dark_note), checked = dark, onCheckedChange = { dark = it })
                    Text("息屏图案亮度 · ${"%.0f".format(renderOptions.brightness * 100)}%", style = MiuixTheme.textStyles.body2)
                    Slider(value = renderOptions.brightness, valueRange = .25f..3f, onValueChange = { mode = 0; option(7, it) })
                    AliveSegments(listOf("较暗 50%", "原版 100%", "明亮 200%"), when(renderOptions.brightness) { .5f -> 0; 1f -> 1; 2f -> 2; else -> -1 }) {
                        mode = 0; option(7, floatArrayOf(.5f, 1f, 2f)[it])
                    }
                    Text("调整时自动切到息屏预览，应用后影响实际壁纸。100% 为原版；默认 130%。不修改系统面板亮度、息屏时长或指纹高亮；纯黑和已达纯白的像素无法继续提亮。", style = MiuixTheme.textStyles.body2)
                    if(variant < 201 || isPhoto && photo.aodMode == 0) {
                        SwitchRow(title = "跟随系统息屏时钟", summary = "保留系统时钟，图案跟随其真实布局和防烧屏移动", checked = renderOptions.followClock, onCheckedChange = { option(3, if(it) 1f else 0f) })
                        SwitchRow(title = "防烧屏位移兜底", summary = "未读取到系统时钟位置时，每分钟小幅移动图案；不会重复叠加系统位移", checked = renderOptions.burnIn, onCheckedChange = { option(4, if(it) 1f else 0f) })
                    } else Text("此类为完整画面或背景构图，保留系统时钟及其自身防烧屏位移；当前不对整幅壁纸叠加时钟锚点位移。",style=MiuixTheme.textStyles.body2)
                    SwitchRow(title = "息屏持续动画", summary = "关闭后保留转场，稳定后停绘；只在可见时运行", checked = renderOptions.continuousAod, onCheckedChange = { option(1, if(it) 1f else 0f) })
                    if(!isPhoto) {
                        SwitchRow(title = "桌面持续动画", checked = renderOptions.continuousHome, onCheckedChange = { option(2, if(it) 1f else 0f) })
                        if(variant < 301 || variant in 501..503) SwitchRow(title = "桌面保留锁屏形态", checked = renderOptions.keepLock, onCheckedChange = { option(0, if(it) 1f else 0f) })
                    }
                    if(variant in 501..503) {
                        Text("传感器灵敏度 · ${renderOptions.sensitivity}", style = MiuixTheme.textStyles.body2)
                        Slider(value = renderOptions.sensitivity.toFloat(), valueRange = -50f..50f, onValueChange = { option(6, it) })
                        SwitchRow(title = "息屏陀螺仪动画", checked = renderOptions.stereoAod, onCheckedChange = { option(5, if(it) 1f else 0f) })
                    }
                    if(clock) SwitchRow(title = "关闭旧版额外时钟", summary = "旧版叠加时钟可能与系统时钟重叠，建议关闭", checked = clock, onCheckedChange = { clock = it })
                    SwitchRow(title = "显示示意时钟", summary = "仅辅助检查布局，不是真实系统时钟样式；取景时始终隐藏", checked = showClockGuide, onCheckedChange = { showClockGuide = it })
                    Text("系统自定义字体、小组件和通知由系统绘制，此处不伪造。关闭示意时钟不会关闭真实锁屏或息屏时钟。", style = MiuixTheme.textStyles.body2)
                }
            }
        }
    }
    WindowDialog(show = confirmation, title = stringResource(R.string.alive_confirm_title), summary = stringResource(R.string.alive_confirm_note),
        onDismissRequest = { confirmation = false }) {
        val dismiss = LocalDismissState.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = stringResource(R.string.alive_cancel), modifier = Modifier.weight(1f), onClick = { confirmation = false; dismiss?.invoke() })
            TextButton(text = stringResource(R.string.alive_confirm), modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                confirmation = false; dismiss?.invoke()
                if (!busy) scope.launch {
                    busy = true; worker.visible(false); photo.save(app, true); if(isVivo) vivo.saveDraft(app)
                    try {
                        val result = withContext(Dispatchers.IO) { runCatching {
                            AliveSettings.apply(app, variant, dark, clock, renderOptions)
                        } }
                        val value = result.getOrNull()
                        val detail = value?.let { "exit=${it.code}\n${it.text}" } ?: result.exceptionOrNull().toString()
                        RootBridge.record(app, "apply", value ?: RootBridge.Result(1, detail))
                        message = when { result.isFailure -> result.exceptionOrNull()?.message.orEmpty()
                            value?.ok() == true -> app.getString(R.string.alive_success)
                            value?.code == 10 -> app.getString(R.string.alive_framework_missing)
                            else -> app.getString(R.string.alive_failed) }
                        AppToast.show(context, message)
                    } finally { busy = false; worker.visible(currentPanel != "mask" && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
                }
            })
        }
    }
}
