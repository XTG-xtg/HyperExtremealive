package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object EditorSurface {
    val page = Color(0xFF000000)
    val panel = Color(0xFF1C1C1E)
    val raised = Color(0xFF2C2C2E)
    val text = Color(0xFFF2F2F2)
    val secondary = Color(0xFF9A9A9E)
    val accent = Color(0xFF3478F6)
}

@Composable
internal fun EditorTextAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: AliveToolIcon? = null,
    onClick: () -> Unit,
) {
    val colors = aliveUiColors()
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = colors.text.copy(alpha = if (enabled) 0.88f else 0.3f)
        if (icon != null) AliveToolGlyph(icon, Modifier.size(18.dp), tint)
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EditorValueSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    val colors = aliveUiColors()
    val scheme = MiuixTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                label,
                color = if (enabled) scheme.primary else colors.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                valueRange = range,
                onValueChange = { if (enabled) onChange(it) },
                enabled = enabled,
                height = 24.dp,
                colors = SliderDefaults.sliderColors(
                    foregroundColor = if (enabled) scheme.primary else colors.text.copy(alpha = 0.22f),
                    backgroundColor = colors.text.copy(alpha = 0.11f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor-slider-$title")
                    .semantics {
                        contentDescription = title
                        if (!enabled) disabled()
                    },
            )
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
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp), content = content)
}

@Composable
internal fun EditorSettingsGroup(
    title: String,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SmallTitle(title)
        Card(Modifier.fillMaxWidth(), insideMargin = insideMargin, content = content)
    }
}

@Composable
internal fun AlivePreviewStatusCard(
    text: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier
            .padding(horizontal = 28.dp)
            .widthIn(min = 180.dp, max = 320.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainer.copy(alpha = 0.90f))
            .border(0.75.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            InfiniteProgressIndicator(size = 24.dp)
        }
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                text = actionLabel,
                enabled = actionEnabled,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onAction,
            )
        }
    }
}
