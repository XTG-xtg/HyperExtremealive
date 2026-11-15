package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.ui.DropdownRow
import com.frederic22.hyperextreme.ui.LinkRow
import com.frederic22.hyperextreme.ui.SwitchRow
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun vivoTitle(variant: Int) = when (variant) {
    701 -> "液态壁纸"
    702 -> "趣味光栅"
    703 -> "闪卡壁纸"
    else -> "Alive 壁纸"
}

internal fun VivoOptions.edited(block: VivoOptions.() -> Unit): VivoOptions =
    VivoOptions.parse(json()).apply(block)

@Composable
internal fun VivoSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SmallTitle(title)
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp), content = content)
    }
}

@Composable
internal fun VivoControls(options: VivoOptions, onChange: (VivoOptions) -> Unit) {
    val flash = options.family == VivoOptions.FLASH
    val styles = (1..if (flash) 5 else 4).map { "原版效果 $it" }
    if (!flash) DropdownRow("效果样式", styles, options.style - 1, onSelect = { onChange(options.edited { style = it + 1 }) },
        summary = if (options.family == VivoOptions.LIQUID) "轻点选区播放，区域外保持原图" else "使用 vivo 原版渲染")
    if (options.family == VivoOptions.LIQUID && options.style > 1) {
        DropdownRow("折射强度", listOf("一档 · 轻微", "二档", "三档", "四档 · 强烈"), options.refraction - 1,
            onSelect = { onChange(options.edited { refraction = it + 1 }) }, summary = "只影响支持折射的效果，不改变主体蒙版")
    }
    if (flash && options.style == 3) {
        val ids = listOf("circle", "fire", "flower", "heart", "hexagon", "leaf", "light", "luck", "music", "paper-cut", "snow", "star", "thunder", "water", "wings")
        DropdownRow("光斑图案", listOf("圆形", "火焰", "花朵", "爱心", "六边形", "叶片", "光芒", "幸运", "音乐", "剪纸", "雪花", "星星", "闪电", "水滴", "翅膀"),
            ids.indexOf(options.pattern.removePrefix("d5_pattern_")).coerceAtLeast(0),
            onSelect = { onChange(options.edited { pattern = "d5_pattern_${ids[it]}" }) })
    }
    if (flash) VivoFlashStylePicker(options.style) { onChange(options.edited { style = it }) }
}

@Composable
internal fun VivoPhotoControls(options: VivoOptions, onChange: (VivoOptions) -> Unit, onImport: (Int) -> Unit,
    onCrop: () -> Unit, onSwap: (Int, Int) -> Unit) {
    if (options.family != VivoOptions.RASTER) {
        LinkRow("更换壁纸", if (options.photos.isEmpty()) "使用内置壁纸，点此选择照片" else "使用自选照片；换图将重置蒙版") {
            onImport(VivoMediaSelection.WALLPAPER)
        }
        LinkRow("调整取景", "拖动与缩放，主体同步对齐", onCrop)
    } else {
        var slot by rememberSaveable { mutableIntStateOf(0) }
        DropdownRow("素材位置", listOf("封面", "光栅 1", "光栅 2"), slot, onSelect = { slot = it },
            summary = "每个位置分别保存照片、视频、取景和旋转")
        LinkRow("选择图片", "替换当前槽位；保留其他位置") { onImport(VivoMediaSelection.SLOT_IMAGE + slot) }
        LinkRow("选择视频", if (options.rasterVideos.getOrNull(slot).isNullOrEmpty()) "当前为静态图片" else "当前为视频，暂停时显示独立封面") {
            onImport(VivoMediaSelection.SLOT_VIDEO + slot)
        }
        val crop = options.slots[slot]
        VivoValueSlider("水平取景", crop.x, 0f..1f, "${(crop.x * 100).toInt()}%") { onChange(options.edited { slots[slot].x = it }) }
        VivoValueSlider("垂直取景", crop.y, 0f..1f, "${(crop.y * 100).toInt()}%") { onChange(options.edited { slots[slot].y = it }) }
        VivoValueSlider("缩放", crop.zoom, 1f..8f, "%.1f×".format(crop.zoom)) { onChange(options.edited { slots[slot].zoom = it }) }
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("旋转 90°", onClick = { onChange(options.edited { slots[slot].rotation = (slots[slot].rotation + 1) % 4 }) })
            TextButton("复位取景", onClick = { onChange(options.edited { slots[slot] = VivoOptions.Slot() }) })
            TextButton("与下一位置交换", onClick = { onSwap(slot, (slot + 1) % 3) })
        }
    }
}

@Composable
internal fun VivoAreaControls(options: VivoOptions, onChange: (VivoOptions) -> Unit, onMask: (Int) -> Unit,
    onRecognize: (Int) -> Unit, onImport: (Int) -> Unit) {
    var exportStatus by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val export = rememberVivoMaskExport(options) {
        exportStatus = it
        com.frederic22.hyperextreme.ui.AppToast.show(context, it, long = true)
    }
    val hasSubject = options.photos.isEmpty() || options.subject.isNotEmpty()
    val hasPaint = options.paint.isNotEmpty() || options.family == VivoOptions.FLASH && options.photos.isEmpty()
    DropdownRow("效果范围", listOf("涂抹区域", "仅主体", "仅背景", "整张壁纸"), options.area - 1,
        summary = "选择哪一部分产生动态效果", onSelect = { index ->
            when {
                index == 0 && !hasPaint -> onMask(1)
                index in 1..2 && !hasSubject -> onRecognize(index + 1)
                else -> onChange(options.edited { area = index + 1 })
            }
        })
    LinkRow(if (hasSubject) "重新识别壁纸主体" else "识别壁纸主体",
        when { options.subject.isNotEmpty() -> options.subjectSource.ifEmpty { "已保存主体蒙版" }
            options.photos.isEmpty() -> "内置主体已就绪"
            else -> "识别照片中的主要对象" }) { onRecognize(if (options.area == 3) 3 else 2) }
    LinkRow("修整主体", "画笔补充、擦除修边、框选识别") { onMask(if (options.area == 3) 3 else 2) }
    LinkRow("涂抹效果区域", "手动画出选区，不覆盖主体蒙版") { onMask(1) }
    BasicComponent(title = "导出主体蒙版", summary = if (hasSubject) "原图尺寸 PNG，不反选" else "识别主体后可导出",
        enabled = hasSubject, onClick = { export(true) })
    BasicComponent(title = "导出涂抹蒙版", summary = if (hasPaint) "PNG 格式，白色为效果区域" else "涂抹效果区域后可导出",
        enabled = hasPaint, onClick = { export(false) })
    if (exportStatus.isNotEmpty()) BasicComponent(title = "蒙版导出", summary = exportStatus)
    DropdownRow("主体识别引擎", listOf("自动", "仅系统 AI", "仅本地 AI"), options.subjectEngine,
        onSelect = { onChange(options.edited { subjectEngine = it }) },
        summary = "优先系统 AI，本地识别离线完成")
    SwitchRow("换图后自动识别主体", summary = "失败时保留照片，可手动修整",
        checked = options.autoSubject, onCheckedChange = { onChange(options.edited { autoSubject = it }) })
    if (options.subjectDetail.isNotEmpty()) BasicComponent(title = "上次识别详情", summary = options.subjectDetail)
    LinkRow("导入主体蒙版", "支持透明抠图或黑白蒙版，比例同原图") { onImport(VivoMediaSelection.SUBJECT) }
    LinkRow("导入涂抹蒙版", "白色启用效果，黑色保持不动") { onImport(VivoMediaSelection.PAINT) }
}

@Composable
internal fun VivoMotionControls(options: VivoOptions, onChange: (VivoOptions) -> Unit) {
    if (options.family == VivoOptions.LIQUID) {
        BasicComponent(title = "轻点选区播放", summary = "锁屏与预览使用同一选区；选区内不触发双击息屏，选区外保留系统手势。滑动解锁不受影响。")
        SwitchRow("亮屏与倾斜自动播放", summary = "默认关闭，仅轻点选区触发；开启后额外保留 vivo 原版自动动作",
            checked = options.liquidAutoMotion, onCheckedChange = { onChange(options.edited { liquidAutoMotion = it }) })
    }
    VivoValueSlider("传感器灵敏度", options.sensitivity.toFloat(), -50f..50f,
        if (options.sensitivity == 0) "0 · 原版" else "%+d".format(options.sensitivity)) {
        onChange(options.edited { sensitivity = it.toInt() })
    }
    BasicComponent(title = "动作响应", summary = "负值更柔和，正值更灵敏；调整后可在预览中倾斜手机检查")
    SwitchRow("系统三态缩放", summary = "只缩放壁纸，不移动系统时钟或小组件", checked = options.wallpaperZoom,
        onCheckedChange = { onChange(options.edited { wallpaperZoom = it }) })
    SwitchRow("息屏效果动画", summary = "仍受总息屏动画开关限制；不可见时停用传感器与视频解码", checked = options.aodAnimation,
        onCheckedChange = { onChange(options.edited { aodAnimation = it }) })
}

@Composable
internal fun VivoValueSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    label: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        EditorValueSlider(title, value, range, label, onChange = onChange)
    }
}
