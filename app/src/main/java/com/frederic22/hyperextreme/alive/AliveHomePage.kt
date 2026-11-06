package com.frederic22.hyperextreme.alive

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frederic22.hyperextreme.applyAppSystemBars
import com.frederic22.hyperextreme.requestHighRefreshRate
import com.frederic22.hyperextreme.resolveAppDark
import com.frederic22.hyperextreme.data.ActivationState
import com.frederic22.hyperextreme.data.MaskPrefs
import com.frederic22.hyperextreme.data.WallpaperFeatures
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy.Feature
import com.frederic22.hyperextreme.ui.AppToast
import com.frederic22.hyperextreme.ui.HyperTextStyles
import com.frederic22.hyperextreme.ui.SwitchRow
import com.frederic22.hyperextreme.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

class AliveLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppSystemBars(resolveAppDark(this, MaskPrefs(this).themeMode))
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
        setContent {
            val controller = remember { ThemeController(ThemeMode.of(MaskPrefs(this).themeMode).toColorSchemeMode()) }
            MiuixTheme(controller = controller, textStyles = HyperTextStyles) { AliveLibrary { finish() } }
        }
    }
}

internal data class AliveHomeModel(
    val name: String = "启航",
    val running: Boolean = false,
    val frameworkConnected: Boolean = false,
    val busy: Boolean = true,
    val frames: List<ImageBitmap> = emptyList(),
    val previewError: String = "",
    val statusError: String = "",
    val aspect: Float = 2.2f,
    val variant: Int = 1,
    val photoShapes: List<ImageBitmap> = emptyList(),
    val photoShapesError: String = "",
)

internal object LayoutCache : LruCache<String, List<ImageBitmap>>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: List<ImageBitmap>) = value.sumOf { it.width * it.height * 4 }
}

@Composable
internal fun AliveDetail(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var variant by remember { mutableIntStateOf(Config.variant(app)) }
    var model by remember { mutableStateOf(AliveHomeModel()) }
    var confirmation by remember { mutableStateOf<Boolean?>(null) }
    var switching by remember { mutableStateOf(false) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val width = configuration.screenWidthDp.coerceAtLeast(1)
    val height = configuration.screenHeightDp.coerceAtLeast(1)
    val aspect = (maxOf(width, height).toFloat() / minOf(width, height)).coerceIn(1.6f, 2.6f)

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revision) {
        val status = withContext(Dispatchers.IO) { runCatching { WallpaperFeatures.isBound(app, Feature.ALIVE) } }
        variant = Config.variant(app)
        val name = withContext(Dispatchers.IO) {
            runCatching { AliveCatalog.load(app).firstOrNull { it.id == variant }?.name }.getOrNull() ?: "Alive"
        }
        model = model.copy(name = name, running = status.getOrDefault(model.running), busy = false,
            frameworkConnected = ActivationState.frameworkConnected,
            statusError = status.exceptionOrNull()?.message.orEmpty(), aspect = aspect, variant = variant)
    }
    val activePhoto = PhotoMedia.resolve(app, PhotoOptions.load(app, false), false, 0)
    val layoutKey = "$variant:${Config.dark(app)}:${Config.prefs(app).getLong("revision", 0)}:" +
        "${activePhoto.lastModified()}:${activePhoto.length()}:$aspect"
    val tileOptions = remember(revision) { PhotoOptions.load(app, true) }
    val tileKey = PhotoStyleThumbnails.key(app, tileOptions)
    DisposableEffect(tileKey, diagnostics, revision) {
        var disposed = false
        var job: PhotoStyleThumbnails? = null
        if(!diagnostics) {
            val cached = LayoutCache.get(tileKey)
            if(cached != null) model = model.copy(photoShapes=cached,photoShapesError="")
            else {
                model=model.copy(photoShapes=emptyList(),photoShapesError="")
                job=PhotoStyleThumbnails(app,tileOptions) { bitmaps,error ->
                    Handler(Looper.getMainLooper()).post {
                        if(disposed) bitmaps.forEach { it.recycle() }
                        else {
                            val shapes=bitmaps.map { it.asImageBitmap() }
                            if(shapes.isNotEmpty()) LayoutCache.put(tileKey,shapes)
                            model=model.copy(photoShapes=shapes,photoShapesError=error)
                        }
                    }
                }
            }
        }
        onDispose { disposed=true;job?.close() }
    }
    DisposableEffect(layoutKey, diagnostics) {
        var disposed = false
        var worker: AliveLayoutFrames? = null
        if (!diagnostics) {
            val cached = LayoutCache.get(layoutKey)
            if (cached != null) model = model.copy(frames = cached, previewError = "")
            else {
                model = model.copy(frames = emptyList(), previewError = "")
                worker = AliveLayoutFrames(app, variant, Config.dark(app), 288, (288 * aspect).roundToInt()) { bitmaps, error ->
                    Handler(Looper.getMainLooper()).post {
                        if (!disposed) {
                            val images = bitmaps.map { it.asImageBitmap() }
                            if (images.isNotEmpty()) LayoutCache.put(layoutKey, images)
                            model = model.copy(frames = images, previewError = error)
                        }
                    }
                }
            }
        }
        onDispose { disposed = true; worker?.close() }
    }
    fun edit(id: Int, mode: Int = 0) {
        val name = when (id) { 601 -> "圆景"; 602 -> "留光"; 603 -> "星月"; 604 -> "轻启·照片"; 605 -> "山脉"; 606 -> "银河"; else -> model.name }
        context.startActivity(Intent(context, AlivePreviewActivity::class.java)
            .putExtra("variant", id).putExtra("name", name).putExtra("mode", mode))
    }
    BackHandler(enabled = diagnostics || switching) { if (!switching) diagnostics = false }
    if (diagnostics) {
        Scaffold(topBar = { SmallTopAppBar(title = "状态与诊断", navigationIcon = { AliveBackButton { diagnostics = false } }) }) { padding ->
            Box(Modifier.padding(padding)) { AliveDiagnostics() }
        }
    } else {
        AliveHomeContent(model.copy(busy = model.busy || switching), onBack,
            onDiagnostics = { diagnostics = true }, onMode = { edit(variant, it) },
            onPhoto = { edit(it) }, onLibrary = { context.startActivity(Intent(context, AliveLibraryActivity::class.java)) },
            onToggle = { confirmation = it }, onRetry = { revision++ })
    }
    WindowDialog(show = confirmation != null,
        title = if (confirmation == false) "关闭 Alive 壁纸？" else "启用 Alive 壁纸？",
        summary = if (confirmation == false)
            "将卸下正在使用的 Alive 壁纸，对应位置恢复系统默认壁纸。照片和参数会保留，下次可直接开启；不会修改其他壁纸组件。"
        else "将上次应用的布局设为桌面和锁屏壁纸（首次为默认启航）。不会应用未确认的照片草稿；请先关闭空间壁纸和视差壁纸。",
        onDismissRequest = { confirmation = null }) {
        val dismiss = LocalDismissState.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", modifier = Modifier.weight(1f), onClick = { confirmation = null; dismiss?.invoke() })
            TextButton(if (confirmation == false) "关闭壁纸" else "确认开启", modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                    val enabled = confirmation ?: return@TextButton
                    confirmation = null; dismiss?.invoke(); switching = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { runCatching { AliveSettings.setEnabled(app, enabled) } }
                            val value = result.getOrNull()
                            val message = result.exceptionOrNull()?.message ?: if (value?.ok() == true)
                                if (enabled) "Alive 已开启，桌面和锁屏均已回读确认" else "Alive 已关闭，照片与设置已保留"
                            else value?.text ?: "操作失败，请到诊断页查看"
                            AppToast.show(context, message, long = true)
                        } finally { switching = false; revision++ }
                    }
                })
        }
    }
}

@Composable
internal fun AliveBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回", modifier = Modifier.size(24.dp))
    }
}

/** Also used by the isolated screenshot harness; this view performs no privileged operations. */
@Composable
internal fun AliveHomeContent(
    model: AliveHomeModel,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onMode: (Int) -> Unit,
    onPhoto: (Int) -> Unit,
    onLibrary: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = aliveUiColors()
    Scaffold(topBar = {
        SmallTopAppBar(title = "", color = colors.page, navigationIcon = { AliveBackButton(onBack) }, actions = {
            IconButton(onClick = onDiagnostics, enabled = !model.busy, modifier = Modifier.size(48.dp)) {
                Icon(MiuixIcons.More, "管理与诊断", Modifier.size(24.dp), tint = colors.text)
            }
        })
    }) { insets ->
        Box(Modifier.fillMaxSize().background(colors.page).padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                Text("Alive 壁纸", fontSize = 26.sp, fontWeight = FontWeight.Normal,
                    color = colors.text, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
                FlowRow(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AliveStatusDot(if (model.frameworkConnected) "LSPosed 已连接" else "LSPosed 未连接", model.frameworkConnected)
                    AliveStatusDot(when { model.busy -> "Alive 检测中"; model.statusError.isNotEmpty() -> "Alive 状态未知"
                        model.running -> "Alive 已启用"; else -> "Alive 未启用" },
                        if (model.busy || model.statusError.isNotEmpty()) null else model.running)
                }
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("布局", color = colors.group, fontSize = 13.sp)
                    Text(model.name, color = colors.secondary, fontSize = 12.sp)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val previewHeight = (maxWidth - 20.dp) / 3 * model.aspect
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("息屏", "锁屏", "桌面").forEachIndexed { mode, name ->
                            LayoutPreview(name, model.frames.getOrNull(mode), previewHeight,
                                model.previewError.isNotEmpty(), !model.busy, model.variant) { onMode(mode) }
                        }
                    }
                }
                if (model.previewError.isNotEmpty()) TextButton(text = "预览未能生成，查看诊断", onClick = onDiagnostics)
                if (model.statusError.isNotEmpty()) TextButton(text = "壁纸状态读取失败，点击重试", onClick = onRetry)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)
                    .clickable(role = Role.Button, enabled = !model.busy, onClick = onLibrary)
                    .heightIn(min = 62.dp).padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    AliveToolGlyph(AliveToolIcon.PHOTO, Modifier.size(22.dp), colors.text)
                    Spacer(Modifier.width(8.dp))
                    Text("动态壁纸", color = colors.text, fontSize = 16.sp)
                    Spacer(Modifier.width(30.dp))
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)) {
                    SwitchRow(title = "启用 Alive 壁纸", checked = model.running,
                        summary = if (model.busy) "正在读取或更新壁纸状态…" else "与空间壁纸、视差壁纸互斥",
                        enabled = !model.busy && model.statusError.isEmpty(), onCheckedChange = onToggle)
                }
                Text("照片壁纸", fontSize = 13.sp, color = colors.group,
                    modifier = Modifier.padding(start = 14.dp, top = 24.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoStyleTile("圆景", "照片化作息屏圆球", model.photoShapes.getOrNull(0), !model.busy, Modifier.weight(1f).fillMaxHeight()) { onPhoto(601) }
                    PhotoStyleTile("留光", "从息屏相框展开", model.photoShapes.getOrNull(1), !model.busy, Modifier.weight(1f).fillMaxHeight()) { onPhoto(602) }
                }
                listOf(listOf(603 to "星月", 604 to "轻启·照片"), listOf(605 to "山脉", 606 to "银河")).forEach { pair ->
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (id, title) ->
                            PhotoStyleTile(title, "照片三态动态效果", model.photoShapes.getOrNull(id-601), !model.busy, Modifier.weight(1f).fillMaxHeight()) { onPhoto(id) }
                        }
                    }
                }
                if(model.photoShapesError.isNotEmpty()) TextButton("照片缩略图生成失败，点击重试",onClick=onRetry)
            }
        }
    }
}

@Composable
private fun AliveStatusDot(label: String, active: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(if (active == true) Color(0xFF34C759) else Color(0xFF8E8E93), CircleShape))
        Text(label, color = aliveUiColors().secondary, fontSize = 12.sp)
    }
}

@Composable
private fun RowScope.LayoutPreview(name: String, frame: ImageBitmap?, height: androidx.compose.ui.unit.Dp,
    failed: Boolean, enabled: Boolean, variant: Int, onClick: () -> Unit) {
    val colors = aliveUiColors()
    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
        .clickable(role = Role.Button, enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        // Card and render canvas share the device aspect; crop also fills rounding differences.
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp))
            .background(if (frame != null) Color.Black else colors.card).testTag("alive-layout-$name"),
            contentAlignment = Alignment.Center) {
            if (frame != null) Image(frame, contentDescription = "$name 壁纸预览", modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
            else Text(if (failed) "预览不可用" else "准备预览", color = colors.secondary, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Text(name, color = colors.text, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    }
}

@Composable
private fun PhotoStyleTile(title: String, subtitle: String, shape: ImageBitmap?,
    enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(aliveUiColors().card)
        .clickable(role = Role.Button, enabled = enabled, onClick = onClick)) {
        Column(Modifier.fillMaxHeight().padding(14.dp)) {
            PhotoShapePreview(title,shape,Modifier.fillMaxWidth().height(96.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MiuixTheme.textStyles.headline1, modifier = Modifier.weight(1f))
                Icon(MiuixIcons.ChevronForward, "自定义$title", Modifier.size(14.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
