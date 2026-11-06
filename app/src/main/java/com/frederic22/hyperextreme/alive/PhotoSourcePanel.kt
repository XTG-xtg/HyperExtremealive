package com.frederic22.hyperextreme.alive

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.ui.SwitchRow
import com.frederic22.hyperextreme.ui.rememberClickHaptic
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun photoSourceSlot(mode: Int, variant: Int, options: PhotoOptions): Int = when {
    mode == 2 -> 1
    mode == 0 && variant == 602 && options.aodMode == 0 -> 2
    else -> 0
}

internal fun photoSourceTitle(slot: Int) = when(slot) {
    1 -> "桌面照片"
    2 -> "相框照片"
    else -> "锁屏照片"
}

/** The preview owns the target, exactly like texture editing; choosing a file is explicit. */
@Composable
internal fun PhotoSourcePanel(slot: Int, options: PhotoOptions, enabled: Boolean,
    onChange: (PhotoOptions) -> Unit, onPick: (Int) -> Unit) {
    val haptic = rememberClickHaptic()
    Column(Modifier.fillMaxWidth()) {
        if(slot == 1) SwitchRow(title = "跟随锁屏", checked = options.followLock, enabled = enabled,
            onCheckedChange = { onChange(options.edit(14, if(it) 1f else 0f)) })
        BasicComponent(title = "选择照片", enabled = enabled, role = Role.Button,
            modifier = Modifier.testTag("photo-source-pick-$slot"),
            endActions = {
                Image(MiuixIcons.ChevronForward, contentDescription = null,
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
                    modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
            }, onClick = { if(enabled) { haptic(); onPick(slot) } })
    }
}
