package com.frederic22.hyperextreme.alive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import java.util.concurrent.TimeUnit

/** Gesture geometry is the renderer's PhotoViewport, never a separately cropped preview image. */
@Composable
internal fun VivoCropEditor(options: VivoOptions, onChange: (VivoOptions) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val current by rememberUpdatedState(options)
    val update by rememberUpdatedState(onChange)
    var dimensions by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var failure by remember { mutableStateOf("") }
    LaunchedEffect(options.photos.toList()) {
        try {
            dimensions = withContext(Dispatchers.IO) {
                VivoImages.load(context, VivoEngineRuntime.prepare(context).get(20, TimeUnit.SECONDS), options).use {
                    it.photos.first().let { photo -> photo.width to photo.height }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { failure = "照片加载失败：${e.message}" }
    }
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pointerInput(dimensions) {
            detectTransformGestures { focus, pan, factor, _ ->
                val image = dimensions ?: return@detectTransformGestures
                val width = size.width.coerceAtLeast(1)
                val height = size.height.coerceAtLeast(1)
                val viewport = current.viewport().apply {
                    dimensions(image.first, image.second, width, height)
                    drag(pan.x / width, pan.y / height)
                    scale(factor, focus.x / width, focus.y / height)
                }
                update(current.edited { x = viewport.centerX(); y = viewport.centerY(); zoom = viewport.zoom() })
            }
        })
        AliveEditorTopBar("壁纸取景", "返回", "完成", true, true, onDone, onDone, Modifier.align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(EditorSurface.panel)
            .heightIn(max = 260.dp).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("取景调整", fontSize = 15.sp, color = EditorSurface.text, modifier = Modifier.weight(1f))
                EditorTextAction("复位取景", enabled = dimensions != null, icon = AliveToolIcon.UNDO,
                    onClick = { onChange(current.edited { x = .5f; y = .5f; zoom = 1f }) })
            }
            Text(failure.ifEmpty { if (dimensions == null) "正在加载照片…" else "拖动位置 · 双指缩放 · 主体同步对齐" },
                color = EditorSurface.secondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
            EditorValueSlider("缩放", options.zoom, 1f..8f, "%.1f×".format(options.zoom), enabled = dimensions != null) {
                onChange(current.edited { zoom = it })
            }
        }
    }
}
