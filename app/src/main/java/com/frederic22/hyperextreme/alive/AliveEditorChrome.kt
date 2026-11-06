package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.Canvas
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

internal enum class AliveToolIcon { PHOTO, STYLE, POSITION, CROP, TEXTURE, OPTIONS, PLAY, STOP }
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
    modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent)))
        .statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        EditorPill(startLabel, startEnabled, false, onStart)
        Text(title, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        EditorPill(endLabel, endEnabled, true, onEnd)
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
    onSelect: (Int) -> Unit,
) {
    Row(modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Color(0x59000000)).padding(3.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(23.dp))
                .background(if (active) Color(0xFF4A4A4C) else Color.Transparent)
                .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                .testTag("alive-segment-$label"),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (active) Color.White else Color(0xFFB0B0B6),
                    fontSize = 14.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun EditorPill(label: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    Box(Modifier.testTag("alive-header-$label").widthIn(min = 74.dp).heightIn(min = 48.dp).clip(CircleShape)
        .background(if (primary) Color(0xFF2F6BFF).copy(alpha = if (enabled) 1f else .45f) else Color(0xCC3A3A3C))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else .55f), fontSize = 14.sp)
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
        }
    }
}
