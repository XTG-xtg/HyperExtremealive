package com.frederic22.hyperextreme.alive

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frederic22.hyperextreme.applyAppSystemBars
import com.frederic22.hyperextreme.resolveAppDark
import com.frederic22.hyperextreme.data.MaskPrefs
import com.frederic22.hyperextreme.ui.ThemeMode
import com.frederic22.hyperextreme.ui.HyperTextStyles
import com.frederic22.hyperextreme.ui.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Independent draft editor. No root, notification posting, wallpaper or power operations. */
class AliveExtensionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = ThemeMode.of(MaskPrefs(this).themeMode)
        applyAppSystemBars(resolveAppDark(this, mode.id))
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme(controller = remember { ThemeController(mode.toColorSchemeMode()) }, textStyles = HyperTextStyles) {
                ExtensionsEditor { finish() }
            }
        }
    }
}

internal val ExtensionSaver = Saver<ExtensionOptions, Bundle>(
    save = { Bundle().also(it::write) }, restore = { ExtensionOptions.from(it) })

@Composable
private fun ExtensionsEditor(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = ExtensionSaver) { mutableStateOf(ExtensionOptions.load(context)) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var family by rememberSaveable { mutableIntStateOf(clockFamily(draft.style)) }
    var scene by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var clockError by remember { mutableStateOf("") }
    var clockReady by remember { mutableStateOf(false) }
    var play by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf(emptyList<Array<String>>()) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(draft.style, scene, retry) { clockReady = false; clockError = "" }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { OriginalClockController.catalog(context) } }
            .onSuccess { catalog = it }.onFailure { message = "时钟目录读取失败" }
    }
    BackHandler(saving) { }
    BackHandler(sheet && !saving) { sheet = false }
    Scaffold(contentWindowInsets = WindowInsets(0)) {
        ExtensionEditorContent(draft, saving, scene, clockReady, clockError, message,
            catalog.firstOrNull { it[0] == draft.style }?.get(1) ?: clockTitle(draft.style),
            onBack = onBack, onApply = {
                    saving = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { draft.save(context.applicationContext) } }
                            .onSuccess { onBack() }.onFailure { message = "保存失败，请重试" }
                        saving = false
                    }
                }, onScene = { scene = it }, onChange = { draft = it },
            onStyles = { family = clockFamily(draft.style); sheet = true }, onPlay = { play++ }, onRetry = { retry++ }) {
            key(retry) {
                OriginalClockPreview(draft, scene == 0, Modifier.fillMaxSize().padding(horizontal = 12.dp)) { error ->
                    clockError = error; clockReady = error.isEmpty()
                }
            }
            LocalNotificationPreview(draft, play, Modifier.fillMaxSize()) { message = it }
        }
    }
    WindowBottomSheet(show = sheet, title = "时钟样式", onDismissRequest = { sheet = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 580.dp).padding(horizontal = 12.dp)) {
            AliveSegments(listOf("澎湃", "Flyme", "Vivo"), family) { family = it }
            val styles = catalog.filter { when (family) { 0 -> it[0].contains(".hyperos."); 1 -> it[0].startsWith("flyme-"); else -> it[0].contains(".vivo.") } }
            Text("${styles.size} 款样式 · 选择后在编辑器中预览", fontSize = 12.sp, color = aliveUiColors().secondary,
                modifier = Modifier.padding(vertical = 14.dp))
            LazyVerticalGrid(GridCells.Adaptive(130.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(styles, key = { it[0] }) { style ->
                    ClockStyleTile(style, draft.style == style[0]) {
                        draft = draft.edit(style = style[0]); sheet = false
                    }
                }
            }
        }
    }
}

internal fun ExtensionOptions.edit(clock: Boolean = this.clock, style: String = this.style,
    light: Int = this.light, seconds: Int = this.seconds, color: Int = this.color, scale: Float = this.scale) =
    ExtensionOptions(clock, style, light, seconds, color, scale)

@Composable
internal fun ExtensionEditorContent(options: ExtensionOptions, busy: Boolean, scene: Int, ready: Boolean,
    previewError: String, message: String, title: String, onBack: () -> Unit, onApply: () -> Unit,
    onScene: (Int) -> Unit, onChange: (ExtensionOptions) -> Unit, onStyles: () -> Unit,
    onPlay: () -> Unit, onRetry: () -> Unit, preview: @Composable BoxScope.() -> Unit) {
    val colors = aliveUiColors()
    val artwork = rememberClockArtwork(options.style)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(colors.page)) {
        AliveEditorTopBar("时钟与通知", "取消", if (busy) "保存中" else "应用", !busy,
            !busy && (!options.clock || ready), onBack, onApply, immersive = false)
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(top = 8.dp)
                .clip(RoundedCornerShape(26.dp)).background(Color.Black)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 8.dp).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("样式预览", color = EditorSurface.secondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    listOf("息屏", "锁屏").forEachIndexed { index, label ->
                        Box(Modifier.clip(RoundedCornerShape(12.dp))
                            .selectable(scene == index, enabled = !busy, role = Role.Tab) { onScene(index) }
                            .heightIn(min = 44.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = if (scene == index) Color.White else EditorSurface.secondary.copy(alpha = .6f),
                                fontSize = 12.sp, fontWeight = if (scene == index) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(204.dp).padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag("extension-preview"), contentAlignment = Alignment.Center) {
                    preview()
                    if (!ready && artwork != null) Image(artwork, "所选时钟的静态样式参考", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else if (!ready) Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AliveToolGlyph(AliveToolIcon.CLOCK, Modifier.size(38.dp), EditorSurface.secondary)
                        Text(if (previewError.isEmpty()) "正在加载原版时钟" else "当前样式无法预览",
                            color = EditorSurface.secondary, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFF191C21)).clickable(enabled = !busy, role = Role.Button, onClick = onStyles)
                    .padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title.substringAfter(" · "), color = EditorSurface.text, fontSize = 15.sp)
                        Text(when {
                            ready -> "原版实时预览"
                            artwork == null -> "原版组件，需设备支持"
                            previewError.isEmpty() -> "正在加载 · 先显示样式参考"
                            else -> "静态参考 · 当前样式未能实时加载"
                        },
                            color = EditorSurface.secondary, fontSize = 11.sp)
                    }
                    AliveToolGlyph(AliveToolIcon.STYLE, Modifier.size(20.dp), EditorSurface.secondary)
                }
            }
            if (previewError.isNotEmpty()) Row(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("可切换样式，或重试加载", color = colors.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                EditorTextAction("重试", enabled = !busy, onClick = onRetry)
            }
            else Text(if (scene == 0) "应用于息屏，位置跟随系统时钟" else "锁屏仅预览，不替换系统锁屏时钟",
                color = colors.secondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 8.dp))
            ExtensionControls(options, busy, onChange, onStyles, {
                scope.launch { scroll.animateScrollTo(0); onPlay() }
            }, title)
            Text("实验功能，默认关闭。指纹按下、亮屏或遮挡时让回系统；通知光效遵循系统允许的显示窗口。",
                color = colors.secondary, fontSize = 12.sp, lineHeight = 19.sp,
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 4.dp, vertical = 18.dp))
            if (message.isNotEmpty()) Text(message, color = colors.text, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}

@Composable
internal fun ExtensionControls(options: ExtensionOptions, busy: Boolean, onChange: (ExtensionOptions) -> Unit,
    onStyles: () -> Unit, onPlay: () -> Unit, title: String = clockTitle(options.style)) {
    val colors = aliveUiColors()
    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        EditorSectionLabel("息屏时钟")
        EditorSettingsCard {
        SwitchRow(title = "替换息屏时钟", checked = options.clock, enabled = !busy,
            summary = if (options.clock) "应用后生效，位置跟随系统" else "未开启，当前仅预览样式",
            onCheckedChange = { onChange(options.edit(clock = it)) })
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(colors.text.copy(alpha = .06f)))
        Row(Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button, onClick = onStyles)
            .testTag("extension-styles").padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("时钟样式", fontSize = 15.sp, color = colors.text)
                Text(title, fontSize = 12.sp, color = colors.secondary)
            }
            AliveToolGlyph(AliveToolIcon.CHEVRON, Modifier.size(18.dp), colors.secondary)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorValueSlider("时钟大小", options.scale, .65f..1.35f, "${(options.scale * 100).toInt()}%", enabled = !busy) {
                onChange(options.edit(scale = it))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("显示颜色", fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                Text("时钟与光效共用", fontSize = 11.sp, color = colors.secondary)
            }
            Row(Modifier.fillMaxWidth().selectableGroup().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val names = listOf("月白", "雾蓝", "暖杏", "薄荷", "浅粉")
                listOf(0xffeeeeee.toInt(), 0xffb4d6ff.toInt(), 0xffffd6a0.toInt(), 0xffb9ead2.toInt(), 0xffffbcc9.toInt()).forEachIndexed { index, color ->
                    val selected = options.color == color
                    Box(Modifier.size(48.dp).clip(CircleShape)
                        .selectable(selected = selected, enabled = !busy, role = Role.RadioButton) { onChange(options.edit(color = color)) }
                        .semantics { contentDescription = names[index] }.testTag("extension-color-$index").padding(5.dp)
                        .border(1.5.dp, if (selected) colors.text else Color.Transparent, CircleShape).padding(4.dp)
                        .background(Color(color).copy(alpha = if (busy) .4f else 1f), CircleShape), contentAlignment = Alignment.Center) {
                        if (selected) Box(Modifier.size(5.dp).background(Color(0xFF333A44), CircleShape))
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(12.dp))
        EditorSectionLabel("通知光效")
        EditorSettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AliveSegments(listOf("关闭", "光环", "双侧光带"), options.light, enabled = !busy) { onChange(options.edit(light = it)) }
            if (options.light != 0) {
                Text("播放时长", fontSize = 14.sp, color = colors.text, modifier = Modifier.padding(start = 4.dp))
                AliveSegments(listOf("5 秒", "10 秒", "15 秒"), listOf(5, 10, 15).indexOf(options.seconds), enabled = !busy) {
                    onChange(options.edit(seconds = listOf(5, 10, 15)[it]))
                }
                EditorTextAction("播放光效", enabled = !busy, icon = AliveToolIcon.PLAY, onClick = onPlay)
            }
            Text(if (options.light == 0) "收到通知时不显示额外光效" else "仅在上方预览中播放，不会发送系统通知",
                color = colors.secondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        }
    }
}

@Composable
internal fun ClockStyleTile(style: Array<String>, selected: Boolean, onClick: () -> Unit) {
    val bitmap = rememberClockArtwork(style[0])
    val colors = aliveUiColors()
    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(colors.card)
        .border(2.dp, if (selected) Color(0xFF709EE9) else Color.Transparent, RoundedCornerShape(20.dp))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(6.dp)
        .testTag("clock-style-${style[0]}"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(15.dp)).background(Color.Black).padding(16.dp),
            contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, "样式缩略图：${style[1]}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else if (clockFamily(style[0]) == 1) {
                var failed by remember { mutableStateOf(false) }
                OriginalClockPreview(ExtensionOptions.defaults().edit(style = style[0]), true, Modifier.fillMaxSize()) { failed = it.isNotEmpty() }
                if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AliveToolGlyph(AliveToolIcon.CLOCK, Modifier.size(28.dp), EditorSurface.secondary)
                    Text("预览不可用", fontSize = 11.sp, color = EditorSurface.secondary)
                }
            }
            else AliveToolGlyph(AliveToolIcon.CLOCK, Modifier.size(32.dp), EditorSurface.secondary.copy(alpha = .35f))
        }
        Text(style[1].substringAfter(" · "), fontSize = 13.sp, color = colors.text,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 12.dp))
    }
}

@Composable
private fun OriginalClockPreview(options: ExtensionOptions, aod: Boolean, modifier: Modifier, status: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(status)
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val frame = remember { ClockPreviewFrame(context) }
    AndroidView(factory = { frame }, modifier = modifier)
    val controller = remember(options.style, aod, active) { OriginalClockController(frame.stage) }
    DisposableEffect(controller) {
        if (active) controller.load(context, options, aod, true) { callback(it) }
        onDispose { controller.close() }
    }
    SideEffect { controller.appearance(options.color, options.scale) }
}

@Composable
private fun LocalNotificationPreview(options: ExtensionOptions, play: Int, modifier: Modifier, error: (String) -> Unit) {
    val context = LocalContext.current
    val frame = remember { FrameLayout(context) }
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AndroidView(factory = { frame }, modifier = modifier)
    LaunchedEffect(play, options.light, options.color, active) {
        if (play == 0 || options.light == 0 || !active) return@LaunchedEffect
        var light: FlymeNotificationLight? = null
        try {
            val model = withContext(Dispatchers.IO) { FlymeNotificationLight.load(context, options.light == 1) }
            light = FlymeNotificationLight(context, model, options.light == 1).also {
                it.onFailure { error("原版光效绘制失败") }
                it.setRingColor(options.color); frame.addView(it, FrameLayout.LayoutParams(-1, -1))
                it.layoutForDisplay(frame.width, frame.height, null, 0, 0)
            }
            fun replay() { light?.play { replay() } }
            replay(); delay(options.seconds * 1000L)
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { error("原版光效加载失败，系统设置未改变") }
        catch (_: LinkageError) { error("当前运行环境不支持原版光效") }
        finally { light?.stop(); light = null; frame.removeAllViews() }
    }
}
