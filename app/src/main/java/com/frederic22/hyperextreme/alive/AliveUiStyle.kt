package com.frederic22.hyperextreme.alive

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** HyperAlive's Settings-like surfaces, scoped to Alive rather than the host app. */
internal data class AliveUiColors(
    val page: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val group: Color,
)

@Composable
internal fun aliveUiColors(): AliveUiColors =
    if (MiuixTheme.colorScheme.onSurface.luminance() > .5f) {
        AliveUiColors(Color.Black, Color(0xFF1C1C1E), Color(0xFFF2F2F2), Color(0xFF9A9A9E), Color(0xFF8E8E93))
    } else {
        AliveUiColors(Color(0xFFF2F3F5), Color.White, Color(0xFF101010), Color(0xFF737373), Color(0xFF707070))
    }
