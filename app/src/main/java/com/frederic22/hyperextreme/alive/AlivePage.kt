package com.frederic22.hyperextreme.alive

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frederic22.hyperextreme.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Dynamic scenes live in a separate child page; custom photo styles stay on the Alive home. */
@Composable
internal fun AliveLibrary(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val owner = LocalLifecycleOwner.current
    var scenes by remember { mutableStateOf<List<AliveCatalog.Scene>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf("") }
    var group by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    val groups = aliveDynamicGroups
    val labels = aliveDynamicLabels
    val colors = aliveUiColors()
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { AliveCatalog.load(app).filter { it.group != 5 } } }
        scenes = result.getOrDefault(emptyList())
        failure = result.exceptionOrNull()?.message.orEmpty()
        loading = false
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = {
        SmallTopAppBar(title = "动态壁纸", color = colors.page, navigationIcon = { AliveBackButton(onBack) })
    }) { padding ->
        Column(Modifier.fillMaxSize().background(colors.page).padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                .padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index, label ->
                    Box(Modifier.clip(RoundedCornerShape(24.dp))
                        .background(if (group == index) colors.card else Color.Transparent)
                        .selectable(selected = group == index, role = Role.Tab, onClick = { group = index })
                        .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 14.sp, color = if (group == index) colors.text else colors.secondary)
                    }
                }
            }
            when {
                loading -> Text(stringResource(R.string.alive_loading), modifier = Modifier.padding(20.dp), color = colors.secondary)
                scenes.isEmpty() -> Text(stringResource(R.string.alive_empty) + "\n" + failure, modifier = Modifier.padding(20.dp), color = colors.secondary)
                else -> {
                    val applied = remember(revision) { Config.variant(app) }
                    val running = remember(revision) { AliveSettings.isRunning(app) }
                    val fontScale = LocalDensity.current.fontScale
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val columns = if (fontScale >= 1.3f || maxWidth < 340.dp) 2 else if (maxWidth > 600.dp) 4 else 3
                        key(group) {
                            LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize().testTag("alive-library-grid"),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                items(scenes.filter { group == 0 || it.group == groups[group] }, key = { it.id }) { scene ->
                                    val selected = scene.id == applied && running
                                    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) {
                                        context.startActivity(Intent(context, AlivePreviewActivity::class.java)
                                            .putExtra("variant", scene.id).putExtra("name", scene.name).putExtra("mode", 1))
                                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        AliveThumbnail(scene, revision, Modifier.fillMaxWidth().aspectRatio(.70f)
                                            .clip(RoundedCornerShape(14.dp)).then(if (selected)
                                                Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier))
                                        Text(scene.name, fontSize = 14.sp, color = colors.text, textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp))
                                        if (selected) Text("正在使用", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AliveDiagnostics() {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var runtime by remember { mutableStateOf(EngineRegistry.status(app)) }
    var busy by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf(RootBridge.lastResult(app)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.alive_framework_note), style = MiuixTheme.textStyles.body2)
        Card(insideMargin = PaddingValues(0.dp)) {
            BasicComponent(title = stringResource(R.string.alive_runtime), summary = stringResource(R.string.alive_runtime_format,
                stringResource(if (runtime.getBoolean("home_engine")) R.string.alive_running else R.string.alive_not_running),
                stringResource(if (runtime.getBoolean("lock_engine")) R.string.alive_running else R.string.alive_not_running),
                stringResource(if (runtime.getBoolean("enabled")) R.string.alive_on else R.string.alive_off), runtime.getLong("frames")))
            BasicComponent(title = stringResource(R.string.alive_refresh), enabled = !busy, onClick = { runtime = EngineRegistry.status(app) })
            BasicComponent(title = "系统息屏时钟布局", summary = if(runtime.containsKey("aod_clock_bounds"))
                "已获取系统坐标；包含系统防烧屏位移" else "尚未获取。应用后进入一次息屏再刷新；当前使用默认位置与小幅位移兜底。")
            BasicComponent(title = "电源状态", summary = "${if(runtime.getBoolean("power_sleeping")) "息屏保护中" else "可交互"} · ${runtime.getString("reason", "initial")}")
            BasicComponent(title = stringResource(if (busy) R.string.alive_busy else R.string.alive_root_query),
                summary = stringResource(R.string.alive_root_note), enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val result = withContext(Dispatchers.IO) { runCatching { RootBridge.wallpaper(app, false) } }
                            detail = result.getOrNull()?.let { "exit=${it.code}\n${it.text}" } ?: result.exceptionOrNull().toString()
                            runtime = EngineRegistry.status(app)
                        } finally { busy = false }
                    }
                })
        }
        SelectionContainer { Text(detail + "\n\n" + runtime.toString(), style = MiuixTheme.textStyles.body2) }
        Text(stringResource(R.string.alive_limits_note), style = MiuixTheme.textStyles.body2)
    }
}

@Composable
internal fun AliveTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(tabs = labels, selectedTabIndex = selected, onTabSelected = onSelect, modifier = Modifier.fillMaxWidth())
}

internal object ThumbnailCache : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

@Composable
internal fun AliveThumbnail(scene: AliveCatalog.Scene, revision: Int, modifier: Modifier) {
    val app = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, scene.id, revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val draft = AliveSettings.draft(app)
                val file = if (draft.isFile) draft else java.io.File(app.filesDir, "alive/photo.png")
                val key = if (scene.thumbnail.isNotEmpty()) scene.thumbnail else "${file.path}:${file.lastModified()}:${file.length()}"
                ThumbnailCache.get(key) ?: run {
                    fun decode(options: BitmapFactory.Options) = if (scene.thumbnail.isNotEmpty()) {
                        app.assets.open(scene.thumbnail).use { BitmapFactory.decodeStream(it, null, options) }
                    } else BitmapFactory.decodeFile(file.path, options)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    decode(options)
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 600) options.inSampleSize *= 2
                    options.inJustDecodeBounds = false
                    decode(options)?.asImageBitmap()?.also { ThumbnailCache.put(key, it) }
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, contentDescription = scene.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(stringResource(if (scene.group == 5) R.string.alive_photo_empty else R.string.alive_loading), modifier = Modifier.padding(16.dp))
    }
}
