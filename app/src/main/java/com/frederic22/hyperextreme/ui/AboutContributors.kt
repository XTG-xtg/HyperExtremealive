package com.frederic22.hyperextreme.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frederic22.hyperextreme.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class AboutContributor(
    val id: String,
    val name: String,
    val project: String,
    val role: String,
    @DrawableRes val avatar: Int,
    val platforms: List<Pair<String, String>>,
)

internal val AboutContributors = listOf(
    AboutContributor(
        id = "hyperextreme",
        name = "Frederic22",
        project = "HyperExtreme",
        role = "原作",
        avatar = R.drawable.contributor_hyperextreme,
        platforms = listOf(
            "打开Github" to DeveloperGithubUrl,
            "打开哔哩哔哩" to DeveloperBilibiliUrl,
            "打开酷安" to DeveloperCoolapkUrl,
        ),
    ),
    AboutContributor(
        id = "maintainer",
        name = "年华虚度",
        project = "HyperExtreme Alive",
        role = "整合维护",
        avatar = R.drawable.contributor_maintainer,
        platforms = listOf(
            "打开Github" to "https://github.com/XTG-xtg/HyperExtremealive",
            "打开酷安" to "https://www.coolapk.com/u/44199044",
        ),
    ),
    AboutContributor(
        id = "hyperalive",
        name = "AXLES",
        project = "HyperAlive",
        role = "HyperAlive",
        avatar = R.drawable.contributor_hyperalive,
        platforms = listOf("打开酷安" to "https://www.coolapk.com/u/39588100"),
    ),
    AboutContributor(
        id = "openalive",
        name = "下北沢最速傳說",
        project = "OpenAlive",
        role = "OpenAlive",
        avatar = R.drawable.contributor_openalive,
        platforms = listOf(
            "打开Github" to "https://github.com/Maga-King/OpenAlive",
            "打开酷安" to "https://www.coolapk.com/u/24655132",
        ),
    ),
)

@Composable
internal fun AboutContributorsCard(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberClickHaptic()
    var selectedId by rememberSaveable { mutableStateOf(AboutContributors.first().id) }
    var showPlatform by rememberSaveable { mutableStateOf(false) }
    val selected = AboutContributors.first { it.id == selectedId }

    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AboutContributors.forEach { contributor ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .testTag("contributor-${contributor.id}")
                        .semantics { contentDescription = "${contributor.project} · ${contributor.name}" }
                        .clickable(role = Role.Button, onClickLabel = "选择平台") {
                            haptic()
                            selectedId = contributor.id
                            showPlatform = true
                        }
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val avatarModifier = Modifier
                        .padding(bottom = 5.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                    Image(
                        painter = painterResource(contributor.avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = avatarModifier,
                    )
                    Text(
                        text = contributor.name,
                        style = MiuixTheme.textStyles.body2,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                    )
                    Text(
                        text = contributor.role,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                    )
                }
            }
        }
    }

    // Keep the selected identity while the dialog animates out.
    PlatformDialog(
        show = showPlatform,
        title = selected.name,
        summary = selected.project,
        entries = selected.platforms,
        onDismissRequest = { showPlatform = false },
        onPick = onOpenUrl,
    )
}
