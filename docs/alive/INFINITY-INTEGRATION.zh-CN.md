# Infinity 测试版接入记录

初次接入版本：`0.5-alive.7-ui.19-infinity1`，versionCode 34。当前 `ui.20-infinity2` 已将 Infinity 提升为同级入口，见 `INFINITY-TOP-LEVEL.zh-CN.md`。此前音乐相关逻辑未在这两次改动中修复或回退。

## 使用入口

功能首页 → Infinity 壁纸 → 主题样式，可选黑、金、蓝、兰紫、粉、银六套主题；与 Alive、光栅、液态同级。编号 801—806，目录组 7。进入预览可切换息屏 / 锁屏 / 桌面，调整深色、息屏亮度和位移、传感器灵敏度，保存预设后应用。倾斜手机与快速横向轻扫可带动图层、星点。

## 实现

- `InfinityRenderer`：独立 EGL 渲染器，使用提取的背景、69 片效果图集网格、渐变颜色和 GLSL，绘制 14,004 个星点。GL 资源由各实例持有，失败及退出时释放。
- `InfinityTheme`：读取并校验六套主题数据。素材来源、文件摘要见 `INFINITY-PROVENANCE.json`。
- `InfinityMotion`：接入现有 `WallpaperMotion` / `MotionSession`；scene 0/1/2 分别显示息屏星空、锁屏分层、桌面渐变。根据时间计算过渡，反向切换从当前画面继续，跨越长时间隐藏直接计算到当前进度。
- 实际锁屏、桌面共享 CPU 动画状态和固定种子的星点，预览独立；不导入原 APK 的全局动画单例或 100 ms 状态轮询。
- 标准 `TYPE_GYROSCOPE` 使用采样时间积分、限幅、平滑；锁屏静止约 5 秒后回中。隐藏与息屏不订阅传感器。缺少传感器仍可使用三态过渡与轻扫。
- 静止后停止 GPU 帧循环，仍保留屏幕上的最终图像；状态、手势、传感器和防烧屏位移事件可以唤醒重绘。完全关闭显示时遵循现有 `DisplayFramePolicy`。
- 已接通选择列表、缩略图、编辑预览、预设解析、从 Vivo 返回 Alive 的历史主题恢复、隐藏桌面伴随槽位的预热。

## 与原 APK 的区别

这是对用户提供的第三方 APK 的视觉数据及行为进行适配，不宣称三星官方实现逐帧一致。保留六主题图集、色板、星点数量及主要过渡时长；逐片跟随改为按时间推进，星点采用固定随机种子，以避免双槽位重建时分布不同。

原 APK 实际灭屏进入全黑，本版把其 AOD 视觉接入 Alive 真正的 scene 0，并持续保留暗色光带与星点静帧；未照搬原 APK 在 60 秒后淡出光带的定时行为。是否能在真实 doze 显示，仍取决于 HyperOS 现有壁纸与息屏链路。

## 验证记录

构建、单元测试、签名、资源清单的最终结果保存在 `build/infinity-integration-20261114/` 与本版交付目录。离屏参考图使用随包着色器和主题数据、Java 生成的粒子，经过桌面 OpenGL 绘制；不是 Android 实机截图，也不能替代 Surface、锁屏和 AOD 联调。

新增测试覆盖：六主题资源与目录、预设往返、历史主题恢复、快速反向过渡、隐藏时间跨度、30/120 Hz 终态、传感器重复/间断/息屏输入、无效输入和粒子分布。

已通过 `InfinityMotionTest` 7 项、`InfinityIntegrationTest` 2 项，以及 `CompanionPreparationTest` 4 项，共 13 项。已有的纯 JVM `MotionSessionTest` 另通过 23 个断言。六主题 × 三状态共 18 张离屏参考图位于 `build/infinity-integration-20261114/previews/`。

本机构建使用 JDK 21、Android SDK 37、Build Tools 37.0.0 和项目原有的 AGP / Kotlin 版本。Google 仓库连接失败以及 Maven 依赖未缓存时，缺失工具依赖被单独暂存到 `build/infinity-integration-20261114/maven/`；Maven Central 或 Google Maven 镜像返回的文件均进行了 SHA-1 对照校验。临时 `local-dependencies.init.gradle` 只为已暂存的精确版本指定本地仓库，没有改动项目的依赖版本或仓库配置。

构建命令（PowerShell，本机构建环境）：

```powershell
$env:JAVA_HOME='E:\AndroidStudio\android-studio\jbr'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/codex-no-unix-socket-dir'
$env:PYTHONUTF8='1'
.\gradlew.bat -I build\infinity-integration-20261114\local-dependencies.init.gradle :app:testDebugUnitTest --tests '*Infinity*' --tests '*CompanionPreparationTest*' :app:assembleRelease -PlegacyUiSmoke=false --offline --no-daemon --console=plain
```

`JAVA_TOOL_OPTIONS` 是本机 Windows NIO 回环连接的进程级临时绕行配置；该目录应保持不存在。正常网络与构建环境可省略临时 init 脚本、离线参数以及这个环境变量。

ui19 交付的 `*-source-delta.zip` 仅包含该次改动和新增的代码、主题资源、测试与文档，需要应用到 ui18-music1 源码基线；不是独立可构建的完整源码项目。ui20 的增量基线为 ui19。`*-r8-mapping.zip` 用于还原对应测试包的混淆堆栈。

本轮目标是构建测试包。未安装到手机，真实息屏、双槽位衔接、手势和功耗仍需实机验收；之前网易云/Alive 问题不属于本轮修复结果。
