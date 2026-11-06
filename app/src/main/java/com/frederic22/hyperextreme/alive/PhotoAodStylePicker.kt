package com.frederic22.hyperextreme.alive

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** Preview-local selection; photo references, crops and applied settings remain untouched. */
@Composable
internal fun PhotoAodStylePicker(variant: Int, aodMode: Int, enabled: Boolean,
    shapes: List<ImageBitmap> = emptyList(),
    onSelect: (variant: Int) -> Unit) {
    val labels = listOf("圆景", "留光", "星月", "轻启", "山脉", "银河")
    val selected = if(aodMode == 0) variant - 601 else -1
    Column(Modifier.fillMaxWidth().selectableGroup().testTag("photo-aod-styles"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for(row in 0..1) Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for(index in row*3..row*3+2) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).alpha(if(enabled) 1f else .45f)
                    .selectable(selected = index == selected, enabled = enabled, role = Role.Tab) { onSelect(601+index) }
                    .testTag("photo-aod-style-$index"), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(88.dp)
                        .border(1.5.dp,if(index == selected) Color(0xffb5d5ff) else Color.Transparent,RoundedCornerShape(12.dp))
                        .padding(3.dp)) {
                        PhotoShapePreview(labels[index],shapes.getOrNull(index),Modifier.fillMaxSize())
                    }
                    Text(labels[index],color=if(index == selected) Color(0xffc6dfff) else Color(0xffb0b0b6),fontSize=14.sp,maxLines=1)
                }
            }
        }
    }
}

@Composable
internal fun PhotoShapePreview(title: String, shape: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black),contentAlignment=Alignment.Center) {
        if(shape != null) Image(shape,"$title 息屏造型",Modifier.fillMaxSize().padding(6.dp),contentScale=ContentScale.Fit)
        else Text("生成中…",color=Color(0xffb0b0b6),fontSize=12.sp)
    }
}

@Composable
internal fun PhotoAodStylesPanel(variant: Int, options: PhotoOptions, enabled: Boolean, onSelect: (Int) -> Unit) {
    val app=LocalContext.current.applicationContext
    val key=PhotoStyleThumbnails.key(app,options)
    var images by remember(key) { mutableStateOf(LayoutCache.get(key).orEmpty()) }
    var error by remember(key) { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    DisposableEffect(key,retry) {
        var disposed=false
        error=""
        val job=if(images.isNotEmpty()) null else PhotoStyleThumbnails(app,options) { bitmaps,failure ->
            Handler(Looper.getMainLooper()).post {
                if(disposed) bitmaps.forEach { it.recycle() }
                else {
                    images=bitmaps.map { it.asImageBitmap() }
                    error=failure
                    if(images.isNotEmpty()) LayoutCache.put(key,images)
                }
            }
        }
        onDispose { disposed=true;job?.close() }
    }
    PhotoAodStylePicker(variant,options.aodMode,enabled,images,onSelect)
    if(error.isNotEmpty()) TextButton("缩略图生成失败，点击重试",onClick={retry++})
}
