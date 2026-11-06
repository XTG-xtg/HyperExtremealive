package com.frederic22.hyperextreme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 开发者主页（酷安）—— 原 `MaskTabs` 里的 `DeveloperProfileUrl`，2026-10-31 随弹窗一起搬来时改名。
 * 链接本身没变。
 */
internal const val DeveloperCoolapkUrl = "https://www.coolapk.com/u/3268364"

/** 项目仓库（README / Issue 都在这儿）。 */
internal const val DeveloperGithubUrl = "https://github.com/22Frederic/HyperExtreme"

/** B 站主页。 */
internal const val DeveloperBilibiliUrl = "https://space.bilibili.com/204757951"

/** Contributor-specific links, preserving the original platform selection interaction. */
@Composable
internal fun PlatformDialog(
    show: Boolean,
    title: String,
    summary: String,
    entries: List<Pair<String, String>>,
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
) {
    WindowDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        val dismissState = LocalDismissState.current
        Column(modifier = Modifier.fillMaxWidth().testTag("contributor-platforms").verticalScroll(rememberScrollState())) {
            entries.forEach { (label, url) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            onDismissRequest()
                            dismissState?.invoke()
                            onPick(url)
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.main,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                // 单按钮右对齐（项目里其余弹窗都是「取消 / 确认」两个 ⇒ SpaceBetween）。
                horizontalArrangement = Arrangement.End,
            ) {
                TextButtonLike(
                    text = "取消",
                    onClick = {
                        onDismissRequest()
                        dismissState?.invoke()
                    },
                )
            }
        }
    }
}
