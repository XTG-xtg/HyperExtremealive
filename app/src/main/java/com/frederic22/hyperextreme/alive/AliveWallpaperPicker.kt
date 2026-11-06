package com.frederic22.hyperextreme.alive

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val aliveDynamicGroups = listOf(-1, 0, 2, 1, 3, 4, 6)
internal val aliveDynamicLabels = listOf("全部", "启航", "流光", "轻启", "视频", "立体", "Vivo")

internal fun previewVivoSceneKey(options: VivoOptions): String = VivoOptions.parse(options.json()).apply {
    sensitivity = 0; wallpaperZoom = true; aodAnimation = true
}.json()

/** Saves only drafts; never changes the applied variant or publishes a wallpaper. */
internal fun switchPreviewVivo(context: Context, currentVariant: Int, nextVariant: Int, current: VivoOptions): VivoOptions {
    if(currentVariant in 701..703) {
        check(current.family == currentVariant - 700)
        current.saveDraft(context)
    }
    return if(nextVariant in 701..703) VivoOptions.load(context, nextVariant, true) else current
}

@Composable
internal fun AliveWallpaperPicker(variant: Int, photo: PhotoOptions, enabled: Boolean, onSelect: (AliveCatalog.Scene) -> Unit) {
    val app = LocalContext.current.applicationContext
    var scenes by remember { mutableStateOf<List<AliveCatalog.Scene>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        loading = true; error = ""
        val result = withContext(Dispatchers.IO) { runCatching { AliveCatalog.load(app) } }
        scenes = result.getOrDefault(emptyList()); error = result.exceptionOrNull()?.message.orEmpty(); loading = false
    }
    when {
        loading -> Text("正在加载壁纸…", modifier = Modifier.padding(20.dp))
        error.isNotEmpty() || scenes.isEmpty() -> TextButton("壁纸加载失败，点击重试", onClick = { retry++ })
        else -> AliveWallpaperPickerContent(variant, scenes, enabled, onSelect) {
            PhotoAodStylesPanel(variant, photo, enabled) { id -> scenes.firstOrNull { it.id == id }?.let(onSelect) }
        }
    }
}

/** Reuses library thumbnails; a bounded grid owns scrolling inside the existing Miuix sheet. */
@Composable
internal fun AliveWallpaperPickerContent(variant: Int, scenes: List<AliveCatalog.Scene>, enabled: Boolean,
    onSelect: (AliveCatalog.Scene) -> Unit, photos: @Composable () -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(if(variant in 601..606) 0 else 1) }
    var group by rememberSaveable { mutableIntStateOf(0) }
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).testTag("alive-wallpaper-picker"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AliveSegments(listOf("照片壁纸", "动态壁纸"), section) { section = it }
        AnimatedContent(targetState = section, modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            transitionSpec = {
                val direction = if(targetState > initialState) 1 else -1
                ((fadeIn(tween(220, 60)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { direction * it / 12 })
                    togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -direction * it / 12 }))
                    .using(SizeTransform(clip = true) { _, _ -> tween(280, easing = FastOutSlowInEasing) })
            }, label = "wallpaper-picker-section") { selectedSection ->
        if(selectedSection == 0) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { photos() }
        else Column(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                aliveDynamicLabels.forEachIndexed { index, label ->
                    Box(Modifier.clip(RoundedCornerShape(24.dp))
                        .background(if(group == index) Color(0xff414144) else Color.Transparent)
                        .selectable(selected = group == index, role = Role.Tab, onClick = { group = index })
                        .testTag("alive-picker-category-$index").heightIn(min = 44.dp).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 14.sp, color = if(group == index) Color.White else Color(0xffb0b0b6))
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = if(fontScale >= 1.3f || maxWidth < 340.dp) 2 else 3
                key(group) {
                    LazyVerticalGrid(GridCells.Fixed(columns), modifier = Modifier.fillMaxSize().testTag("alive-picker-grid"),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(scenes.filter { it.group != 5 && (group == 0 || it.group == aliveDynamicGroups[group]) }, key = { it.id }) { scene ->
                            Column(Modifier.clip(RoundedCornerShape(12.dp))
                                .selectable(selected = scene.id == variant, enabled = enabled, role = Role.Tab, onClick = { onSelect(scene) })
                                .testTag("alive-picker-scene-${scene.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                                AliveThumbnail(scene, 0, Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(12.dp))
                                    .then(if(scene.id == variant) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier))
                                Text(scene.name, fontSize = 13.sp, textAlign = TextAlign.Center,
                                    color = if(scene.id == variant) Color(0xffc6dfff) else Color.White,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
