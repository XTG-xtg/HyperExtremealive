package com.frederic22.hyperextreme.alive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.frederic22.hyperextreme.ui.rememberClickHaptic
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
    subtitle: String = "",
    activeDemo: Boolean = false,
    onStopDemo: (() -> Unit)? = null,
    immersiveHidden: Boolean = false,
    onToggleImmersive: (() -> Unit)? = null,
    activeTools: Set<String> = emptySet(),
) {
    val haptic = rememberClickHaptic()
    val scheme = MiuixTheme.colorScheme
    val surfacePill = scheme.surfaceContainer.copy(alpha = 0.86f)
    val hairlineBorder = Color.White.copy(alpha = 0.10f)
    val showChrome = !immersiveHidden && !(activeDemo && onStopDemo != null)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { -it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            AliveEditorTopBar(
                title = title,
                startLabel = "取消",
                endLabel = if (busy) "处理中" else "应用",
                startEnabled = !busy,
                endEnabled = !busy && canApply,
                onStart = { haptic(); onCancel() },
                onEnd = { haptic(); onApply() },
                subtitle = subtitle,
            )
        }

        if (onToggleImmersive != null && !(activeDemo && onStopDemo != null)) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = if (immersiveHidden) 12.dp else 68.dp, end = 16.dp)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(surfacePill)
                    .border(0.75.dp, hairlineBorder, RoundedCornerShape(18.dp))
                    .clickable(enabled = !busy, role = Role.Button) { haptic(); onToggleImmersive() }
                    .padding(horizontal = if (immersiveHidden) 14.dp else 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AliveToolGlyph(AliveToolIcon.EYE, Modifier.size(16.dp), Color.White.copy(alpha = 0.9f))
                if (immersiveHidden) {
                    Text("显示界面", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        AnimatedVisibility(
            visible = activeDemo && onStopDemo != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .testTag("alive-tool-停止")
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(surfacePill)
                    .border(0.75.dp, scheme.primary.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                    .clickable(enabled = !busy, role = Role.Button) { haptic(); onStopDemo?.invoke() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AliveToolGlyph(AliveToolIcon.STOP, Modifier.size(16.dp), scheme.primary)
                Text("停止演示", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                    .navigationBarsPadding()
                    .padding(top = 28.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (message.isNotEmpty()) {
                    Box(
                        Modifier
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceContainer.copy(alpha = 0.76f))
                            .border(0.5.dp, hairlineBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            message,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                ModeCapsule(
                    mode = mode,
                    enabled = !busy,
                    surfaceColor = surfacePill,
                    borderColor = hairlineBorder,
                    onMode = { haptic(); onMode(it) },
                )

                // Up to five+ tools: scroll rather than shrinking tap targets.
                Row(
                    Modifier
                        .widthIn(max = 600.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    tools.forEach { tool ->
                        val isEnabled = !busy && tool.enabled
                        val isActive = tool.label in activeTools && isEnabled
                        val circleBg by animateColorAsState(
                            targetValue = when {
                                isActive -> scheme.primary.copy(alpha = 0.24f)
                                else -> surfacePill
                            },
                            animationSpec = tween(180),
                            label = "tool-bg",
                        )
                        val circleBorder by animateColorAsState(
                            targetValue = if (isActive) scheme.primary.copy(alpha = 0.7f) else hairlineBorder,
                            animationSpec = tween(180),
                            label = "tool-border",
                        )
                        val iconTint = when {
                            !isEnabled -> Color.White.copy(alpha = 0.32f)
                            isActive -> scheme.primary
                            else -> Color.White.copy(alpha = 0.92f)
                        }
                        Column(
                            Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("alive-tool-${tool.label}")
                                .clickable(enabled = isEnabled, role = Role.Button) { haptic(); tool.onClick() }
                                .padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(circleBg)
                                    .border(if (isActive) 1.dp else 0.75.dp, circleBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                AliveToolGlyph(
                                    icon = tool.icon,
                                    modifier = Modifier.size(22.dp),
                                    tint = iconTint,
                                    containerColor = circleBg,
                                )
                            }
                            Text(
                                tool.label,
                                color = if (isEnabled) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.42f),
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCapsule(
    mode: Int,
    enabled: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    onMode: (Int) -> Unit,
) {
    val labels = listOf("息屏", "锁屏", "桌面")
    val clamped = mode.coerceIn(0, labels.lastIndex)
    BoxWithConstraints(
        Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth(0.74f)
            .clip(RoundedCornerShape(28.dp))
            .background(surfaceColor)
            .border(0.75.dp, borderColor, RoundedCornerShape(28.dp))
            .padding(4.dp)
            .selectableGroup()
            .testTag("alive-editor-modes"),
    ) {
        val slotWidth = maxWidth / labels.size
        val offset by animateDpAsState(
            targetValue = slotWidth * clamped,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            label = "mode-capsule-offset",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(slotWidth)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val selected = index == clamped
                val textColor by animateColorAsState(
                    targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
                    animationSpec = tween(160),
                    label = "mode-tab-text",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onMode(index) },
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AliveEditorTopBar(
    title: String,
    startLabel: String,
    endLabel: String,
    startEnabled: Boolean,
    endEnabled: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    immersive: Boolean = true,
    subtitle: String = "",
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (immersive) Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.48f), Color.Transparent)))
                else Modifier,
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorPill(startLabel, startEnabled, false, onStart, immersive)
        Column(
            Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = if (immersive) Color.White else MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    color = if (immersive) Color.White.copy(alpha = 0.68f) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        EditorPill(endLabel, endEnabled, true, onEnd, immersive)
    }
}

/**
 * 编辑器面板里的分段选择器（2–4 项）。
 *
 * 面板背景是圆角卡片，直接用 Miuix 的 TabRow 会把未选中项画成描边黑块、
 * 文字贴在顶部、选中块比轨道高，所以这里自绘：等宽分段、文字垂直居中、
 * 只有一条轨道和一个弹性滑块，和编辑器底部“息屏/锁屏/桌面”保持一致。
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
    val haptic = rememberClickHaptic()
    BoxWithConstraints(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.text.copy(alpha = 0.06f))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        val count = labels.size.coerceAtLeast(1)
        val slotWidth = maxWidth / count
        if (selected in labels.indices) {
            val offset by animateDpAsState(
                targetValue = slotWidth * selected,
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 440f),
                label = "segment-offset",
            )
            Box(
                Modifier
                    .offset(x = offset)
                    .width(slotWidth)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.text.copy(alpha = 0.14f)),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                val textColor by animateColorAsState(
                    targetValue = (if (active) colors.text else colors.secondary).copy(alpha = if (enabled) 1f else 0.45f),
                    animationSpec = tween(150),
                    label = "segment-text",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = active, enabled = enabled, role = Role.Tab) {
                            haptic()
                            onSelect(index)
                        }
                        .testTag("alive-segment-$label"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPill(label: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit, immersive: Boolean) {
    val scheme = MiuixTheme.colorScheme
    val text = when {
        primary -> scheme.onPrimary
        immersive -> Color.White
        else -> scheme.onSurface
    }
    val bg = when {
        primary -> scheme.primary.copy(alpha = if (enabled) 1f else 0.35f)
        immersive -> scheme.surfaceContainer.copy(alpha = 0.82f)
        else -> Color.Transparent
    }
    val borderMod = if (!primary && immersive) {
        Modifier.border(0.75.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
    } else Modifier
    Box(
        Modifier
            .testTag("alive-header-$label")
            .widthIn(min = 68.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(borderMod)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = text.copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
internal fun AliveToolGlyph(
    icon: AliveToolIcon,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    containerColor: Color = Color(0xFF232326),
) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(1.6f * u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x * u, y * u), Offset(x2 * u, y2 * u), 1.6f * u, StrokeCap.Round)
        when (icon) {
            AliveToolIcon.STYLE -> {
                for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f))
                    drawRoundRect(tint, Offset(x * u, y * u), Size(7 * u, 7 * u), CornerRadius(2 * u), style = stroke)
            }
            AliveToolIcon.PHOTO -> {
                drawRoundRect(tint, Offset(3 * u, 3 * u), Size(18 * u, 18 * u), CornerRadius(3 * u), style = stroke)
                drawCircle(tint, 1.7f * u, Offset(8 * u, 8 * u))
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
                drawCircle(tint, 9 * u, center, style = stroke)
                line(6f, 16f, 16f, 6f); line(8f, 19f, 19f, 8f); line(5f, 12f, 12f, 5f)
            }
            AliveToolIcon.OPTIONS -> {
                for (i in 0..2) {
                    val y = 6f + i * 6f
                    val cx = if (i == 1) 16f else 8f
                    line(3f, y, cx - 2.5f, y)
                    line(cx + 2.5f, y, 21f, y)
                    drawCircle(containerColor.copy(alpha = 1f), 2.5f * u, Offset(cx * u, y * u))
                    drawCircle(tint, 2.5f * u, Offset(cx * u, y * u), style = stroke)
                }
            }
            AliveToolIcon.PLAY -> drawPath(Path().apply {
                moveTo(8 * u, 4 * u); lineTo(20 * u, 12 * u); lineTo(8 * u, 20 * u); close()
            }, tint, style = stroke)
            AliveToolIcon.STOP -> drawRoundRect(tint, Offset(6 * u, 6 * u), Size(12 * u, 12 * u), CornerRadius(2 * u))
            AliveToolIcon.BRUSH -> {
                drawPath(Path().apply { moveTo(9 * u, 14 * u); lineTo(17 * u, 3 * u); quadraticTo(19 * u, u, 21 * u, 4 * u); lineTo(13 * u, 16 * u); close() }, tint, style = stroke)
                drawPath(Path().apply { moveTo(9 * u, 15 * u); cubicTo(4 * u, 14 * u, 7 * u, 20 * u, 3 * u, 21 * u); cubicTo(11 * u, 22 * u, 13 * u, 20 * u, 12 * u, 17 * u) }, tint, style = stroke)
            }
            AliveToolIcon.ERASER -> {
                drawPath(Path().apply { moveTo(3 * u, 14 * u); lineTo(13 * u, 3 * u); lineTo(21 * u, 10 * u); lineTo(12 * u, 20 * u); lineTo(9 * u, 20 * u); close() }, tint, style = stroke)
                line(8f, 9f, 16f, 16f); line(9f, 21f, 21f, 21f)
            }
            AliveToolIcon.SELECT -> {
                for (x in listOf(4f, 20f)) {
                    val dx = if (x == 4f) 5f else -5f
                    line(x, 4f, x + dx, 4f); line(x, 4f, x, 9f)
                    line(x, 20f, x + dx, 20f); line(x, 20f, x, 15f)
                }
            }
            AliveToolIcon.UNDO -> {
                line(8f, 5f, 3f, 10f); line(3f, 10f, 8f, 15f)
                drawPath(Path().apply { moveTo(3 * u, 10 * u); lineTo(15 * u, 10 * u); cubicTo(23 * u, 10 * u, 23 * u, 21 * u, 14 * u, 21 * u) }, tint, style = stroke)
            }
            AliveToolIcon.CLEAR -> {
                line(4f, 6f, 20f, 6f); line(9f, 3f, 15f, 3f)
                drawPath(Path().apply { moveTo(6 * u, 6 * u); lineTo(7 * u, 21 * u); lineTo(17 * u, 21 * u); lineTo(18 * u, 6 * u) }, tint, style = stroke)
                line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f)
            }
            AliveToolIcon.EYE -> {
                drawPath(Path().apply { moveTo(2 * u, 12 * u); quadraticTo(12 * u, -2 * u, 22 * u, 12 * u); quadraticTo(12 * u, 26 * u, 2 * u, 12 * u) }, tint, style = stroke)
                drawCircle(tint, 3 * u, center, style = stroke)
            }
            AliveToolIcon.SPARK -> drawPath(Path().apply {
                moveTo(12 * u, 2 * u); quadraticTo(13 * u, 11 * u, 22 * u, 12 * u); quadraticTo(13 * u, 13 * u, 12 * u, 22 * u)
                quadraticTo(11 * u, 13 * u, 2 * u, 12 * u); quadraticTo(11 * u, 11 * u, 12 * u, 2 * u)
            }, tint, style = stroke)
            AliveToolIcon.CLOCK -> { drawCircle(tint, 9 * u, center, style = stroke); line(12f, 6f, 12f, 12f); line(12f, 12f, 16f, 14f) }
            AliveToolIcon.CHEVRON -> { line(9f, 6f, 15f, 12f); line(15f, 12f, 9f, 18f) }
        }
    }
}
