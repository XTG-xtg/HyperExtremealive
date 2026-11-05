package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.R
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PhotoControls(variant: Int, options: PhotoOptions, section: String, onPreviewMode: (Int) -> Unit, onChange: (PhotoOptions) -> Unit) {
    var target by remember(variant, section) { mutableIntStateOf(0) }
    fun update(key: Int, value: Float) = onChange(PhotoOptions(
        if (key == 0) value.toInt() else options.lockStyle,
        if (key == 1) value else options.x, if (key == 2) value else options.y,
        if (key == 3) value else options.zoom,
        if (key == 4) value else options.frameX, if (key == 5) value else options.frameY,
        if (key == 6) 1f / value else options.frameSize,
        if (key == 7) value else options.frameAngle,
        if (key == 8) value else options.aodOffsetX, if (key == 9) value else options.aodOffsetY))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (section) {
            "texture" -> {
                AliveSegments(stringArrayResource(R.array.alive_photo_lock_styles).toList(), options.lockStyle) { update(0, it.toFloat()) }
                Text("只改变锁屏纹理，桌面保留原图。", style = MiuixTheme.textStyles.body2)
            }
            "position" -> {
                CropSlider("水平位置", options.aodOffsetX, -.2f.. .2f) { update(8, it) }
                CropSlider("垂直位置", options.aodOffsetY, -.02f.. .4f) { update(9, it) }
                Text("移动整个圆球或相框，不是裁切照片。位置按屏幕比例保存；暂不跟随系统时钟，也不包含防烧屏位移。", style = MiuixTheme.textStyles.body2)
                TextButton(text = "恢复默认位置", onClick = { onChange(PhotoOptions(options.lockStyle, options.x, options.y, options.zoom,
                    options.frameX, options.frameY, options.frameSize, options.frameAngle, 0f, 0f)) })
            }
            else -> {
                if (variant == 602) AliveSegments(stringArrayResource(R.array.alive_photo_crop_targets).toList(), target) {
                    target = it; onPreviewMode(if (it == 1) 0 else 1)
                }
                CropSlider(stringResource(R.string.alive_photo_x), if (target == 0) options.x else options.frameX, 0f..1f) { update(if (target == 0) 1 else 4, it) }
                CropSlider(stringResource(R.string.alive_photo_y), if (target == 0) options.y else options.frameY, 0f..1f) { update(if (target == 0) 2 else 5, it) }
                CropSlider(stringResource(R.string.alive_photo_zoom), if (target == 0) options.zoom else 1f / options.frameSize, 1f..4f) { update(if (target == 0) 3 else 6, it) }
                if (target == 1) CropSlider(stringResource(R.string.alive_photo_angle), options.frameAngle, -45f..45f) { update(7, it) }
                TextButton(text = "恢复取景", onClick = { onChange(PhotoOptions(options.lockStyle, .5f, .5f, 1f, .5f, .5f, 1f, 0f, options.aodOffsetX, options.aodOffsetY)) })
            }
        }
    }
}

@Composable
private fun CropSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    Slider(value = value, valueRange = range, onValueChange = onChange, modifier = Modifier.fillMaxWidth())
}
