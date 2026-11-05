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

## 兼容性

对于空间壁纸：LSPosed模式只在澎湃OS4上测试，也只会对澎湃OS4适配；系统壁纸服务（免Root）模式理论支持到安卓13，但对安卓17之外的安卓版本未测试。

对于其他功能：都需要Root与LSPosed，只会对澎湃OS4适配。

## 使用说明

对于需要LSPosed的功能，请先提前勾选并重启所有推荐作用域（初次安装及模块更新时），再使用对应功能。

本模块可能对修改较多的HyperOS ROM或修改过的推荐作用域应用不生效甚至导致其他问题。

本模块可能与其他修改相同作用域的LSPosed模块产生冲突，在使用模块出现问题时，请先排查模块冲突。

## 开源代码许可

本项目采用AGPL-3.0许可证，有关开源代码许可请在模块的关于页查看。

## Alive 整合修复候选

HyperAlive 作者主页：[酷安主页](https://www.coolapk.com/u/39588100)。

基于 `main@7321d76102da566264f8773671ec64172eae7849` 整合 Alive 实现，当前版本 `0.5-alive.6` / versionCode 11。
入口：**功能 → Alive 动态壁纸**。沿用本项目的 Miuix 主题、中文页面与系统二级页转场。

- Alive 二级页按 HyperAlive 首页组织：息屏圆形预览、锁屏与桌面竖卡、动态壁纸入口，圆景和留光单独放在照片壁纸。
- 启航、流光、轻启、视频、立体收进「动态壁纸」子页；圆景和留光不进这个列表。
- 预览可以进入息屏 / 锁屏 / 桌面编辑，未确认前不写入系统壁纸或已应用配置。
- 照片先保存为独立草稿；确认应用后才复制到生效文件。导入原图按当前壁纸尺寸居中裁切，不拉伸。
- 诊断收进右上角菜单。合并后的模块仍只有一个 libxposed API 102 入口，不混用 legacy Xposed。
- Root 设壁纸只允许本应用的两个 Alive 服务，并在设置结束后再次回读两侧组件。
- Alive、空间壁纸、视差壁纸通过各自的启用开关互斥。应用内开启会拒绝另一项仍在生效或尚未确认关闭的功能；关闭 Alive 只卸下本模块绑定的壁纸槽。

第三轮已替换简化照片渲染器：圆景使用 OpenAlive 透镜管线，留光使用其逐帧蒙版、前后照片与矩阵管线。启航/流光也修正了坐标、时间基准和 vsync 调度。取景、锁屏照片效果和三态演示沿用中文 Miuix 界面。

第四轮仅修复圆景/留光的息屏状态仲裁：息屏前馈锁定 AOD，壁纸可见/临时 KEYGUARD 不能解除，真实可交互唤醒后才允许展开。启航、流光及照片渲染器保持第三轮版本，不改其材质和曲线。用户已在第三轮版本验证照片基础效果，设备日志也确认双槽绑定成功。

第五轮保留第四轮状态仲裁：照片静止后停绘，圆球稳定自转按约 30fps 上限调度，转场仍跟随 vsync；留光加入有界方向预读和纹理复用。新增息屏位置调整，并参考 HyperAlive 的状态编辑交互重组壁纸库与编辑页。

**仍是待整机验收的候选版**：离屏 GPU / 消费窗口测试不能代替系统 AOD 背景、时钟、指纹、重启与功耗验收；第五轮的新界面与真实壁纸性能仍待用户验收，圆景/留光尚未宣称与原版 HyperAlive 像素一致。最新证据与限制见 `docs/alive/REPAIR-05.zh-CN.md`，息屏仲裁见 `docs/alive/REPAIR-04.zh-CN.md`，渲染移植见 `docs/alive/REPAIR-03.zh-CN.md`。

第六轮补上 Alive 首页开关和三类壁纸互斥，并把二级页改成三态预览加动态壁纸子页。遮罩/主体导入会按系统壁纸尺寸居中裁成目标画布。空间与视差的关闭以系统回读或 hook 回执为准，未确认前开关保持开启，避免假关闭后再开另一项。

**仍是待整机验收的候选版**：本轮界面截图来自 Robolectric 的 Compose 渲染，离屏 GPU 测试不能代替真机点击、息屏动画、指纹解锁和功耗验收。视差 hook 增加了指令序号，安装后需要重启作用域进程才会加载。最新说明见 `docs/alive/REPAIR-06.zh-CN.md`。

### 构建与验证

沿用上游固定版本：JDK 21、SDK 37（也可能安装在 `android-37.0`）、Build Tools 37.0.0、NDK 28.2.13676358、CMake 3.22.1、Gradle 9.6.1。新增 Python 3.10+，用于从 `alive-root/src` 编译受限 helper；也可设置 `PYTHON` 指向解释器。不会从旧 APK / 不透明 JAR 提取可执行核心。

照片原生库另需 Rust **1.96.0** 和 `aarch64-linux-android` target（`alive-photo/rust-toolchain.toml` 固定）。Cargo 在 PATH，或以 `CARGO` 环境变量指定绝对路径。Gradle 在构建前运行 `tools/build_alive_photo.py`，使用上述 NDK 从源码生成 arm64、16KB 对齐的 `libhyperextreme_photo.so`；首次构建需下载锁定的 `jni-sys 0.3.0`。可通过 `RUSTUP_HOME` / `CARGO_HOME` 使用独立本地工具链。

```powershell
python tools/verify_alive.py
.\gradlew.bat :app:assembleDebug
```

`verify_alive.py` 默认使用本机 SDK 34 / Build Tools 35 检查新增 Java 层的公开 API 兼容性，可用 `--sdk`、`--java-home`、`--compile-sdk`、`--build-tools` 覆盖。它**不代替**完整 Gradle 构建和实机验证。

合并 APK 的包名仍是 `com.frederic22.hyperextreme`。没有原签名时，debug 包不能覆盖签名不同的已装版本；不要为绕过这个限制直接卸载用户现有应用或清除数据。系统框架作用域是 **`system`，不是 `android`**，另含 `miui.systemui.plugin` 等推荐项，需要用户在 LSPosed 手动确认；不写 LSPosed 数据库、不自动重启。

OpenAlive 的 MIT 源码许可、移植来源与素材哈希保留在 `docs/alive/`。**第三方官方壁纸、视频、着色器和蒙版并不因此获得 MIT 再分发 / 商用授权**，公开发布前需核对权利。
