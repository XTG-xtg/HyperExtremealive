package com.frederic22.hyperextreme.alive

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.frederic22.hyperextreme.ui.DetailTabHost
import com.frederic22.hyperextreme.ui.CoverCarousel
import com.frederic22.hyperextreme.ui.CoverItem
import com.frederic22.hyperextreme.ui.BLOCK_GAP
import com.frederic22.hyperextreme.ui.CARD_WIDTH_RATIO
import com.frederic22.hyperextreme.ui.LocalTopBarHeight
import com.frederic22.hyperextreme.ui.rememberPageScroll
import com.frederic22.hyperextreme.ui.rememberScreenSizePx
import com.frederic22.hyperextreme.ui.pagerVPadding
import com.frederic22.hyperextreme.ui.LinkRow
import androidx.compose.ui.platform.LocalDensity
import top.yukonga.miuix.kmp.squircle.squircleClip
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
            MiuixTheme(controller = controller, textStyles = HyperTextStyles) {
                AliveLibrary(infinity = intent.getBooleanExtra("infinity", false)) { finish() }
            }
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
    val presets: List<AlivePreset> = emptyList(),
    val currentPreset: AlivePreset? = null,
    val resourceMissing: Boolean = false,
    val aodEnabled: Boolean? = null,
    val aodError: String = "",
    val aodUpdating: Boolean = false,
    val forceAod: Boolean = false,
)

internal object LayoutCache : LruCache<String, List<ImageBitmap>>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: List<ImageBitmap>) = value.sumOf { it.width * it.height * 4 }
}

@Composable
internal fun AliveDetail(onBack: () -> Unit, infinity: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext
    val family = if(infinity) 800 else 0
    val familyName = if(infinity) "Infinity" else "Alive"
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var variant by remember { mutableIntStateOf(aliveHomeVariant(app, infinity)) }
    var model by remember { mutableStateOf(AliveHomeModel(resourceMissing=DynamicResources.needsPack(variant))) }
    var confirmation by remember { mutableStateOf<Boolean?>(null) }
    var switching by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var aodUpdating by remember { mutableStateOf(false) }
    var aodRevision by remember { mutableIntStateOf(0) }
    var newPhotoVariant by rememberSaveable { mutableIntStateOf(601) }
    var pendingPhotoPreview by rememberSaveable { mutableStateOf<Intent?>(null) }
    var deleting by remember { mutableStateOf<AlivePreset?>(null) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val width = configuration.screenWidthDp.coerceAtLeast(1)
    val height = configuration.screenHeightDp.coerceAtLeast(1)
    val aspect = (maxOf(width, height).toFloat() / minOf(width, height)).coerceIn(1.6f, 2.6f)

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { revision++; aodRevision++ } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(app) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { aodRevision++ }
        }
        app.contentResolver.registerContentObserver(android.provider.Settings.Secure.getUriFor(SystemAodControl.ENABLED), false, observer)
        app.contentResolver.registerContentObserver(android.provider.Settings.Secure.getUriFor(SystemAodControl.FORCE), false, observer)
        onDispose { app.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(aodRevision, aodUpdating) {
        if (!aodUpdating) {
            val state=withContext(Dispatchers.IO) { SystemAodControl.read(app) }
            model=model.copy(aodEnabled=if(state.error.isEmpty()) state.enabled else null,aodError=state.error,forceAod=state.forced)
        }
    }
    LaunchedEffect(revision) {
        try {
            val status = withContext(Dispatchers.IO) { runCatching { WallpaperFeatures.isBound(app, Feature.ALIVE) && AlivePresets.family(Config.variant(app)) == family } }
            val current = withContext(Dispatchers.IO) { AlivePresets.current(app, family) }
            variant = current.variant
            val missing = DynamicResources.needsPack(variant) && !withContext(Dispatchers.IO) { DynamicResources.installed(app) }
            val history = withContext(Dispatchers.IO) { AlivePresets.list(app) }.filter { AlivePresets.family(it.variant) == family && it.signature() != current.signature() }
            val name = withContext(Dispatchers.IO) {
                runCatching { AliveCatalog.load(app).firstOrNull { it.id == variant }?.name }.getOrNull() ?: "Alive"
            }
            model = model.copy(name = name, running = status.getOrDefault(model.running), busy = false,
                frameworkConnected = ActivationState.frameworkConnected,
                statusError = status.exceptionOrNull()?.message.orEmpty(), aspect = aspect, variant = variant,
                currentPreset = current, presets = history,resourceMissing=missing,
                previewError=if(missing) DynamicResources.MISSING else "")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            model = model.copy(busy = false, statusError = "壁纸与预设读取失败：${e.message}；可刷新重试")
        }
    }
    val activePhoto = PhotoMedia.resolve(app, PhotoOptions.load(app, false), false, 0)
    val layoutKey = "single-cover:${model.currentPreset?.signature()}:$variant:${Config.dark(app)}:${Config.prefs(app).getLong("revision", 0)}:" +
        "${activePhoto.lastModified()}:${activePhoto.length()}:$aspect"
    val tileOptions = remember(revision) { PhotoOptions.load(app, true) }
    val tileKey = PhotoStyleThumbnails.key(app, tileOptions)
    DisposableEffect(tileKey, diagnostics, revision) {
        var disposed = false
        var job: PhotoStyleThumbnails? = null
        if(!diagnostics && !infinity) {
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
    DisposableEffect(layoutKey, diagnostics, model.currentPreset?.signature(),model.resourceMissing) {
        var disposed = false
        var worker: AliveLayoutFrames? = null
        if (!diagnostics && !model.resourceMissing) {
            val cached = LayoutCache.get(layoutKey)
            if (cached != null) model = model.copy(frames = cached, previewError = "")
            else {
                model = model.copy(frames = emptyList(), previewError = "")
                val current = model.currentPreset
                worker = AliveLayoutFrames(app, variant, current?.dark ?: Config.dark(app), 288, (288 * aspect).roundToInt(),
                    current?.render ?: AliveRenderOptions.load(app), current?.photo ?: PhotoOptions.load(app,false), true) { bitmaps, error ->
                    Handler(Looper.getMainLooper()).post {
                        if (disposed) bitmaps.forEach { it.recycle() }
                        else {
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
    fun openPreset(preset: AlivePreset) {
        if(DynamicResources.needsPack(preset.variant) && !DynamicResources.installed(app)) {
            context.startActivity(Intent(context,AliveLibraryActivity::class.java))
            return
        }
        context.startActivity(Intent(context, AlivePreviewActivity::class.java).putExtra("variant",preset.variant)
            .putExtra("name",preset.name).putExtra("mode",1).putExtra("preset",preset.json()))
    }
    fun edit(id: Int, mode: Int = 1) {
        val name = when (id) { 601 -> "圆景"; 602 -> "留光"; 603 -> "星月"; 604 -> "轻启·照片"; 605 -> "山脉"; 606 -> "银河"; else -> model.name }
        context.startActivity(Intent(context, AlivePreviewActivity::class.java)
            .putExtra("variant", id).putExtra("name", name).putExtra("mode", mode))
    }
    fun openLibrary() {
        context.startActivity(Intent(context, AliveLibraryActivity::class.java).putExtra("infinity", infinity))
    }
    val newPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                pendingPhotoPreview = withContext(Dispatchers.IO) { newAlivePhotoPreview(app, uri, newPhotoVariant) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { AppToast.show(context, "照片导入失败：${e.message}", long = true) }
            finally { importing = false }
        }
    }
    ImportedWallpaperPreviewEffect(pendingPhotoPreview != null, importing) {
        val preview = pendingPhotoPreview ?: return@ImportedWallpaperPreviewEffect
        pendingPhotoPreview = null
        context.startActivity(preview)
    }
    BackHandler(enabled = diagnostics || switching || importing) { if (!switching && !importing) diagnostics = false }
    if (diagnostics) {
        Scaffold(topBar = { SmallTopAppBar(title = "状态与诊断", navigationIcon = { AliveBackButton { diagnostics = false } }) }) { padding ->
            Box(Modifier.padding(padding)) { AliveDiagnostics() }
        }
    } else {
        AliveHomeContent(model.copy(busy = model.busy || switching || importing, aodUpdating = aodUpdating), onBack,
            onDiagnostics = { diagnostics = true }, onMode = { if(model.resourceMissing) openLibrary() else model.currentPreset?.let(::openPreset) ?: edit(variant, 1) },
            onPhoto = { edit(it) }, onLibrary = ::openLibrary,
            onToggle = {
                if(it && model.resourceMissing) openLibrary()
                else if (it && AlivePresets.family(Config.variant(app)) != family) {
                    model.currentPreset?.let(::openPreset) ?: edit(variant, 1)
                    AppToast.show(context, "请预览并确认应用 $familyName 壁纸")
                } else confirmation = it
            }, onRetry = { revision++ }, onNew = {
                if(infinity) openLibrary()
                else {
                    newPhotoVariant = variant.takeIf { it in 601..606 }
                        ?: model.presets.firstOrNull { it.variant in 601..606 }?.variant ?: 601
                    newPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            onPreset = ::openPreset, onDeletePreset = { deleting = it }, infinity = infinity,
            onAodToggle = { enabled ->
                if(!aodUpdating) scope.launch {
                    aodUpdating=true
                    try {
                        val result=withContext(Dispatchers.IO) { SystemAodControl.set(app,enabled) }
                        AppToast.show(context,result.text,long=!result.ok())
                    } finally { aodUpdating=false; aodRevision++ }
                }
            }, onAodRetry = { aodRevision++ }, onForceAod = { forced ->
                if(!aodUpdating) scope.launch {
                    aodUpdating=true
                    try {
                        val result=withContext(Dispatchers.IO) { SystemAodControl.setForced(app,forced) }
                        AppToast.show(context,result.text,long=true)
                    } finally { aodUpdating=false; aodRevision++ }
                }
            })
    }
    WindowDialog(show = importing, title = "正在导入照片", summary = "导入完成后进入预览，当前壁纸不会改变。",
        onDismissRequest = {}) {}
    WindowDialog(show = deleting != null, title = "删除这个预设？", summary = "只移除历史卡片，当前壁纸与共享照片保留。", onDismissRequest = { deleting = null }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", onClick = { deleting = null })
            TextButton("删除预设", onClick = {
                val item=deleting ?: return@TextButton;deleting=null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { AlivePresets.delete(app,item.id) } }
                        .onFailure { AppToast.show(context,"删除失败：${it.message}") }
                    revision++
                }
            })
        }
    }
    WindowDialog(show = confirmation != null,
        title = if (confirmation == false) "关闭 $familyName 壁纸？" else "启用 $familyName 壁纸？",
        summary = if (confirmation == false)
            "将卸下正在使用的 $familyName 壁纸，对应位置恢复系统默认壁纸。预设和参数会保留，下次可直接开启。"
        else "将上次应用的 $familyName 布局设为桌面和锁屏壁纸。不会应用未确认的草稿。",
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
                            val result = withContext(Dispatchers.IO) { runCatching { AliveSettings.setFamilyEnabled(app, enabled, infinity) } }
                            val value = result.getOrNull()
                            val message = result.exceptionOrNull()?.message ?: if (value?.ok() == true)
                                if (enabled) "$familyName 已开启，桌面和锁屏均已回读确认" else "$familyName 已关闭，预设与设置已保留"
                            else value?.text ?: "操作失败，请到诊断页查看"
                            AppToast.show(context, message, long = true)
                        } finally { switching = false; revision++ }
                    }
                })
        }
    }
}

internal fun aliveHomeVariant(context: android.content.Context, infinity: Boolean = false): Int {
    val active = Config.variant(context)
    fun accepts(id: Int) = if(infinity) InfinityTheme.supports(id) else id in 1..606
    if(accepts(active)) return active
    val prefs = Config.prefs(context)
    val fallback = if(infinity) 801 else 1
    val remembered = prefs.getInt(if(infinity) "infinity_last_variant" else "alive_last_variant", fallback)
    // Read the ui19 preference only as an Infinity migration fallback, never as an Alive theme.
    if(infinity && !prefs.contains("infinity_last_variant"))
        return prefs.getInt("alive_last_variant", fallback).takeIf(::accepts) ?: fallback
    return remembered.takeIf(::accepts) ?: fallback
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
    onNew: () -> Unit,
    onPreset: (AlivePreset) -> Unit = {},
    onDeletePreset: (AlivePreset) -> Unit = {},
    infinity: Boolean = false,
    onAodToggle: (Boolean) -> Unit = {},
    onAodRetry: () -> Unit = {},
    onForceAod: (Boolean) -> Unit = {},
) {
    val familyName = if(infinity) "Infinity" else "Alive"
    val screen = rememberScreenSizePx()
    val cardWidth = with(LocalDensity.current) { (screen.width * CARD_WIDTH_RATIO).toDp() }
    var focused by remember { mutableIntStateOf(1) }
    val focusPreset = model.presets.getOrNull(focused - 2)
    val context = LocalContext.current
    DetailTabHost(title = "$familyName 壁纸", onBack = onBack, onRestartAod = null) {
        Column(Modifier.fillMaxSize().testTag(if(infinity) "infinity-home-page" else "alive-home-page").verticalScroll(rememberPageScroll())
            .padding(top = LocalTopBarHeight.current, bottom = 32.dp)) {
            Spacer(Modifier.height(BLOCK_GAP))
            val cards = listOf(CoverItem("current", null, frame = model.frames.firstOrNull(), description = "当前壁纸封面")) +
                model.presets.map { CoverItem(it.id, AlivePresets.cover(context,it.id), description = "已保存预设：${it.name}") }
            CoverCarousel(cards, 1, "current", null, 0f, 0, 0, screen,
                onFocus = { focused = it }, onNew = { if (!model.busy) onNew() },
                onClick = { card -> if (!model.busy) {
                    if(card.id == "current") onMode(1) else model.presets.firstOrNull { it.id==card.id }?.let(onPreset)
                } }, onDelete = { card -> model.presets.firstOrNull { it.id==card.id }?.let(onDeletePreset) },
                onDeleteSettled = {}, allowDelete = !model.busy)
            Spacer(Modifier.height((BLOCK_GAP - pagerVPadding(cardWidth)).coerceAtLeast(0.dp)))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { if (focused == 0) onNew() else if(focusPreset != null) onPreset(focusPreset) else onMode(1) },
                    enabled = !model.busy && focused >= 0,
                    modifier = Modifier.widthIn(min = 160.dp).width(cardWidth * 5f / 6f),
                    colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(if (focused == 0) "新建壁纸" else "预览与应用")
                }
            }
            Text(when { focused == 0 -> "新建壁纸"; focusPreset != null -> "${focusPreset.name} · 已保存预设"
                else -> "${model.name} · ${if(model.running) "正在使用" else "上次使用"}" },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, start = 16.dp, end = 16.dp),
                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VivoSettingsGroup("壁纸与预览") {
                    LinkRow(if(infinity) "主题样式" else "动态壁纸",
                        if(infinity) "S8、N8 与几何 · 二十款三态动态效果" else "浏览动态场景；照片样式在下方单独选择") { if (!model.busy) onLibrary() }
                    BasicComponent(title = "草稿与预设", summary = "左侧新建，右侧为已保存预设。在预览编辑器点「保存预设」；切换壁纸后自动保留历史配置。")
                    if(model.resourceMissing) LinkRow("需要动态壁纸资源包", "导入后恢复预览，已有预设会保留", onLibrary)
                    else if (model.previewError.isNotEmpty()) LinkRow("预览未能生成，查看诊断", "不会影响已保存的壁纸与照片", onDiagnostics)
                }
                VivoSettingsGroup("运行状态") {
                    SwitchRow(title = "启用 $familyName 壁纸", checked = model.running,
                        summary = when { model.busy -> "正在读取或更新壁纸状态…"
                            model.statusError.isNotEmpty() -> "状态读取失败，请重试；不会改动当前壁纸"
                            model.running -> "正在使用 · 与其他壁纸组件互斥"
                            else -> "尚未启用 · 预览并确认应用后生效" },
                        enabled = !model.busy && !model.aodUpdating && model.statusError.isEmpty(), onCheckedChange = onToggle)
                    SwitchRow(title = "系统息屏显示", checked = model.aodEnabled == true, modifier = Modifier.testTag("system-aod-switch"),
                        summary = when {
                            model.aodUpdating -> "正在更新系统息屏开关…"
                            model.aodError.isNotEmpty() -> model.aodError
                            model.aodEnabled == null -> "正在读取系统息屏状态…"
                            else -> "与系统开关同步 · 需要 Root 授权，显示时段仍由系统控制"
                        }, enabled = !model.busy && !model.aodUpdating && model.aodEnabled != null,
                        onCheckedChange = onAodToggle)
                    SwitchRow(title = "强制息屏支持", checked = model.forceAod,
                        summary = "跳过设备支持限制并开启息屏显示 · 开启或关闭后重启手机完整生效",
                        enabled = !model.busy && !model.aodUpdating && model.aodEnabled != null,
                        onCheckedChange = onForceAod)
                    if(model.aodError.isNotEmpty()) LinkRow("息屏状态读取失败，点击重试", "重新读取系统开关", onAodRetry)
                    if (model.statusError.isNotEmpty()) LinkRow("壁纸状态读取失败，点击重试", "保留现有设置", onRetry)
                    LinkRow("管理与诊断", if (model.frameworkConnected) "LSPosed 已连接 · 查看运行状态与故障信息" else "LSPosed 未连接 · 查看配置说明", onDiagnostics)
                }
                if(!infinity) Column {
                    SmallTitle("照片壁纸")
                    listOf(listOf(601 to "圆景", 602 to "留光"), listOf(603 to "星月", 604 to "轻启·照片"),
                        listOf(605 to "山脉", 606 to "银河")).forEachIndexed { row, pair ->
                        if (row > 0) Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { (id, title) ->
                                val subtitle = when (id) { 601 -> "照片化作息屏圆球"; 602 -> "从息屏相框展开"; else -> "照片三态动态效果" }
                                PhotoStyleTile(title, subtitle, model.photoShapes.getOrNull(id - 601), !model.busy,
                                    Modifier.weight(1f).fillMaxHeight()) { onPhoto(id) }
                            }
                        }
                    }
                    if (model.photoShapesError.isNotEmpty()) TextButton("照片缩略图生成失败，点击重试", onClick = onRetry)
                }
                VivoSettingsGroup("使用说明") {
                    BasicComponent(title = "生效方式", summary = "需要 Root + LSPosed。$familyName 使用独立的主题入口与预设记录，预览并确认应用后生效。")
                }
            }
        }
    }
}

@Composable
private fun PhotoStyleTile(title: String, subtitle: String, shape: ImageBitmap?,
    enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.squircleClip(16.dp).background(MiuixTheme.colorScheme.surfaceContainer)
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
