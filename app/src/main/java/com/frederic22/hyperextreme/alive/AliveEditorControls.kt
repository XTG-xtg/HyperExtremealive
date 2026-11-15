package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text

internal object EditorSurface {
    val page = Color(0xFF111317)
    val panel = Color(0xFF1D2025)
    val raised = Color(0xFF2B2F36)
    val text = Color(0xFFF1F3F7)
    val secondary = Color(0xFFA6ADB9)
    val accent = Color(0xFFB5D5FF)
}

@Composable
internal fun EditorTextAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    icon: AliveToolIcon? = null, onClick: () -> Unit) {
    val colors = aliveUiColors()
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val tint = colors.text.copy(alpha = if (enabled) .85f else .3f)
        if (icon != null) AliveToolGlyph(icon, Modifier.size(18.dp), tint)
        Text(label, color = tint, fontSize = 13.sp)
    }
}

@Composable
internal fun EditorValueSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Float) -> Unit) {
    val colors = aliveUiColors()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(label, color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Slider(value = value, valueRange = range, onValueChange = { if (enabled) onChange(it) }, enabled = enabled, height = 24.dp,
                colors = SliderDefaults.sliderColors(foregroundColor = colors.text.copy(alpha = .36f),
                    backgroundColor = colors.text.copy(alpha = .09f)),
                modifier = Modifier.fillMaxWidth().testTag("editor-slider-$title").semantics {
                    contentDescription = title
                    if (!enabled) disabled()
                })
        }
    }
}

@Composable
internal fun EditorSectionLabel(title: String, subtitle: String? = null) {
    val colors = aliveUiColors()
    Column(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 13.sp, color = colors.secondary)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = colors.secondary)
    }
}

@Composable
internal fun EditorSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(aliveUiColors().card), content = content)
}
