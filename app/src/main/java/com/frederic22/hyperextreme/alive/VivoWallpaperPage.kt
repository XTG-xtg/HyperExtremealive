package com.frederic22.hyperextreme.alive

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.frederic22.hyperextreme.data.WallpaperFeatures
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy.Feature
import com.frederic22.hyperextreme.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
internal fun VivoWallpaperDetail(variant: Int) {
    val context = LocalContext.current
    val app = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(VivoOptions.load(app, variant, true)) }
    var cover by remember { mutableStateOf<File?>(null) }
    var running by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf("") }
    var importKind by rememberSaveable { mutableIntStateOf(VivoMediaSelection.WALLPAPER) }
    var job by remember { mutableStateOf<Job?>(null) }
    var recognition by remember { mutableStateOf<VivoSubjectRecognition?>(null) }
    var activePreset by remember { mutableStateOf<AlivePreset?>(null) }
    var presets by remember { mutableStateOf<List<AlivePreset>>(emptyList()) }
    var currentCover by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<AlivePreset?>(null) }
    var newImport by rememberSaveable { mutableStateOf(false) }
    var importedPreviewPending by rememberSaveable { mutableStateOf(false) }
    val current by rememberUpdatedState(options)

    fun update(next: VivoOptions) {
        try { next.saveDraft(app); options = next }
        catch (e: Exception) { failure = true; message = "草稿保存失败：${e.message}" }
    }
    fun preview(panel: String = "", area: Int = 2) {
        if (busy) return
        context.startActivity(Intent(context, AlivePreviewActivity::class.java)
            .putExtra("variant", variant).putExtra("name", vivoTitle(variant)).putExtra("mode", 1)
            .putExtra("panel", panel).putExtra("maskArea", area))
    }
    ImportedWallpaperPreviewEffect(importedPreviewPending, busy) {
        importedPreviewPending = false
        preview()
    }
    suspend fun preserveDraft(): AlivePreset {
        val snapshot = withContext(Dispatchers.IO) { AlivePresets.snapshot(app,vivoTitle(variant),variant,Config.dark(app),
            Config.prefs(app).getBoolean("clock",false),PhotoOptions.defaults(),AliveRenderOptions.load(app,true),true,current) }
        AlivePresets.save(app,snapshot)
        return snapshot
    }
    suspend fun recognize(snapshot: VivoOptions, area: Int) {
        val task = VivoSubjectRecognition()
        recognition = task
        try {
            val result = recognizeVivoDraft(app, snapshot, area, task) { text -> scope.launch { progress = text } }
            update(result)
            message = "${result.subjectSource} · 主体已生成，可在“修整主体”中检查边缘"
        } finally { recognition = null }
    }
    fun work(label: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; progress = label; message = ""; failure = false
        job = scope.launch {
            try { action() }
            catch (e: CancellationException) { message = "已取消，现有照片和草稿已保留"; throw e }
            catch (e: Exception) { failure = true; message = e.message ?: "操作失败，请重试" }
            finally { busy = false; job = null }
        }
    }
    fun openPreset(preset: AlivePreset) = work("正在保留草稿…") {
        if(current.json() != preset.vivoJson) preserveDraft()
        context.startActivity(Intent(context,AlivePreviewActivity::class.java).putExtra("variant",variant)
            .putExtra("name",vivoTitle(variant)).putExtra("mode",1).putExtra("preset",preset.json()))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recognition?.cancel() }
    }
    LaunchedEffect(revision) {
        try {
            if (!busy) options = VivoOptions.load(app, variant, true)
            val state = withContext(Dispatchers.IO) { runCatching { WallpaperFeatures.isBound(app, Feature.ALIVE) && Config.variant(app) == variant } }
            running = state.getOrDefault(false)
            val stored = withContext(Dispatchers.IO) { AlivePresets.current(app,variant) }
            activePreset = stored
            applied = stored.vivoJson
            presets = withContext(Dispatchers.IO) { AlivePresets.list(app) }.filter { it.variant==variant && it.signature()!=stored.signature() }
            val activeOptions = VivoOptions.parse(stored.vivoJson)
            currentCover = withContext(Dispatchers.IO) {
                AlivePresets.cover(app,stored.signature()).takeIf { it.isFile } ?: activeOptions.photos.firstOrNull()?.let { File(app.filesDir,it) }
                    ?: File(app.cacheDir,"vivo-cover-$variant.jpg").also { file ->
                        if(!file.isFile) app.assets.open("vivo/preview/${when(variant){701->"liquid";702->"raster";else->"flash"}}.jpg")
                            .use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = true; message = "壁纸与预设读取失败：${e.message}；可刷新重试" }
    }
    LaunchedEffect(options.photos.toList(), revision) {
        cover = withContext(Dispatchers.IO) {
            options.photos.firstOrNull()?.let { File(app.filesDir, it).takeIf(File::isFile) } ?: run {
                val file = File(app.cacheDir, "vivo-cover-$variant.jpg")
                if (!file.isFile) runCatching {
                    val name = when (variant) { 701 -> "liquid"; 702 -> "raster"; else -> "flash" }
                    app.assets.open("vivo/preview/$name.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                file.takeIf(File::isFile)
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val creatingWallpaper = newImport
        newImport = false
        if (uri != null) work("正在导入壁纸…") {
            preserveDraft()
            val selection = VivoMediaSelection(app, if(creatingWallpaper) VivoMediaSelection.defaults(current) else current)
            var imported = false
            try {
                withContext(Dispatchers.IO) {
                    selection.importUris(VivoEngineRuntime.prepare(app).get(20, TimeUnit.SECONDS), importKind, listOf(uri))
                    selection.result.validateFiles(app)
                }
                selection.result.saveDraft(app); options = selection.result; imported = true
                message = "壁纸已导入草稿，尚未应用"
                if (importKind == VivoMediaSelection.WALLPAPER && selection.result.autoSubject) {
                    try { recognize(selection.result, if (current.area == 3) 3 else 2) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failure = true; message = "照片已保留，主体识别未完成：${e.message}" }
                }
                revision++
                if (creatingWallpaper) {
                    importedPreviewPending = true
                    if (failure) AppToast.show(context, message, long = true)
                }
            } finally { if (!imported) selection.discard() }
        }
    }
    fun pick(kind: Int, new: Boolean = false) {
        if (busy) return
        importKind = kind
        newImport = new
        picker.launch(PickVisualMediaRequest(if (kind in VivoMediaSelection.SLOT_VIDEO..VivoMediaSelection.SLOT_VIDEO + 2)
            ActivityResultContracts.PickVisualMedia.VideoOnly else ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    VivoWallpaperContent(options, cover, running, options.json() != applied, message, failure,
        onChange = ::update, onImport = { pick(it) }, onPreview = { preview() },
        onCrop = { preview("vivo-crop") }, onMask = { preview("mask", it) },
        onRecognize = { area -> work("正在准备主体识别…") { recognize(current, area) } },
        onSwap = { a, b -> work("正在交换素材位置…") {
            val selection = VivoMediaSelection(app, current)
            var saved = false
            try {
                withContext(Dispatchers.IO) { selection.swapSlots(VivoEngineRuntime.prepare(app).get(20, TimeUnit.SECONDS), a, b) }
                selection.result.saveDraft(app); options = selection.result; saved = true
            } finally { if (!saved) selection.discard() }
        } }, onReset = { pending = "reset" }, onStop = { pending = "stop" },
        currentOptions = activePreset?.vivoJson?.let(VivoOptions::parse), currentCover = currentCover,
        savedCards = presets.map { CoverItem(it.id,AlivePresets.cover(app,it.id),description="已保存预设：${it.name}") },
        draftSaved = presets.any { it.vivoJson == options.json() },
        savedLabels = presets.associate { it.id to if(it.variant==703) VivoFlashStyles.names[VivoOptions.parse(it.vivoJson).style-1] else it.name },
        onCurrent = { activePreset?.let(::openPreset) }, onSaved = { id -> presets.firstOrNull { it.id==id }?.let(::openPreset) },
        onDeleteSaved = { id -> deleting=presets.firstOrNull { it.id==id } },
        onNew = { pick(if(options.family==VivoOptions.RASTER) VivoMediaSelection.SLOT_IMAGE else VivoMediaSelection.WALLPAPER,true) },
        onSaveDraft = { work("正在保存预设…") { preserveDraft();revision++;message="已保存预设，当前壁纸未改变" } })

    WindowDialog(show=deleting!=null,title="删除这个预设？",summary="只移除保存的卡片，不删除当前壁纸或共享的照片、蒙版。",onDismissRequest={deleting=null}) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton("取消",onClick={deleting=null})
            TextButton("删除预设",onClick={val item=deleting ?: return@TextButton;deleting=null
                work("正在删除预设…") { withContext(Dispatchers.IO){AlivePresets.delete(app,item.id)};revision++ }})
        }
    }

    WindowDialog(show = busy, title = "正在处理", summary = progress,
        onDismissRequest = {}) {
        if (recognition != null) TextButton("取消识别", onClick = { recognition?.cancel(); job?.cancel() })
    }
    WindowDialog(show = pending.isNotEmpty(), title = if (pending == "stop") "关闭${vivoTitle(variant)}？" else "恢复内置壁纸与效果？",
        summary = if (pending == "stop") "恢复对应位置的系统默认壁纸，保留照片和设置。不会关闭其他壁纸。"
            else "只重置此类壁纸的草稿，不改变正在使用的壁纸。下次预览并确认应用后才生效。",
        onDismissRequest = { pending = "" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", modifier = Modifier.weight(1f), onClick = { pending = "" })
            TextButton("确认", modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                val action = pending; pending = ""
                if (action == "reset") work("正在保留草稿…") { preserveDraft();update(VivoMediaSelection.defaults(current));revision++;message = "已恢复内置草稿，旧草稿保留为预设" }
                else work("正在关闭壁纸…") {
                    withContext(Dispatchers.IO) { AliveSettings.disableVariant(app, variant) }
                    revision++; message = "壁纸已关闭，照片和设置已保留"
                }
            })
        }
    }
}

/** Picker completion may arrive before resume; open only after import work has released busy. */
@Composable
internal fun ImportedWallpaperPreviewEffect(pending: Boolean, busy: Boolean, onOpen: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val open by rememberUpdatedState(onOpen)
    LaunchedEffect(pending, busy, owner) {
        if (pending && !busy) owner.lifecycle.withResumed { open() }
    }
}

/** Pure UI, shared with screenshot/accessibility tests; no service or root calls here. */
@Composable
internal fun VivoWallpaperContent(options: VivoOptions, cover: File?, running: Boolean, dirty: Boolean,
    message: String, failure: Boolean, onChange: (VivoOptions) -> Unit, onImport: (Int) -> Unit,
    onPreview: () -> Unit, onCrop: () -> Unit, onMask: (Int) -> Unit, onRecognize: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit, onReset: () -> Unit, onStop: () -> Unit,
    currentOptions: VivoOptions? = null, currentCover: File? = null, savedCards: List<CoverItem> = emptyList(),
    savedLabels: Map<String,String> = emptyMap(), onCurrent: () -> Unit = onPreview, onSaved: (String)->Unit = {},
    onDeleteSaved: (String)->Unit = {}, onNew: (() -> Unit)? = null, onSaveDraft: (() -> Unit)? = null,
    draftSaved: Boolean = false) {
    val screen = rememberScreenSizePx()
    val cardWidth = with(LocalDensity.current) { (screen.width * CARD_WIDTH_RATIO).toDp() }
    var focused by remember { mutableIntStateOf(1) }
    val activeOptions = currentOptions ?: options
    val activeCover = currentCover ?: cover
    val draftCard = currentOptions != null && dirty && !draftSaved
    val source by produceState<RectF?>(null, activeCover, activeOptions.x, activeOptions.y, activeOptions.zoom, screen) {
        value = withContext(Dispatchers.IO) {
            val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            activeCover?.let { BitmapFactory.decodeFile(it.path, info) }
            if (info.outWidth > 0 && info.outHeight > 0 && options.family != VivoOptions.RASTER && activeCover?.parentFile?.name!="presets") {
                val viewport = activeOptions.viewport().apply { dimensions(info.outWidth, info.outHeight, screen.width, screen.height) }
                RectF(viewport.left(), viewport.top(), viewport.left() + viewport.width(), viewport.top() + viewport.height())
            } else null
        }
    }
    val draftSource by produceState<RectF?>(null, cover, options.x, options.y, options.zoom, screen) {
        value = withContext(Dispatchers.IO) {
            val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cover?.let { BitmapFactory.decodeFile(it.path, info) }
            if (info.outWidth > 0 && info.outHeight > 0 && options.family != VivoOptions.RASTER) {
                val viewport = options.viewport().apply { dimensions(info.outWidth, info.outHeight, screen.width, screen.height) }
                RectF(viewport.left(), viewport.top(), viewport.left() + viewport.width(), viewport.top() + viewport.height())
            } else null
        }
    }
    Column(Modifier.fillMaxSize().testTag("vivo-wallpaper-page").verticalScroll(rememberPageScroll())
        .padding(top = LocalTopBarHeight.current, bottom = 32.dp)) {
        Spacer(Modifier.height(BLOCK_GAP))
        val cards = listOf(CoverItem("current",activeCover,source,description="当前壁纸封面")) +
            (if(draftCard) listOf(CoverItem("working",cover,draftSource,description="未应用的草稿",removable=false)) else emptyList()) + savedCards
        fun newWallpaper() { onNew?.invoke() ?: onImport(if(options.family==VivoOptions.RASTER) VivoMediaSelection.SLOT_IMAGE else VivoMediaSelection.WALLPAPER) }
        fun openCard(id: String) { when(id){"current"->onCurrent();"working"->onPreview();else->onSaved(id)} }
        CoverCarousel(cards, 1, "current",
            null, 0f, 0, 0, screen, onFocus = { focused = it },
            onNew = ::newWallpaper, onClick = { openCard(it.id) },
            onDelete = { if(it.id!="working") onDeleteSaved(it.id) }, onDeleteSettled = {}, allowDelete = true)
        Spacer(Modifier.height((BLOCK_GAP - pagerVPadding(cardWidth)).coerceAtLeast(0.dp)))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(onClick = { if (focused == 0) newWallpaper() else cards.getOrNull(focused-1)?.let { openCard(it.id) } }, enabled=focused>=0,
                modifier = Modifier.widthIn(min = 160.dp).width(cardWidth * 5f / 6f).testTag("vivo-preview"),
                colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(if (focused == 0) "新建壁纸" else "预览与应用")
            }
        }
        val selectedId=cards.getOrNull(focused-1)?.id
        Text(when { focused==0->"新建壁纸";selectedId=="working"->"未应用的草稿";selectedId!=null&&selectedId!="current"->"${savedLabels[selectedId].orEmpty()} · 已保存预设"
            running->"正在使用";else->"上次使用 · 确认应用后生效" },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, start = 16.dp, end = 16.dp),
            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(onSaveDraft!=null) VivoSettingsGroup("草稿与预设") {
                LinkRow("保存当前草稿为预设","保留照片、主体、涂抹选区、效果和取景；不会替换当前壁纸",onSaveDraft)
                if(dirty) LinkRow("继续编辑草稿","下方设置只修改草稿；当前壁纸卡片保持不变",onPreview)
                if(selectedId!=null && selectedId !in listOf("current","working")) LinkRow("删除所选预设","当前壁纸与共享素材保留") { onDeleteSaved(selectedId) }
            }
            if (options.family == VivoOptions.FLASH) BasicComponent(title = VivoFlashStyles.HINT,
                summary = "静置时可在预览中点「光效演示」；夜空星点建议搭配深色壁纸。")
            VivoSettingsGroup("壁纸与取景") { VivoPhotoControls(options, onChange, onImport, onCrop, onSwap) }
            if (message.isNotEmpty()) Card(insideMargin = PaddingValues(0.dp)) {
                BasicComponent(title = if (failure) "操作未完成" else "草稿状态", summary = message)
            }
            VivoSettingsGroup("效果设置") { VivoControls(options, onChange) }
            if (options.family != VivoOptions.RASTER) VivoSettingsGroup("主体识别与效果范围") {
                VivoAreaControls(options, onChange, onMask, onRecognize, onImport)
            }
            VivoSettingsGroup("动作与三态") { VivoMotionControls(options, onChange) }
            Card(insideMargin = PaddingValues(0.dp)) {
                BasicComponent(title = "生效方式", summary = "共享 Alive 壁纸服务，需要 Root + LSPosed。与 Alive、空间、光栅和视差壁纸互斥；本页设置不会立即改动实际壁纸。")
                LinkRow("恢复内置壁纸与效果", "仅重置当前草稿", onReset)
                if (running) LinkRow("关闭当前壁纸", "保留素材与参数", onStop)
            }
        }
    }
}
