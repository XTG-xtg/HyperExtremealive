# 首轮整合验证记录

> 历史记录：这是 0.5-alive.1 的构建/只读查询记录，不是功能验收。用户安装并重启后确认应用壁纸失败。已查明错误的框架作用域与缺失的息屏权限适配；最新进展见 `REPAIR-02.zh-CN.md`。原文“未安装”和签名不一致仅描述当时状态，现在手机已安装同开发签名的 v6。

日期：2026-11-05。版本：`0.5-alive.1` / versionCode 6。原始 Git 基线：`7321d76`。

## 已通过

| 项目 | 结果 |
|---|---|
| 完整 `:app:assembleDebug` | Kotlin / Compose、Java、原 C/C++ 桌面桥与空间填充库均构建完成 |
| 状态机 | 18 个 JVM 断言通过 |
| 立体运动 | 6 个 JVM 断言通过，包括实际 gyro 输入改变视差、非法值拒绝、静止回中 |
| 结构与素材 | 563 项检查通过，包括上游资产 SHA-256、模型结构、蒙版解压、目录与元数据 |
| 新增 Java 层 | 36 个源文件另行用公开 SDK 34 编译通过 |
| APK 签名 | apksigner verify 通过，开发签名 |
| APK / ELF 对齐 | zipalign 16KB 检查通过；5 个 arm64 `.so` 的 LOAD 对齐均至少 16384 |
| 模块入口 | DEX 实际类定义中不打包 legacy Xposed API，也不打包 libxposed compile-only API；只有一个 `HookEntry` 元数据入口 |
| Root helper | 构建生成 DEX 039；APK 内 helper 与只读实机测试的文件哈希相同 |
| 实机只读查询 | system UID helper 成功返回原版 home / lock 组件；未执行 apply |
| 原壁纸保护 | 检查前后两侧仍为 `com.qihang.aod`；未改作用域、未重启、未卸载 |

机器可读记录：`verification.json`、`apk-validation.json`、`device/apk-helper-query.json`。

## Lint 未全绿

最终 `lintDebug`：**3 errors、226 warnings、17 hints**。本次新增代码的 error 已修复；剩余三个 error 位于以下文件，已逐文件与 Git HEAD 对比，内容完全未改：

1. `data/DesktopWallpaperInjector.kt:104`：`WallpaperManager.getDrawable` 的权限检查。
2. `hook/ParallaxBg.kt:1070`：旧 receiver fallback 未声明导出标志。
3. `hook/ScreenFlash.kt:51`：旧 receiver fallback 未声明导出标志。

没有通过全局 suppress 或新建 baseline 隐藏它们，也没有为了本次 Alive 整合重写这三条旧功能链。完整 assemble 成功不等于 lint 全通过。

## 尚未验收

**新合并 APK 未安装到手机，因此没有声称新 UI 或新 Hook 已实机跑通。**

原因：已装 HyperExtreme 的证书与本次 debug 签名不同，无法无损覆盖。保留现有安装和数据，等待原签名文件的本地路径。签名摘要与版本在 `apk-validation.json` 和 `device/installed-certificate.txt` 中。

安装授权与签名解决之后，仍需验证中文布局、主题切换、照片导入、每类场景的实际 GL 效果、真实双侧应用、息屏 / 指纹直达 / 横屏 / 重启恢复和功耗。当前双侧 helper 查询成功不能推导出这些项目成功。

## 本机构建环境

已补齐仓库所需 SDK 37.0、Build Tools 37、NDK 28.2.13676358、CMake 3.22.1，沿用上游 Gradle 9.6.1 / AGP 9.4.1 / Kotlin 2.4.20 / Miuix 0.9.4，没有降级依赖来规避编译。

本机 JBR 21 最初在 Gradle 的 Java NIO pipe 初始化遇到 `UnixDomainSockets.connect: Invalid argument`。此次仅给构建进程设置 `jdk.net.unixdomain.tmpdir` 到工作区内不存在的目录，使 JDK 在建立 pipe 时回退 TCP loopback；没有修改防火墙、系统代理或全局环境变量。其他机器无此错误时无需此设置。

源码包不包含签名私钥、构建缓存、已装 APK 副本或 Gradle 下载缓存。
