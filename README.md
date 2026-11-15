<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo.png" alt="LOGO" width="128">
</p>

<h1 align="center"><strong>HyperExtreme Alive</strong></h1>

<p align="center">Alive wallpapers for HyperOS</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-blue?style=flat-square&logo=android" alt="platform">
  <img src="https://img.shields.io/badge/minSdk-33-red?style=flat-square" alt="minSdk">
  <img src="https://img.shields.io/badge/targetSdk-37-green?style=flat-square" alt="targetSdk">
  <img src="https://img.shields.io/badge/License-AGPL--3.0-yellow?style=flat-square" alt="license">
</p>

面向 HyperOS 的个性化模块。本分支在 HyperExtreme 的基础上加入 Alive 壁纸支持，
让息屏、锁屏与桌面之间的画面衔接更连贯。

## 主要功能

- **Alive 照片壁纸**：圆景、留光、星月、轻启、山脉、银河，支持自选照片。
- **动态壁纸**：启航、流光、视频、立体，以及 Vivo 液态、趣味光栅、闪卡等效果。
- **Infinity 壁纸**：20 款样式，支持息屏、锁屏与桌面转场，以及桌面滑动跟随、松手回弹和页面视差。
- **照片与取景**：锁屏、桌面和相框独立选图、缩放与裁切；桌面可跟随锁屏照片，锁屏与桌面纹理分别设置。
- **统一三态预览**：Alive、Infinity、Vivo 系列及独立光栅、空间、视差壁纸均可在全屏预览中切换息屏、锁屏和桌面，确认应用后才保存设置。
- **息屏亮度**：在预览中调整，固定 0–5 倍、20 格、每格 0.25；0 保留系统原始息屏亮度。非零倍率去掉全屏 AOD 的系统壁纸遮罩，通过壁纸曝光控制亮度，保留自动关闭与唤醒流程。
- **唤醒曝光**：退出 AOD 的首帧撤销额外曝光，避免高倍率残留到锁屏恢复阶段；原有展开与压暗过渡继续播放。
- **光栅与空间壁纸**：支持多图光栅搭配、卡片管理、空间壁纸显示区域裁剪，以及系统壁纸服务模式。视差预览显示所选背景和移动效果，系统主体以实机为准。
- **系统个性化**：保留 HyperExtreme 的视差壁纸、景深及其他系统个性化功能。

不包含小米原有超级壁纸。时钟替换和通知光效扩展已在 ui22 移除；
具体范围与兼容性边界见[功能清单](docs/alive/INTEGRATION-MATRIX.zh-CN.md)。

## 使用要求

- 主要适配澎湃 OS 4，面向 ARM64 设备；其他系统及修改较多的第三方 ROM 不保证兼容。
- 空间／光栅壁纸的系统壁纸服务模式可免 Root 使用；其余模块功能需要 **Root + LSPosed**。
- 最低支持 Android 13；系统钩子及渲染效果依赖具体 ROM，需在设备上确认兼容性。

## 使用方法

从 [Releases](https://github.com/XTG-xtg/HyperExtremealive/releases/latest) 下载正式安装包及配套资源。

| 文件 | 用途 |
| --- | --- |
| `HyperExtreme-Alive-0.5-alive.7-ui.34.apk` | ui34 正式版安装包，versionCode 49，开启 R8 优化与资源收缩 |
| `HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip` | 启航、流光、视频、立体等动态壁纸的配套资源包 |
| `SHA256SUMS.txt` | 安装包与资源包的 SHA-256 校验值 |

1. 安装模块，在 LSPosed 中启用并勾选模块推荐的作用域，按提示重启。首次安装和更新后均需确认作用域生效。
2. 打开 **功能 → Alive 动态壁纸**，选择壁纸、编辑照片并预览，满意后点击应用。
3. 使用需要资源包的动态壁纸时，在 Alive 的动态壁纸页点击“导入动态壁纸”，选择本次 Release 的 ZIP 文件，保持压缩包完整。模块会校验资源版本、文件大小和摘要，导入成功后即可预览与应用；已有预设会保留。
4. 光栅、空间、视差详情页通过“预览与应用”或“预览与调整”进入全屏预览，在“息屏亮度”面板中调整倍率。取消不保存，应用后生效。
5. Alive、空间、光栅和视差壁纸不能同时启用，切换前请先关闭正在使用的另一项。

资源包只存放壁纸数据，照片壁纸、Infinity 和 Vivo 系列所需资源仍随 APK 提供。
更新后重启系统界面或手机，新的系统层息屏亮度保持逻辑才能加载；仅重新打开应用无法更新已经运行的系统钩子。

本分支使用独立包名 `com.xtg.hyperextremealive`，可与主线并存，不会覆盖主线或自动迁移旧配置。
请勿同时启用两个模块中修改相同系统功能的选项，遇到异常请先排查模块冲突。

## 构建

需要 JDK 21、Android SDK / Build Tools 37、NDK 28.2.13676358、CMake 3.22.1、
Python 3.10+，以及 Rust 1.96.0 和 `aarch64-linux-android` target。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

正式构建开启 R8 优化与资源收缩，产物位于 `app/build/outputs/apk/release/`。
构建会同时生成 `build/dynamic-wallpapers/HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip`，
发布时应将它与同次构建的 APK 一起提供。包内清单必须与 APK 中的 `dynamic-wallpapers.json` 一致。
开发调试可使用 `:app:assembleDebug`。本地签名配置参考
`keystore.properties.example`，不要提交密钥；签名不同的 APK 无法直接覆盖安装。
开发记录与验证资料保留在 [docs/alive](docs/alive/)。

## 开源许可

本项目采用 [AGPL-3.0](LICENSE) 许可，引用的上游代码保留原作者署名及相应许可。
OpenAlive 自实现代码的 [MIT 许可](docs/alive/OPENALIVE_LICENSE)不涵盖官方运行时、字体、
壁纸及效果素材，这些资源的权利仍归原权利人。更多信息见模块关于页及[资源说明](docs/alive/OPENALIVE_THIRD_PARTY.md)。

## 鸣谢

本项目是基于 **HyperExtreme** 的 Alive 壁纸适配分支，参考 [**HyperAlive**](https://github.com/Askt-clot/HyperAlive) 的界面与交互，
并移植、整合 **OpenAlive** 的照片渲染器及相关功能，针对 HyperOS 进行适配与维护。
感谢各位作者的开源与分享：

- **HyperExtreme · Frederic22**：本项目的基础与原作。[Github](https://github.com/22Frederic/HyperExtreme) · [酷安](https://www.coolapk.com/u/3268364)
- **HyperAlive · AXLES / Askt-clot**：Alive 壁纸界面与交互的参考来源。[GitHub 主页](https://github.com/Askt-clot) · [开源仓库](https://github.com/Askt-clot/HyperAlive) · [酷安](https://www.coolapk.com/u/39588100)
- **OpenAlive · 下北沢最速傳說 / Maga-King**：照片渲染管线及相关功能的移植来源。[Github](https://github.com/Maga-King/OpenAlive) · [酷安](https://www.coolapk.com/u/24655132)
- **整合维护 · 年华虚度 / XTG-xtg**：本分支的整合、HyperOS 适配与维护。[Github](https://github.com/XTG-xtg/HyperExtremealive) · [酷安](https://www.coolapk.com/u/44199044)

上游代码保留原作者署名与相应许可；官方运行时、字体、壁纸及素材的权利仍归原权利人。
