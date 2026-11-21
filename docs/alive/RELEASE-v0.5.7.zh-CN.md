# HyperExtreme Alive v0.5.7

`v0.5.7` · versionCode `53` · ARM64 · Android 13+

v0.5.7 以 ui36 为稳定基线继续整理，统一正式版本号与发布入口。它保留 Alive 照片壁纸、Infinity、Vivo 效果、空间/光栅/视差壁纸，以及三态预览、息屏亮度和 AOD 会话控制。

## 本版重点

- 正式版本号统一为 `0.5.7`，独立包名仍为 `com.xtg.hyperextremealive`。
- 优化照片壁纸、Infinity 与动态壁纸在息屏、锁屏、桌面之间的状态交接。
- 保留 0–5 倍息屏亮度控制；倍率为 0 时遵循系统原始亮度。
- Release 构建启用 R8、资源收缩及 ART baseline profile。
- 动态壁纸大资源继续独立提供 ZIP，照片、Infinity 与 Vivo 资源随 APK 内置。

## 安装

1. 安装 `HyperExtreme-Alive-v0.5.7.apk`。
2. 在 LSPosed 中启用模块并勾选推荐作用域，然后重启手机。
3. 如需启航、流光、视频、立体等动态壁纸，在应用内导入完整的 `HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip`。
4. 不要同时启用主线 HyperExtreme 与本分支中修改相同系统功能的选项。

从 versionCode 54/55 的内部测试包回退时，Android 通常不允许直接覆盖安装，需要先备份配置并卸载测试包。

## 校验

- APK versionName：`0.5.7`
- APK versionCode：`53`
- applicationId：`com.xtg.hyperextremealive`
- minSdk：33
- targetSdk：37
- ABI：arm64-v8a
- 单元测试：383 项通过
- APK 内置源资源：566 项校验通过
- 外置动态资源：48 项校验通过

以上是离线构建与资源校验结果，不等同于所有 HyperOS 版本上的实机兼容保证。
