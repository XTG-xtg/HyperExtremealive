# Infinity 独立入口

当前版本：`0.5-alive.7-ui.23-infinity2`，versionCode 38。新增 14 个样式；接入详情见 [Infinity 样式扩展](INFINITY-EXPANSION.zh-CN.md)。

入口改为「功能首页 → Infinity 壁纸」，与 Alive、光栅壁纸、液态壁纸同级。首页直接复用 `AppEntryCard`，沿用相同的壁纸服务图标、标题、间距、箭头和点击反馈。

详情页复用 `AliveHomeContent`、`DetailTabHost`、`CoverCarousel`、`LinkRow`、`SwitchRow` 与现有设置分组，跟随项目的 Miuix 明暗主题与文字样式。左侧新建打开 20 个样式的主题库，正中展示当前 / 上次使用壁纸，右侧展示 Infinity 已保存预设。Infinity 不显示 Alive 照片编辑入口，预览编辑器的样式面板仅显示 20 个 Infinity 样式。

Alive 的动态壁纸列表与样式面板不再收录 Infinity。Infinity 的启用状态、最近应用的主题和预设历史独立展示；底层继续使用现有的壁纸渲染服务和系统壁纸互斥规则。并非让多个动态壁纸服务同时生效。

预设按 Alive、Infinity、各 Vivo 家族分别保存最近应用的快照。读取 ui19 留在 `last-alive.json` 的 Infinity 快照时，只允许其恢复到 Infinity 页；不允许其再次成为 Alive 页的当前主题。开关操作会重新核对当前家族，防止页面状态过期时关闭另一类壁纸。

验证日志、测试结果与 APK 校验见 `build/infinity-top-level-20261114/`；UI 截图输出至 `app/build/reports/infinity-ui/`。UI 截图由 Robolectric 渲染实际 Compose 页面，封面使用随包参考缩略图，不是手机截图，不构成真实 AOD 验收。

ui20 独立入口建立时的验证记录：28 项定向测试全部通过：Infinity 动画 7 项、接入与家族隔离 5 项、Infinity 页面与导航 4 项、预设回归 7 项、Alive 明亮主题页面回归 1 项、伴随槽位策略 4 项。Release 构建与 Lint Vital 检查通过。三张 Infinity UI 截图和 Alive 对照图已人工查看，明暗主题及 320 dp 窄屏保持同一套布局。

ui23 扩展验证见 `build/infinity-expansion-20261115/`。未安装到实机。
