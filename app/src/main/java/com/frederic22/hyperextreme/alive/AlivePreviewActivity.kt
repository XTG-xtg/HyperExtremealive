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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private val PhotoSaver = listSaver<PhotoOptions, Float>(
    save = { listOf(it.lockStyle.toFloat(), it.x, it.y, it.zoom, it.frameX, it.frameY, it.frameSize, it.frameAngle, it.aodOffsetX, it.aodOffsetY) },
    restore = { PhotoOptions(it[0].toInt(), it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9]) })

@Composable
private fun Preview(name: String, initialVariant: Int, initialMode: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val worker = remember { EglWorker(context, true) }
    var variant by rememberSaveable { mutableIntStateOf(initialVariant) }
    var dark by rememberSaveable { mutableStateOf(Config.dark(app)) }
    var clock by rememberSaveable { mutableStateOf(Config.prefs(app).getBoolean("clock", false)) }
    var mode by rememberSaveable { mutableIntStateOf(initialMode) }
    var previous by remember { mutableIntStateOf(initialMode) }
    var demo by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable(stateSaver = PhotoSaver) { mutableStateOf(PhotoOptions.load(app, true)) }
    var revision by remember { mutableIntStateOf(0) }
    var sequence by remember { mutableLongStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var rendered by remember { mutableStateOf(false) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    val isPhoto = variant in 601..602
    val hasPhoto = remember(revision) { AliveSettings.draft(app).isFile || java.io.File(app.filesDir, "alive/photo.png").isFile }
    val currentPhoto by rememberUpdatedState(photo)
    val currentVariant by rememberUpdatedState(variant)
    val currentDark by rememberUpdatedState(dark)
    val currentBusy by rememberUpdatedState(busy)
    val title = if (variant == 601) "圆景" else if (variant == 602) "留光" else name

    fun attach(surface: SurfaceHolder) {
        val rect = surface.surfaceFrame
        worker.attach(surface.surface, rect.width(), rect.height(), currentVariant, currentDark)
        if (currentVariant in 601..602) worker.photoOptions(currentPhoto)
        worker.visible(!currentBusy && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LaunchedEffect(variant, dark, revision) { rendered = false; error = ""; surfaceHolder?.takeIf { it.surface.isValid }?.let { attach(it) } }
    LaunchedEffect(mode) {
        worker.state(EngineRegistry.Snapshot(previous, mode, SystemClock.uptimeMillis(), ++sequence)); previous = mode
    }
    LaunchedEffect(photo) { if (isPhoto) { worker.photoOptions(photo); delay(250); photo.save(app, true) } }
    LaunchedEffect(demo) {
        if (demo) { mode = 0; delay(1800); mode = 1; delay(2000); mode = 2; delay(2000); demo = false }
    }
    LaunchedEffect(worker) {
        while (true) { error = worker.error; rendered = worker.renderedFrames > 0; delay(500) }
    }
    DisposableEffect(owner, worker) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) worker.visible(!currentBusy)
            if (event == Lifecycle.Event.ON_PAUSE) { worker.visible(false); demo = false; currentPhoto.save(app, true) }
        }
        owner.lifecycle.addObserver(observer)
        worker.visible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { owner.lifecycle.removeObserver(observer); worker.close() }
    }
    BackHandler(enabled = busy) { }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) { runCatching { AliveSettings.importPhoto(app, uri) } }
                if (result.isSuccess) { photo = PhotoOptions.load(app, true); revision++; message = "照片已导入草稿" }
                else message = "照片读取失败：${result.exceptionOrNull()?.message}"
            } finally { busy = false; worker.visible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        }
    }
    fun openPanel(value: String) { demo = false; panel = value }

    Scaffold(contentWindowInsets = WindowInsets(0)) { _ ->
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                SurfaceView(ctx).apply { this.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(surface: SurfaceHolder) = Unit
                    override fun surfaceChanged(surface: SurfaceHolder, format: Int, width: Int, height: Int) { surfaceHolder = surface; attach(surface) }
                    override fun surfaceDestroyed(surface: SurfaceHolder) { surfaceHolder = null; worker.detach() }
                }) }
            })
            if (isPhoto && !hasPhoto) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("用一张照片，连接息屏与桌面", color = Color.White)
                TextButton(text = stringResource(R.string.alive_photo), enabled = !busy,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            }
            else if (error.isNotEmpty() || !rendered) Text(if (error.isNotEmpty()) stringResource(R.string.alive_preview_error, error)
                else stringResource(R.string.alive_preview_starting), color = Color.White,
                modifier = Modifier.padding(24.dp).background(Color(0xBB161616)).padding(16.dp))
            val tools = buildList {
                if (isPhoto) {
                    add(AliveEditorTool("照片", AliveToolIcon.PHOTO) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    if (mode == 0) add(AliveEditorTool("位置", AliveToolIcon.POSITION, hasPhoto) { openPanel("position") })
                    add(AliveEditorTool("取景", AliveToolIcon.CROP, hasPhoto) { openPanel("crop") })
                    if (mode == 1) add(AliveEditorTool("纹理", AliveToolIcon.TEXTURE, hasPhoto) { openPanel("texture") })
                }
                add(AliveEditorTool("选项", AliveToolIcon.OPTIONS) { openPanel("options") })
                add(AliveEditorTool(if (demo) "停止" else "演示", if (demo) AliveToolIcon.STOP else AliveToolIcon.PLAY,
                    rendered && error.isEmpty()) { panel = ""; demo = !demo })
            }
            AliveEditorChrome(title = title, mode = mode, busy = busy,
                canApply = rendered && error.isEmpty() && (!isPhoto || hasPhoto),
                message = message.ifEmpty { if (isPhoto) "调整仅保留为草稿，应用后生效" else "预览不会改变正在使用的壁纸" },
                tools = tools, onCancel = { photo.save(app, true); onBack() },
                onApply = { demo = false; confirmation = true }, onMode = { demo = false; mode = it })
        }
    }
    WindowBottomSheet(show = panel.isNotEmpty(), title = when (panel) { "position" -> "息屏布局"; "crop" -> "照片取景"; "texture" -> "锁屏纹理"; else -> "壁纸选项" },
        enableWindowDim = false, onDismissRequest = { panel = "" },
        endAction = { val dismiss = LocalDismissState.current; TextButton(text = "完成", onClick = { dismiss?.invoke() }) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (panel) {
                "position", "crop", "texture" -> {
                    if (panel == "position") AliveSegments(listOf("圆景", "留光"), variant - 601) { variant = 601 + it }
                    PhotoControls(variant, photo, panel, onPreviewMode = { mode = it }) { photo = it }
                }
                "options" -> {
                    if (!isPhoto) SwitchRow(title = stringResource(R.string.alive_dark), summary = stringResource(R.string.alive_dark_note), checked = dark, onCheckedChange = { dark = it })
                    SwitchRow(title = stringResource(R.string.alive_clock), summary = stringResource(R.string.alive_clock_note), checked = clock, onCheckedChange = { clock = it })
                    Text("完整分辨率与原始材质不变。照片转场跟随 vsync；静止相框和静态照片停止重复绘制，圆球稳定自转约 30 帧/秒。", style = MiuixTheme.textStyles.body2)
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
                    busy = true; worker.visible(false); photo.save(app, true)
                    try {
                        val result = withContext(Dispatchers.IO) { runCatching {
                            AliveSettings.apply(app, variant, dark, clock)
                        } }
                        val value = result.getOrNull()
                        val detail = value?.let { "exit=${it.code}\n${it.text}" } ?: result.exceptionOrNull().toString()
                        RootBridge.record(app, "apply", value ?: RootBridge.Result(1, detail))
                        message = when { result.isFailure -> result.exceptionOrNull()?.message.orEmpty()
                            value?.ok() == true -> app.getString(R.string.alive_success)
                            value?.code == 10 -> app.getString(R.string.alive_framework_missing)
                            else -> app.getString(R.string.alive_failed) }
                        AppToast.show(context, message)
                    } finally { busy = false; worker.visible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
                }
            })
        }
    }
}
