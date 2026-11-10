package com.frederic22.hyperextreme.alive

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import com.frederic22.hyperextreme.ui.squircleStroke
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object VivoFlashStyles {
    val names = listOf("虹彩流光", "夜空星点", "斑斓光斑", "镭射光带", "放射光束")
    val descriptions = listOf(
        "彩色反光随倾斜移动，像转动一张闪卡",
        "细小星点随倾斜闪动；深色壁纸更明显，亮色背景较柔和",
        "彩色光斑随倾斜浮动，可更换圆形、爱心等图案",
        "斜向彩虹光带随倾斜扫过画面",
        "多束高光随倾斜转动，呈现放射光芒",
    )
    const val HINT = "需倾斜手机才能看到效果"
}

/** Captured from the bundled engine, not illustrations of a different renderer. */
@Composable
internal fun VivoFlashStylePicker(style: Int, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (style - 1).coerceIn(0, 4))
    Text(if (style == 2) "夜空星点 · 深色壁纸更明显" else "5 种样式 · 左右滑动选择",
        style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    LazyRow(state = list, modifier = Modifier.fillMaxWidth().selectableGroup().testTag("flash-style-gallery"),
        contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(VivoFlashStyles.names, key = { index, _ -> index }) { index, name ->
            val frame by produceState<ImageBitmap?>(null, index) {
                value = withContext(Dispatchers.IO) {
                    runCatching { context.assets.open("vivo/preview/flash-styles/${index + 1}.png").use {
                        BitmapFactory.decodeStream(it)?.asImageBitmap()
                    } }.getOrNull()
                }
            }
            val selected = style == index + 1
            Column(Modifier.width(112.dp).squircleClip(16.dp)
                .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
                .squircleStroke({ if (selected) 2.dp else 0.dp }, { colors.primary }, 16.dp)
                .selectable(selected, role = Role.RadioButton, onClick = { onSelect(index + 1) })
                .testTag("flash-style-${index + 1}").padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().aspectRatio(360f / 800f).squircleClip(11.dp).background(colors.surfaceContainer),
                    contentAlignment = Alignment.Center) {
                    if (frame != null) Image(frame!!, "$name · 原引擎效果示意", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text("加载示意图", style = MiuixTheme.textStyles.footnote1)
                }
                Text(name, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp), style = MiuixTheme.textStyles.body2)
            }
        }
    }
}
