package com.frederic22.hyperextreme.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.alive.AodBrightness
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

private val brightnessTicks = (0..20).map { it * AodBrightness.STEP }
internal fun brightnessLabel(value: Float): String = String.format(Locale.ROOT, "%.2f", AodBrightness.normalize(value))
    .trimEnd('0').trimEnd('.')

@Composable
internal fun AodBrightnessControl(value: Float, onChange: (Float) -> Unit) {
    val level = AodBrightness.normalize(value)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("息屏亮度", style = MiuixTheme.textStyles.body1)
            Text(if(level == 0f) "0 · 不修改" else "${brightnessLabel(level)}×",
                style = MiuixTheme.textStyles.body1, modifier = Modifier.testTag("aod-brightness-value"))
        }
        Slider(value = level, valueRange = 0f..5f, steps = 19,
            showKeyPoints = true, keyPoints = brightnessTicks, magnetThreshold = 0f,
            onValueChange = { onChange((it / AodBrightness.STEP).roundToInt() * AodBrightness.STEP) },
            modifier = Modifier.fillMaxWidth().testTag("aod-brightness-slider"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for(tick in 0..5) Text(tick.toString(), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Text("0 保留原始亮度，每格 0.25。跟随系统自动亮度，暗处按设定倍率增强，亮处减少增强；最高 5 倍。", style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

