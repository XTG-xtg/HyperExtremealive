<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo.png" alt="LOGO" width="128">
</p>

<h1 align="center"><strong>HyperExtreme</strong></h1>

<p align="center">Extreme for HyperOS</p>

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
- **照片与取景**：锁屏、桌面和相框独立选图、缩放与裁切；桌面可跟随锁屏照片，锁屏与桌面纹理分别设置。
- **三态预览**：在息屏、锁屏和桌面预览中切换壁纸与样式，确认应用后才生效。
- **原有功能**：保留 HyperExtreme 的空间壁纸、视差壁纸及系统个性化功能。

不包含小米原有超级壁纸。OpenAlive 的通知光效、原版时钟编辑器及完整指纹遮罩联动尚未接入；
具体范围与兼容性边界见[功能清单](docs/alive/INTEGRATION-MATRIX.zh-CN.md)。

## 使用要求

- 主要适配澎湃 OS 4，面向 ARM64 设备；其他系统及修改较多的第三方 ROM 不保证兼容。
- 除空间壁纸的系统壁纸服务模式外，其他功能需要 **Root + LSPosed**。
- 空间壁纸的免 Root 模式最低支持 Android 13，目前主要在 Android 17 上验证。

## 使用方法

1. 安装模块，在 LSPosed 中启用并勾选模块推荐的作用域，按提示重启。首次安装和更新后均需确认作用域生效。
2. 打开 **功能 → Alive 动态壁纸**，选择壁纸、编辑照片并预览，满意后点击应用。
3. Alive、空间壁纸和视差壁纸不能同时启用，切换前请先关闭正在使用的另一项。

与其他修改相同系统功能的模块同时使用可能产生冲突，遇到异常请先排查模块冲突。

## 构建

需要 JDK 21、Android SDK / Build Tools 37、NDK 28.2.13676358、CMake 3.22.1、
Python 3.10+，以及 Rust 1.96.0 和 `aarch64-linux-android` target。

```powershell
.\gradlew.bat :app:assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/`。本地签名配置参考
`keystore.properties.example`，不要提交密钥；签名不同的 APK 无法直接覆盖安装。
开发记录与验证资料保留在 [docs/alive](docs/alive/)。

## 开源许可

本项目采用 [AGPL-3.0](LICENSE) 许可，引用的上游代码保留原作者署名及相应许可。
OpenAlive 自实现代码的 [MIT 许可](docs/alive/OPENALIVE_LICENSE)不涵盖官方运行时、字体、
壁纸及效果素材，这些资源的权利仍归原权利人。更多信息见模块关于页及[资源说明](docs/alive/OPENALIVE_THIRD_PARTY.md)。

## 鸣谢

本项目是基于 **HyperExtreme** 的 Alive 壁纸适配分支，参考 **HyperAlive** 的界面与交互，
并移植、整合 **OpenAlive** 的照片渲染器及相关功能，针对 HyperOS 进行适配与维护。
感谢各位作者的开源与分享：

- **HyperExtreme · Frederic22**：本项目的基础与原作。[Github](https://github.com/22Frederic/HyperExtreme) · [酷安](https://www.coolapk.com/u/3268364)
- **HyperAlive · AXLES**：Alive 壁纸界面与交互的参考来源。[酷安](https://www.coolapk.com/u/39588100)
- **OpenAlive · 下北沢最速傳說 / Maga-King**：照片渲染管线及相关功能的移植来源。[Github](https://github.com/Maga-King/OpenAlive) · [酷安](https://www.coolapk.com/u/24655132)
- **整合维护 · 年华虚度 / XTG-xtg**：本分支的整合、HyperOS 适配与维护。[Github](https://github.com/XTG-xtg/HyperExtremealive) · [酷安](https://www.coolapk.com/u/44199044)

上游代码保留原作者署名与相应许可；官方运行时、字体、壁纸及素材的权利仍归原权利人。
