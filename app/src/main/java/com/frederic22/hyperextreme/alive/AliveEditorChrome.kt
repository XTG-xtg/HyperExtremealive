package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class AliveToolIcon { PHOTO, STYLE, POSITION, CROP, TEXTURE, OPTIONS, PLAY, STOP, BRUSH, ERASER, SELECT, UNDO, CLEAR, EYE, SPARK, CLOCK, CHEVRON }
internal data class AliveEditorTool(val label: String, val icon: AliveToolIcon, val enabled: Boolean = true, val onClick: () -> Unit)

/** Pure UI: this layer cannot write config or signal a wallpaper. */
@Composable
internal fun AliveEditorChrome(
    title: String,
    mode: Int,
    busy: Boolean,
    canApply: Boolean,
    message: String,
    tools: List<AliveEditorTool>,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onMode: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AliveEditorTopBar(title, "取消", if(busy) "处理中" else "应用", !busy, !busy && canApply,
            onCancel, onApply, Modifier.align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .68f))))
            .navigationBarsPadding().padding(top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.widthIn(max = 300.dp).fillMaxWidth(.72f).clip(RoundedCornerShape(28.dp))
                .background(Color(0xD92A2A2C)).padding(4.dp).selectableGroup().testTag("alive-editor-modes")) {
                listOf("息屏", "锁屏", "桌面").forEachIndexed { index, label ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                        .background(if (index == mode) Color(0xFF4A4A4C) else Color.Transparent)
                        .selectable(selected = index == mode, enabled = !busy, role = Role.Tab,
                            onClick = { onMode(index) }).heightIn(min = 48.dp).padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label, color = if (index == mode) Color.White else Color(0xFFB5B5BA),
                            fontSize = 15.sp, fontWeight = if (index == mode) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            // Up to five photo tools: scroll rather than shrinking tap targets.
            Row(Modifier.widthIn(max = 600.dp).horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tools.forEach { tool ->
                    Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                        .testTag("alive-tool-${tool.label}")
                        .clickable(enabled = !busy && tool.enabled, role = Role.Button, onClick = tool.onClick)
                        .padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(52.dp).background(Color(0xE6232326), CircleShape), contentAlignment = Alignment.Center) {
                            AliveToolGlyph(tool.icon, Modifier.size(24.dp),
                                if (!busy && tool.enabled) Color(0xFFB5D5FF) else Color(0xFF65656B))
                        }
                        Text(tool.label, color = if (!busy && tool.enabled) Color.White else Color(0xFF98989E),
                            fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            if (message.isNotEmpty()) Text(message, color = Color(0xFFD1D1D6), fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
internal fun AliveEditorTopBar(title: String, startLabel: String, endLabel: String,
    startEnabled: Boolean, endEnabled: Boolean, onStart: () -> Unit, onEnd: () -> Unit,
    modifier: Modifier = Modifier, immersive: Boolean = true) {
    Row(modifier.fillMaxWidth()
        .then(if (immersive) Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .4f), Color.Transparent))) else Modifier)
        .statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        EditorPill(startLabel, startEnabled, false, onStart, immersive)
        Text(title, color = if (immersive) Color.White else MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        EditorPill(endLabel, endEnabled, true, onEnd, immersive)
    }
}

/**
 * 编辑器面板里的分段选择器（2–4 项）。
 *
 * 面板背景是圆角卡片，直接用 Miuix 的 TabRow 会把未选中项画成描边黑块、
 * 文字贴在顶部、选中块比轨道高，所以这里自绘：等宽分段、文字垂直居中、
 * 只有一条轨道和一个实心滑块，和编辑器底部“息屏/锁屏/桌面”保持一致。
 */
@Composable
internal fun AliveSegments(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val colors = aliveUiColors()
    Row(modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(colors.text.copy(alpha = .055f)).padding(4.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val fill = animateColorAsState(if (active) colors.text.copy(alpha = .13f) else Color.Transparent)
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                .background(fill.value)
                .selectable(selected = active, enabled = enabled, role = Role.Tab) { onSelect(index) }
                .testTag("alive-segment-$label"),
                contentAlignment = Alignment.Center) {
                Text(label, color = (if (active) colors.text else colors.secondary).copy(alpha = if (enabled) 1f else .45f),
                    fontSize = 14.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun EditorPill(label: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit, immersive: Boolean) {
    val text = if (immersive || primary) Color.White else MiuixTheme.colorScheme.onSurface
    Box(Modifier.testTag("alive-header-$label").widthIn(min = 68.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
        .background(if (primary) Color(0xFF3478F6).copy(alpha = if (enabled) 1f else .3f)
            else if (immersive) Color(0x99282B30) else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = text.copy(alpha = if (enabled) 1f else .45f), fontSize = 14.sp)
    }
}

@Composable
internal fun AliveToolGlyph(icon: AliveToolIcon, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(1.6f * u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x * u, y * u), Offset(x2 * u, y2 * u), 1.6f * u, StrokeCap.Round)
        when (icon) {
            AliveToolIcon.STYLE -> {
                for(x in listOf(3f,14f)) for(y in listOf(3f,14f))
                    drawRoundRect(tint,Offset(x*u,y*u),Size(7*u,7*u),CornerRadius(2*u),style=stroke)
            }
            AliveToolIcon.PHOTO -> {
                drawRoundRect(tint, Offset(3*u, 3*u), Size(18*u, 18*u), CornerRadius(3*u), style = stroke)
                drawCircle(tint, 1.7f*u, Offset(8*u, 8*u))
                line(4f, 18f, 10f, 12f); line(10f, 12f, 14f, 16f); line(14f, 16f, 18f, 12f); line(18f, 12f, 21f, 15f)
            }
            AliveToolIcon.CROP -> {
                line(7f, 2f, 7f, 17f); line(7f, 17f, 22f, 17f)
                line(2f, 7f, 17f, 7f); line(17f, 7f, 17f, 22f)
            }
            AliveToolIcon.POSITION -> {
                line(12f, 2f, 12f, 22f); line(2f, 12f, 22f, 12f)
                line(9f, 5f, 12f, 2f); line(15f, 5f, 12f, 2f)
                line(9f, 19f, 12f, 22f); line(15f, 19f, 12f, 22f)
                line(5f, 9f, 2f, 12f); line(5f, 15f, 2f, 12f)
                line(19f, 9f, 22f, 12f); line(19f, 15f, 22f, 12f)
            }
            AliveToolIcon.TEXTURE -> {
                drawCircle(tint, 9*u, center, style = stroke)
                line(6f, 16f, 16f, 6f); line(8f, 19f, 19f, 8f); line(5f, 12f, 12f, 5f)
            }
            AliveToolIcon.OPTIONS -> {
                for (i in 0..2) {
                    val y = 6f + i*6f
                    line(3f, y, 21f, y)
                    drawCircle(Color(0xFF232326), 2.5f*u, Offset((if (i == 1) 16f else 8f)*u, y*u))
                    drawCircle(tint, 2.5f*u, Offset((if (i == 1) 16f else 8f)*u, y*u), style = stroke)
                }
            }
            AliveToolIcon.PLAY -> drawPath(Path().apply {
                moveTo(8*u, 4*u); lineTo(20*u, 12*u); lineTo(8*u, 20*u); close()
            }, tint, style = stroke)
            AliveToolIcon.STOP -> drawRoundRect(tint, Offset(6*u, 6*u), Size(12*u, 12*u), CornerRadius(2*u))
            AliveToolIcon.BRUSH -> {
                drawPath(Path().apply { moveTo(9*u, 14*u); lineTo(17*u, 3*u); quadraticTo(19*u, u, 21*u, 4*u); lineTo(13*u, 16*u); close() }, tint, style = stroke)
                drawPath(Path().apply { moveTo(9*u, 15*u); cubicTo(4*u, 14*u, 7*u, 20*u, 3*u, 21*u); cubicTo(11*u, 22*u, 13*u, 20*u, 12*u, 17*u) }, tint, style = stroke)
            }
            AliveToolIcon.ERASER -> {
                drawPath(Path().apply { moveTo(3*u, 14*u); lineTo(13*u, 3*u); lineTo(21*u, 10*u); lineTo(12*u, 20*u); lineTo(9*u, 20*u); close() }, tint, style = stroke)
                line(8f, 9f, 16f, 16f); line(9f, 21f, 21f, 21f)
            }
            AliveToolIcon.SELECT -> {
                for (x in listOf(4f, 20f)) { val dx = if (x == 4f) 5f else -5f
                    line(x, 4f, x + dx, 4f); line(x, 4f, x, 9f)
                    line(x, 20f, x + dx, 20f); line(x, 20f, x, 15f) }
            }
            AliveToolIcon.UNDO -> {
                line(8f, 5f, 3f, 10f); line(3f, 10f, 8f, 15f)
                drawPath(Path().apply { moveTo(3*u, 10*u); lineTo(15*u, 10*u); cubicTo(23*u, 10*u, 23*u, 21*u, 14*u, 21*u) }, tint, style = stroke)
            }
            AliveToolIcon.CLEAR -> {
                line(4f, 6f, 20f, 6f); line(9f, 3f, 15f, 3f)
                drawPath(Path().apply { moveTo(6*u, 6*u); lineTo(7*u, 21*u); lineTo(17*u, 21*u); lineTo(18*u, 6*u) }, tint, style = stroke)
                line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f)
            }
            AliveToolIcon.EYE -> {
                drawPath(Path().apply { moveTo(2*u, 12*u); quadraticTo(12*u, -2*u, 22*u, 12*u); quadraticTo(12*u, 26*u, 2*u, 12*u) }, tint, style = stroke)
                drawCircle(tint, 3*u, center, style = stroke)
            }
            AliveToolIcon.SPARK -> drawPath(Path().apply {
                moveTo(12*u, 2*u); quadraticTo(13*u, 11*u, 22*u, 12*u); quadraticTo(13*u, 13*u, 12*u, 22*u)
                quadraticTo(11*u, 13*u, 2*u, 12*u); quadraticTo(11*u, 11*u, 12*u, 2*u)
            }, tint, style = stroke)
            AliveToolIcon.CLOCK -> { drawCircle(tint, 9*u, center, style = stroke); line(12f, 6f, 12f, 12f); line(12f, 12f, 16f, 14f) }
            AliveToolIcon.CHEVRON -> { line(9f, 6f, 15f, 12f); line(15f, 12f, 9f, 18f) }
        }
    }
}
