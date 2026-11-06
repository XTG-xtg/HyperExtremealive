package com.frederic22.hyperextreme.alive

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.alive.core.PhotoGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Gestures edit the actual renderer; no second bitmap preview with a different crop. */
@Composable
internal fun PhotoCropEditor(variant: Int, options: PhotoOptions, target: Int,
    onTarget: (Int) -> Unit, onChange: (PhotoOptions) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(options)
    val update by rememberUpdatedState(onChange)
    val file = PhotoMedia.resolve(context, options, true, target)
    var imageAspect by remember(file) { mutableFloatStateOf(1f) }
    LaunchedEffect(file) {
        imageAspect = withContext(Dispatchers.IO) {
            val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, info)
            if(info.outWidth > 0 && info.outHeight > 0) info.outWidth.toFloat() / info.outHeight else 1f
        }
    }
    val aspect by rememberUpdatedState(imageAspect)
    val keys = when(target) { 1 -> intArrayOf(11,12,13); 2 -> intArrayOf(4,5,6); else -> intArrayOf(1,2,3) }
    val zoom = when(target) { 1 -> if(options.followLock) options.zoom else options.homeZoom; 2 -> 1f/options.frameSize; else -> options.zoom }
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pointerInput(target) {
            detectTransformGestures { focus, pan, factor, rotation ->
                val o = current.editableCrop(target)
                // Following is broken only by an explicit home edit, not by merely opening the editor.
                val x = when(target) { 1 -> o.homeX; 2 -> o.frameX; else -> o.x }
                val y = when(target) { 1 -> o.homeY; 2 -> o.frameY; else -> o.y }
                val z = when(target) { 1 -> o.homeZoom; 2 -> 1f/o.frameSize; else -> o.zoom }
                val width = size.width.toFloat().coerceAtLeast(1f)
                val height = size.height.toFloat().coerceAtLeast(1f)
                val square = width * .44f
                val values = PhotoGeometry.gesture(x,y,z,aspect,if(target == 2) 1f else width/height,
                    pan.x/(if(target == 2) square else width),pan.y/(if(target == 2) square else height),
                    if(target == 2) .5f else focus.x/width,if(target == 2) .5f else focus.y/height,factor)
                var next = o.edit(keys[0],values[0]).edit(keys[1],values[1]).edit(keys[2],values[2])
                if(target == 2) next = next.edit(7,o.frameAngle-rotation)
                update(next)
            }
        })
        AliveEditorTopBar("照片取景", "返回", "完成", true, true, onDone, onDone,
            Modifier.align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max=600.dp).fillMaxWidth()
            .clip(RoundedCornerShape(topStart=24.dp,topEnd=24.dp)).background(Color(0xff1c1c1e))
            .heightIn(max=300.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal=20.dp,vertical=12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AliveSegments(if(variant == 602) listOf("锁屏", "桌面", "相框") else listOf("锁屏", "桌面"),target,onSelect=onTarget)
            Text(if(target == 2) "拖动、双指缩放及旋转" else "拖动调整位置，双指缩放", style = MiuixTheme.textStyles.footnote1,
                color=Color(0xffb0b0b6),modifier=Modifier.padding(top=8.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("缩放 · ${"%.1f".format(zoom)}×",style=MiuixTheme.textStyles.body2)
                CropAction("复位",onClick={onChange(current.editableCrop(target).edit(keys[0],.5f).edit(keys[1],.5f).edit(keys[2],1f).let { if(target==2) it.edit(7,0f) else it })})
            }
            Slider(value=zoom,valueRange=1f..4f,onValueChange={
                onChange(current.editableCrop(target).edit(keys[2],it))
            })
            if(target == 2) {
                Text("角度 · ${"%.0f".format(options.frameAngle)}°",style=MiuixTheme.textStyles.body2)
                Slider(value=options.frameAngle,valueRange=-45f..45f,onValueChange={onChange(options.edit(7,it))})
            }
        }
    }
}

@Composable
private fun CropAction(label: String, primary: Boolean=false, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable(role=Role.Button,onClick=onClick)
        .heightIn(min=48.dp).padding(horizontal=12.dp),contentAlignment=Alignment.Center) {
        Text(label,color=if(primary) Color(0xff9dc8ff) else Color.White,style=MiuixTheme.textStyles.body2)
    }
}
