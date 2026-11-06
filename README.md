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

当前源码版本为 **`0.5-alive.7-ui.4` / versionCode 19**。关于页四位贡献者使用并排圆形头像，
点击头像选择各自的平台。头像全部内置，不依赖网络加载；HyperAlive 使用用户提供的酷安
默认头像且仅显示酷安入口，其余三位保留 Github / 酷安，原作保留原有哔哩哔哩入口。
本轮只修改关于页，不修改壁纸渲染、电源状态或已应用配置。
校验和发布状态见 [REPAIR-07-UI4](docs/alive/REPAIR-07-UI4.zh-CN.md)。

`0.5-alive.7-ui.3` / versionCode 18：照片/动态分类增加过渡，
真实引擎切换先淡出、等新画面首帧再淡入；取景和预览共用椭圆顶栏按钮。
照片面板按当前预览状态显示，桌面只保留「跟随锁屏」开关及「选择照片」设置行，
无小字备注或大按钮。跟随不合并桌面纹理，独立选图成功才关闭跟随，取消不改变设置。
验证见 [REPAIR-07-UI3](docs/alive/REPAIR-07-UI3.zh-CN.md)。

`0.5-alive.7-ui.2` / versionCode 17 将预览底栏「样式」扩展为
照片/动态壁纸选择面板，动态部分复用壁纸库分类和缩略图。所有类型在息屏、锁屏、桌面
预览都能直接切换，保留当前预览状态，不用退出编辑页；Vivo 各类草稿独立恢复。
选中不等于应用，仍须手动确认。验证见 [REPAIR-07-UI2](docs/alive/REPAIR-07-UI2.zh-CN.md)。

`0.5-alive.7-ui.1` / versionCode 16 修正照片编辑交互：
锁屏/桌面纹理按预览状态独立显示；取景页移除多层半透明黑框；六种照片缩略图统一
使用真实息屏渲染器（圆景不再只是圆形裁图）。息屏预览底栏新增「样式」按钮，点击
展开复用二级页缩略图的选择面板，不常驻选择条。亮度范围调整为 25%–300%，修复
低于原版时白色不能变暗、静止圆景调亮度会跳转角度的问题。示意时钟默认关闭，
不再冒充真实系统样式。验证和复测步骤见 [REPAIR-07-UI1](docs/alive/REPAIR-07-UI1.zh-CN.md)。

`0.5-alive.7` 修复隐藏引擎的状态事件消费，新增照片四种效果、独立纹理、系统时钟
位置桥接与防烧屏兜底。历史验证见 [REPAIR-07](docs/alive/REPAIR-07.zh-CN.md)。

后续扩展已补上锁屏/桌面/相框独立照片、双指取景、磨砂辅助纹理、全屏/纯黑照片 AOD，
以及本地应用事务恢复。Vivo 液态（4 种）、光栅（4 种）、闪卡（5 种）接入原始隔离
运行时，支持独立素材/视频位置、取景、灵敏度、效果范围、画笔蒙版及本机主体识别。
这些渲染器已有隔离设备测试，但不等于安装到 HyperOS 后的完整系统验收。
Vivo 视频位置使用无音频的 MediaCodec 解码器，隐藏时释放硬件解码器；已验证导入、
封面、时间戳推进和循环。当前手机系统 APK 的只读契约核对见
[ROM-CONTRACTS](docs/alive/ROM-CONTRACTS.zh-CN.md)。

**尚未完成 OpenAlive 全功能合入**：通知光效、原版时钟编辑器与指纹遮罩联动未接入，
真实 HyperOS 时钟几何 Hook 仍需安装后验证；完整清单见 [功能矩阵](docs/alive/INTEGRATION-MATRIX.zh-CN.md)。
不含小米原有超级壁纸。Vivo 官方运行时与素材保留其原始权利及校验清单，不因适配代码
开源而自动获得再分发授权。下面是旧版历史记录。

历史交付 `0.5-alive.6-ui.3` / versionCode 14，在 ui.2 基础上将真实电源状态保护
扩展到全部 Alive 壁纸，修复息屏被可见性提示覆盖成锁屏的问题。
修复范围、测试和手机复测步骤见 [ALIVE-POWER-FIX.md](ALIVE-POWER-FIX.md)。
以下保留原整合版本的基线说明。

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

`tools/install_and_scope.sh` 保留历史文件名，但已移除原有 LSPosed 数据库修改和进程
强杀逻辑。现在必须显式传入设备序列号、APK 和 `--install` 才会覆盖安装；默认仅打印
计划。作用域确认和重启由用户手动完成。`tools/package_alive_candidate.py` 以白名单
打包源码、校验归档及逐文件哈希，不包含参考包、构建缓存、密钥或本机配置。

## 鸣谢

本项目是基于 **HyperExtreme** 的 Alive 壁纸适配分支，参考 **HyperAlive** 的界面与交互，
并移植、整合 **OpenAlive** 的照片渲染器及相关功能，针对 HyperOS 进行适配与维护。
感谢各位作者的开源与分享：

- **HyperExtreme · Frederic22**：本项目的基础与原作。[Github](https://github.com/22Frederic/HyperExtreme) · [酷安](https://www.coolapk.com/u/3268364)
- **HyperAlive · AXLES**：Alive 壁纸界面与交互的参考来源。[酷安](https://www.coolapk.com/u/39588100)
- **OpenAlive · 下北沢最速傳說 / Maga-King**：照片渲染管线及相关功能的移植来源。[Github](https://github.com/Maga-King/OpenAlive) · [酷安](https://www.coolapk.com/u/24655132)
- **整合维护 · 年华虚度 / XTG-xtg**：本分支的整合、HyperOS 适配与维护。[Github](https://github.com/XTG-xtg/HyperExtremealive) · [酷安](https://www.coolapk.com/u/44199044)

上游代码保留原作者署名与相应许可；官方运行时、字体、壁纸及素材的权利仍归原权利人。
