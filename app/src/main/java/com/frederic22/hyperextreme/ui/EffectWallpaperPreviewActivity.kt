package com.frederic22.hyperextreme.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frederic22.hyperextreme.alive.*
import com.frederic22.hyperextreme.applyAppSystemBars
import com.frederic22.hyperextreme.requestHighRefreshRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.window.WindowBottomSheet

internal enum class EffectWallpaperKind(val title: String) { SPATIAL("空间壁纸"), RASTER("光栅壁纸"), PARALLAX("视差壁纸") }

/** This activity returns a draft. Only its caller owns wallpaper activation and saving. */
internal data class EffectPreviewRequest(val kind: EffectWallpaperKind, val key: String) {
    fun intent(context: Context) = Intent(context, EffectWallpaperPreviewActivity::class.java)
        .putExtra("kind", kind.name).putExtra("key", key)
    fun result(brightness: Float) = Intent().putExtra("kind", kind.name).putExtra("key", key)
        .putExtra("brightness", AodBrightness.normalize(brightness))
    companion object {
        fun parse(intent: Intent?): EffectPreviewRequest? {
            val kind = EffectWallpaperKind.entries.firstOrNull { it.name == intent?.getStringExtra("kind") } ?: return null
            val key = intent?.getStringExtra("key").orEmpty()
            if (kind != EffectWallpaperKind.PARALLAX && !key.matches(Regex("[A-Za-z0-9_-]{1,100}"))) return null
            return EffectPreviewRequest(kind, key)
        }
        fun brightness(resultCode: Int, intent: Intent?): Float? = if(resultCode != Activity.RESULT_OK) null
            else intent?.takeIf { it.hasExtra("brightness") }?.getFloatExtra("brightness", Float.NaN)
                ?.takeIf { it.isFinite() && it in AodBrightness.MIN..AodBrightness.MAX }
    }
}

class EffectWallpaperPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppSystemBars(true)
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
        val request = EffectPreviewRequest.parse(intent) ?: run { finish(); return }
        setContent {
            MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }, textStyles = HyperTextStyles) {
                EffectWallpaperPreview(request, onCancel = { finish() }, onApply = {
                    setResult(Activity.RESULT_OK, request.result(it)); finish()
                })
            }
        }
    }
}

@Composable
private fun EffectWallpaperPreview(request: EffectPreviewRequest, onCancel: () -> Unit, onApply: (Float) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var source by remember(request) { mutableStateOf<EffectPreviewSource?>(null) }
    var error by remember(request) { mutableStateOf("") }
    var ready by remember(request) { mutableStateOf(false) }
    var view by remember { mutableStateOf<EffectPreviewView?>(null) }
    LaunchedEffect(request) {
        runCatching { withContext(Dispatchers.IO) { EffectPreviewSource.load(context, request) } }
            .onSuccess { source = it }.onFailure {
                if(it is CancellationException) throw it
                error = it.message ?: "图片读取失败"
            }
    }
    DisposableEffect(owner, view) {
        val current = view
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_RESUME) current?.resumePreview()
            if(event == Lifecycle.Event.ON_PAUSE) current?.pausePreview()
        }
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) current?.resumePreview()
        onDispose { owner.lifecycle.removeObserver(observer); current?.pausePreview() }
    }
    EffectPreviewEditor(request.kind.title, AodBrightness.load(context), ready, error,
        onCancel = onCancel, onApply = onApply,
        preview = { mode, weight, brightness, _ ->
            val data = source
            if(data != null) AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                EffectPreviewView(ctx, data) { result ->
                    ready = result == null; error = result.orEmpty()
                }.also { view = it }
            }, update = { it.configure(mode, weight, brightness) })
        })
}

/** Shared Alive chrome, isolated from data loading so layout and draft semantics are testable. */
@Composable
internal fun EffectPreviewEditor(
    title: String, initialBrightness: Float, ready: Boolean, error: String,
    onCancel: () -> Unit, onApply: (Float) -> Unit,
    preview: @Composable (mode: Int, aodWeight: Float, brightness: Float, demo: Boolean) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(1) }
    var brightness by rememberSaveable { mutableFloatStateOf(AodBrightness.normalize(initialBrightness)) }
    var panel by rememberSaveable { mutableStateOf("") }
    var demo by remember { mutableStateOf(false) }
    var immersiveHidden by rememberSaveable { mutableStateOf(false) }
    var showClockGuide by rememberSaveable { mutableStateOf(false) }
    val aod = remember { Animatable(0f) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_PAUSE) demo = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(mode) { aod.animateTo(if(mode == 0) 1f else 0f, tween(700)) }
    LaunchedEffect(demo) {
        if(demo) { mode = 0; delay(1800); mode = 1; delay(1800); mode = 2; delay(1800); demo = false }
    }
    Scaffold(contentWindowInsets = WindowInsets(0)) { _ ->
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("effect-preview-editor"), contentAlignment = Alignment.Center) {
            preview(mode, aod.value, brightness, demo)
            if (ready && error.isEmpty() && showClockGuide) AliveSceneOverlay(mode)
            if(!ready) AlivePreviewStatusCard(
                text = if(error.isEmpty()) "正在加载预览…" else error,
                loading = error.isEmpty(),
            )
            AliveEditorChrome(
                title = title,
                mode = mode,
                busy = false,
                canApply = ready && error.isEmpty(),
                message = if (showClockGuide) "示意时钟 · 非系统真实样式"
                    else if (title == "视差壁纸") "预览背景效果 · 系统主体以实机为准"
                    else "倾斜手机或拖动查看效果",
                tools = listOf(
                    AliveEditorTool("息屏亮度", AliveToolIcon.SPARK, ready) { demo = false; immersiveHidden = false; mode = 0; panel = "brightness" },
                    AliveEditorTool("选项", AliveToolIcon.OPTIONS, ready) { demo = false; immersiveHidden = false; panel = "options" },
                    AliveEditorTool(if(demo) "停止" else "演示", if(demo) AliveToolIcon.STOP else AliveToolIcon.PLAY, ready) { immersiveHidden = false; demo = !demo },
                ),
                onCancel = onCancel,
                onApply = { onApply(brightness) },
                onMode = { demo = false; mode = it },
                subtitle = listOf("息屏预览", "锁屏预览", "桌面预览")[mode.coerceIn(0, 2)],
                activeDemo = demo,
                onStopDemo = { demo = false },
                immersiveHidden = immersiveHidden,
                onToggleImmersive = { immersiveHidden = !immersiveHidden },
                activeTools = buildSet {
                    if (brightness > 0f) add("息屏亮度")
                    if (showClockGuide) add("选项")
                },
            )
        }
    }
    WindowBottomSheet(show = panel.isNotEmpty(), title = if (panel == "options") "预览选项" else "息屏亮度", enableWindowDim = false,
        onDismissRequest = { panel = "" }, endAction = {
            val dismiss = LocalDismissState.current
            TextButton("完成", onClick = { dismiss?.invoke() })
        }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
            .testTag(if (panel == "options") "effect-options-panel" else "effect-brightness-panel"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (panel == "options") {
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    SwitchRow(
                        title = "显示示意时钟",
                        summary = "仅辅助检查布局，不是真实系统时钟样式",
                        checked = showClockGuide,
                        onCheckedChange = { showClockGuide = it },
                    )
                }
                Text("系统自定义字体、小组件和通知由系统绘制，此处不伪造。", style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    AodBrightnessControl(brightness) { brightness = it; mode = 0 }
                }
                Text("应用后保存，取消预览不改变当前亮度。", style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
