package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.R
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PhotoControls(variant: Int, options: PhotoOptions, section: String, previewMode: Int,
    onPreviewMode: (Int) -> Unit, onChange: (PhotoOptions) -> Unit) {
    var target by remember(variant, section) { mutableIntStateOf(0) }
    fun update(key: Int, value: Float) = onChange(options.edit(key, value))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (section) {
            "texture" -> {
                if(previewMode == 2) {
                    AliveSegments(listOf("原图", "条纹", "曲面", "磨砂"), options.homeStyle) { update(10, it.toFloat()) }
                } else {
                    AliveSegments(listOf("原图", "条纹", "曲面"), if(options.lockStyle < 3) options.lockStyle else -1) { update(0, it.toFloat()) }
                    AliveSegments(listOf("磨砂", "纯色", "颜色蒙版"), if(options.lockStyle >= 3) options.lockStyle - 3 else -1) { update(0, (it + 3).toFloat()) }
                }
                if(previewMode != 2 && options.lockStyle >= 4) {
                    Text("蒙版与相框底色", style = MiuixTheme.textStyles.body2)
                    val colors = listOf(0xff264552.toInt(), 0xffc49980.toInt(), 0xff5c6454.toInt(), 0xff272727.toInt())
                    AliveSegments(listOf("青灰", "砂岩", "苔绿", "墨黑"), colors.indexOf(options.tint)) { onChange(options.color(colors[it])) }
                }
                Text(if(previewMode == 2) "仅调整桌面纹理，不改变锁屏。" else "仅调整锁屏纹理，不改变桌面。", style = MiuixTheme.textStyles.body2)
            }
            "position" -> {
                CropSlider("水平位置", options.aodOffsetX, -.2f.. .2f) { update(8, it) }
                CropSlider("垂直位置", options.aodOffsetY, -.02f.. .4f) { update(9, it) }
                Text("移动整个息屏图案。开启跟随系统时钟后，这里的位置作为微调叠加；真实时钟的防烧屏位移会同步到壁纸。未获取到系统位置时使用小幅防烧屏位移。", style = MiuixTheme.textStyles.body2)
                TextButton(text = "恢复默认位置", onClick = { onChange(options.edit(8, 0f).edit(9, 0f)) })
            }
            else -> {
                AliveSegments(if(variant == 602) listOf("锁屏", "桌面", "相框") else listOf("锁屏", "桌面"), target) {
                    target = it; onPreviewMode(if (it == 2) 0 else it + 1)
                }
                val keys = when(target) { 1 -> intArrayOf(11,12,13); 2 -> intArrayOf(4,5,6); else -> intArrayOf(1,2,3) }
                val values = when(target) { 1 -> floatArrayOf(options.homeX,options.homeY,options.homeZoom); 2 -> floatArrayOf(options.frameX,options.frameY,1f/options.frameSize); else -> floatArrayOf(options.x,options.y,options.zoom) }
                CropSlider(stringResource(R.string.alive_photo_x), values[0], 0f..1f) { update(keys[0], it) }
                CropSlider(stringResource(R.string.alive_photo_y), values[1], 0f..1f) { update(keys[1], it) }
                CropSlider(stringResource(R.string.alive_photo_zoom), values[2], 1f..4f) { update(keys[2], it) }
                if (target == 2) CropSlider(stringResource(R.string.alive_photo_angle), options.frameAngle, -45f..45f) { update(7, it) }
                TextButton(text = "恢复取景", onClick = { onChange(options.edit(keys[0], .5f).edit(keys[1], .5f).edit(keys[2], 1f).edit(7, 0f)) })
            }
        }
    }
}

@Composable
private fun CropSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    Slider(value = value, valueRange = range, onValueChange = onChange, modifier = Modifier.fillMaxWidth())
}
