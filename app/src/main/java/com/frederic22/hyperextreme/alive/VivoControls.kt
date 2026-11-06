package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.ui.SwitchRow
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VivoControls(options: VivoOptions, onChange: (VivoOptions) -> Unit, onImport: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit, onMask: (Int) -> Unit) {
    var slot by rememberSaveable { mutableIntStateOf(0) }
    fun edit(block: VivoOptions.() -> Unit) { onChange(VivoOptions.parse(options.json()).apply(block)) }
    val raster = options.family == VivoOptions.RASTER
    val flash = options.family == VivoOptions.FLASH
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("原版效果", style=MiuixTheme.textStyles.headline2)
        AliveSegments((1..if(flash) 5 else 4).map { "效果 $it" },options.style-1) { edit { style=it+1 } }
        if(options.family == VivoOptions.LIQUID && options.style > 1) {
            Text("折射强度", style=MiuixTheme.textStyles.body2)
            AliveSegments(listOf("一档", "二档", "三档", "四档"),options.refraction-1) { edit { refraction=it+1 } }
        }
        if(flash && options.style == 3) {
            val ids=listOf("circle","fire","flower","heart","hexagon","leaf","light","luck","music","paper-cut","snow","star","thunder","water","wings")
            val names=listOf("圆形","火焰","花朵","爱心","六边形","叶片","光芒","幸运","音乐","剪纸","雪花","星星","闪电","水滴","翅膀")
            ids.indices.chunked(3).forEach { indices ->
                AliveSegments(indices.map { names[it] },indices.indexOf(ids.indexOf(options.pattern.removePrefix("d5_pattern_")))) { choice -> edit { pattern="d5_pattern_${ids[indices[choice]]}" } }
            }
        }
        Text("传感器灵敏度 · ${options.sensitivity}", style=MiuixTheme.textStyles.body2)
        Slider(value=options.sensitivity.toFloat(),valueRange=-50f..50f,onValueChange={ edit { sensitivity=it.toInt() } })
        SwitchRow(title="系统三态缩放",summary="仅缩放壁纸，不移动系统时钟或小组件",checked=options.wallpaperZoom,onCheckedChange={ edit { wallpaperZoom=it } })
        SwitchRow(title="Vivo 息屏动画",summary="仍受总息屏动画开关限制；隐藏时停用传感器与视频解码",checked=options.aodAnimation,onCheckedChange={ edit { aodAnimation=it } })
        if(raster) {
            Text("三个独立素材位置",style=MiuixTheme.textStyles.headline2)
            AliveSegments(listOf("封面", "光栅 1", "光栅 2"),slot) { slot=it }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton("选择图片",onClick={ onImport(VivoMediaSelection.SLOT_IMAGE+slot) })
                TextButton("选择视频",onClick={ onImport(VivoMediaSelection.SLOT_VIDEO+slot) })
            }
            Text(if(options.rasterVideos.getOrNull(slot).isNullOrEmpty()) "此位置使用静态图片" else "此位置使用视频及其独立封面",style=MiuixTheme.textStyles.body2)
            val crop=options.slots[slot]
            Text("水平取景",style=MiuixTheme.textStyles.body2)
            Slider(value=crop.x,valueRange=0f..1f,onValueChange={ edit { slots[slot].x=it } })
            Text("垂直取景",style=MiuixTheme.textStyles.body2)
            Slider(value=crop.y,valueRange=0f..1f,onValueChange={ edit { slots[slot].y=it } })
            Text("缩放 · ${"%.1f".format(crop.zoom)}×",style=MiuixTheme.textStyles.body2)
            Slider(value=crop.zoom,valueRange=1f..8f,onValueChange={ edit { slots[slot].zoom=it } })
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton("旋转 90°",onClick={ edit { slots[slot].rotation=(slots[slot].rotation+1)%4 } })
                TextButton("复位取景",onClick={ edit { slots[slot]=VivoOptions.Slot() } })
                TextButton("与下一位置交换",onClick={ onSwap(slot,(slot+1)%3) })
            }
        } else {
            TextButton("选择壁纸照片",onClick={ onImport(VivoMediaSelection.WALLPAPER) })
            Text("效果范围",style=MiuixTheme.textStyles.body2)
            val hasSubject=options.photos.isEmpty() || options.subject.isNotEmpty()
            val hasPaint=options.paint.isNotEmpty() || flash && options.photos.isEmpty()
            AliveSegments(listOf("涂抹", "主体", "背景", "全图"),options.area-1) {
                when { it==0 && !hasPaint -> onMask(1)
                    it in 1..2 && !hasSubject -> onMask(it+1)
                    else -> edit { area=it+1 } }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton("编辑主体蒙版",onClick={ onMask(if(options.area==3) 3 else 2) })
                TextButton("编辑涂抹范围",onClick={ onMask(1) })
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton("导入主体蒙版",onClick={ onImport(VivoMediaSelection.SUBJECT) })
                TextButton("导入涂抹蒙版",onClick={ onImport(VivoMediaSelection.PAINT) })
            }
            Text("主体识别完全在本机完成；可以框选、涂抹、擦除和撤销。导入蒙版时，白色启用、黑色排除，并应与原图对齐。",style=MiuixTheme.textStyles.body2)
            Text("照片取景",style=MiuixTheme.textStyles.body2)
            Slider(value=options.x,valueRange=0f..1f,onValueChange={ edit { x=it } })
            Slider(value=options.y,valueRange=0f..1f,onValueChange={ edit { y=it } })
            Slider(value=options.zoom,valueRange=1f..8f,onValueChange={ edit { zoom=it } })
        }
        TextButton("恢复出厂素材与效果（仅草稿）",onClick={onChange(VivoMediaSelection.defaults(options))})
        Text("Vivo 原始运行时只在应用内加载，不替换系统包。所有修改均为草稿，确认应用后才改变实际壁纸。",style=MiuixTheme.textStyles.body2)
    }
}
