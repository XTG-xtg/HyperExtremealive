package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text

/** Separate presentation so compact screens and tool states can be tested without running AI. */
@Composable
internal fun MaskEditorContent(subject: Boolean, ready: Boolean, working: Boolean, tool: Int, brush: Float,
    showMask: Boolean, canUndo: Boolean, status: String, onCancel: () -> Unit, onSave: () -> Unit,
    onTool: (Int) -> Unit, onBrush: (Float) -> Unit, onCompare: () -> Unit, onUndo: () -> Unit,
    onClear: () -> Unit, onRecognize: () -> Unit, canvas: @Composable BoxScope.() -> Unit) {
    val enabled = ready && !working
    BoxWithConstraints(Modifier.fillMaxSize().background(EditorSurface.page)) {
        val dockLimit = maxHeight * .49f
        Column(Modifier.fillMaxSize()) {
            AliveEditorTopBar(if (subject) "主体蒙版" else "涂抹范围", "取消", "完成", true, enabled, onCancel, onSave)
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (showMask) Color(0xFF58C8FF) else EditorSurface.secondary, CircleShape))
                Text(if (!showMask) "原图对照 · 暂停编辑" else if (subject) "蓝色区域为主体" else "蓝色区域启用效果",
                    color = EditorSurface.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                EditorTextAction(if (showMask) "查看原图" else "返回蒙版", enabled = enabled, icon = AliveToolIcon.EYE,
                    modifier = Modifier.testTag("mask-compare").semantics { stateDescription = if (showMask) "显示蒙版" else "显示原图" },
                    onClick = onCompare)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B0D10)).testTag("mask-canvas"), contentAlignment = Alignment.Center) {
                canvas()
                if (!ready) Text(status, color = EditorSurface.secondary, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
            Text(when {
                status.isNotEmpty() && ready -> status
                !showMask -> "再次点击「返回蒙版」继续修整"
                tool == 2 -> "拖出包含主体的选框，再识别选区"
                tool == 1 -> "涂抹移除多余区域，沿边缘轻轻修整"
                else -> "涂抹添加选区，随时查看原图对照"
            }, color = EditorSurface.secondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(EditorSurface.panel).heightIn(max = dockLimit).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tools = buildList {
                        add("画笔" to AliveToolIcon.BRUSH); add("擦除" to AliveToolIcon.ERASER)
                        if (subject) add("框选" to AliveToolIcon.SELECT)
                    }
                    tools.forEachIndexed { index, (title, glyph) ->
                        MaskTool(title, glyph, tool == index, enabled, Modifier.weight(1f), { onTool(index) })
                    }
                    MaskTool("撤销", AliveToolIcon.UNDO, false, enabled && canUndo, Modifier.weight(1f), onUndo, false)
                }
                // Tool changes must not resize the canvas and move the subject under the finger.
                Box(Modifier.fillMaxWidth().heightIn(min = 88.dp), contentAlignment = Alignment.CenterStart) {
                    if (tool != 2) EditorValueSlider("画笔大小", brush, .005f.. .1f, "%.1f%%".format(brush * 100),
                        enabled = enabled && showMask, onChange = onBrush)
                    else Text("选框只限制识别范围，不会裁切照片", color = EditorSurface.secondary, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorTextAction("清空", enabled = enabled, icon = AliveToolIcon.CLEAR, onClick = onClear)
                    Spacer(Modifier.weight(1f))
                    if (subject) Row(Modifier.clip(RoundedCornerShape(14.dp))
                        .background(EditorSurface.accent.copy(alpha = if (enabled) .14f else .05f))
                        .clickable(enabled = enabled, role = Role.Button, onClick = onRecognize)
                        .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp).testTag("mask-recognize"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AliveToolGlyph(AliveToolIcon.SPARK, Modifier.size(18.dp), EditorSurface.accent)
                        Text(if (working) "处理中…" else if (tool == 2) "识别选区" else "自动识别", fontSize = 14.sp,
                            color = EditorSurface.accent.copy(alpha = if (enabled) 1f else .4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MaskTool(title: String, icon: AliveToolIcon, selected: Boolean, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit, select: Boolean = true) {
    val tint = (if (selected) EditorSurface.accent else EditorSurface.secondary).copy(alpha = if (enabled) 1f else .3f)
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) EditorSurface.raised else Color.Transparent)
        .then(if (select) Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick))
        .testTag("mask-tool-$title").padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AliveToolGlyph(icon, Modifier.size(23.dp), tint)
        Text(title, color = tint, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
