# 上轮日志排查的实机复核

本次实机复核发现并定位了 **ui25 的 AOD JNI 导致 SystemUI 连续崩溃**。它是上轮新增修复中的回归，不能归咎于旧用户日志或其他模块。ui25 已撤销候选资格；ui26 删除此 JNI 路径。音乐、空间壁纸、光栅、指纹和息屏动画的完整复测尚未完成。

证据目录：`build/device-review-20261115/`。原始日志含设备个人信息，仅在本地留存，不上传。

## 设备与安装前状态

- 小米 15，serial `197fb721`，代号 dada，型号 24129PN74C。
- ROM `OS4.0.0.19.XOCCNXM`，SystemUI 为 UID 1000。此设备不能直接复现用户 B 的独立 UID 10231 配置权限问题。
- 开始时安装 ui24 / versionCode 39；HOME 和 LOCK 都绑定本模块，当前 Alive 照片预设 601。
- 已保存安装前 logcat、LSPosed 模块日志、壁纸组件、应用退出记录、模块偏好及资源文件哈希。未清空日志、卸载应用或清数据。
- ui25 / versionCode 40 覆盖安装成功，随后重启以加载同一版本 Hook；日志确认新构建标签已生效。

## P0：AOD JNI 使系统界面崩溃

`postboot-logcat.txt` 记录同一次开机中的 4 次 SystemUI SIGABRT：

| 时间 | PID | 记录行 |
| --- | --- | --- |
| 13:15:02.234 | 6084 | 38380 |
| 13:15:12.267 | 7582 | 62826 |
| 13:15:14.656 | 8218 | 73346 |
| 13:15:16.934 | 8535 | 86498 |

致命检查为 `Cannot set public static final field boolean com.miui.utils.configs.MiuiConfigs.SUPPORT_FULL_AOD`。

调用链：`MiuiFullAodManager` 构造 Hook → `ForcedAodSupport.prepare` → `AodNative.prepare` → `libaod_control.so` → ART `SetStaticBooleanField` → `EnsureModifiable` → `abort`。例如同文件第 71231～71239 行包含 JNI 到本模块的完整入口，第 73883 行记录 abort message。

这是 native 进程终止，Java `catch` 无法捕获。上轮“JNI 失败会保留原判断”的说法错误。上轮单元测试的 ROM 字段替身不是 `final`，没有覆盖真实约束；本地构建、签名、对齐通过也不能说明运行时安全。

恢复操作：将当前用户 `hyperextreme_force_aod` 从 1 临时改为 0，其他 AOD 开关未改。用户随后确认 LSPosed 进入安全模式。PID 8729 保持运行只是界面恢复的证据，**不是新 Hook 正常工作的证据**。待 ui26 覆盖安装、重启并确认 Hook 加载后，再恢复用户原本开启的强制 AOD 配置进行验证。

## ui26 修复方式与验证边界

- 删除 `AodNative.java`、`aod_control_jni.c`、CMake 目标及对应 R8 keep 规则，包内不再需要 `libaod_control.so`。
- 新增 `AodSupportFlags`，预先验证两个字段都是 static boolean；已支持时直接返回；需写入时由运行时解析 `staticFieldBase/staticFieldOffset`，用 `putBooleanVolatile` 设置并反射回读，不硬编码 ART 内存布局。
- 访问 API 或字段不匹配时抛出可捕获异常；两个字段完整解析前不写，执行异常时尝试恢复原值。
- 保留构造函数 deoptimize 和显式强制开关范围。没有改时段、省电、指纹遮罩或亮度策略。
- ROM 测试替身改为真正的 `static final`，新增字段完整性、重复准备、混合支持状态回归。

在同一手机的独立 `app_process` 中，替代访问方式对两个初始为 false 的 static final 字段成功写入并回读 true，见 `aod-probe/jdk-runtime.txt`。此前 `sun.misc.Unsafe` 试探因缺少 `staticFieldOffset` 产生独立测试进程退出，未加入产品实现；产品采用实际存在的 `jdk.internal.misc.Unsafe` API。

另已提供 `tools/test_aod_support_device.py --serial 197fb721`：编译生产访问器与测试驱动，在隔离 ART 进程验证，既不安装应用，也不修改系统 AOD 设置。这仍不能替代实际 SystemUI 构造、监听器注册和息屏显示复测。

## 网易云退出记录新增证据

`before-music-exits.txt` 中，11 月 14 日网易云进程退出包括：

- 19:36:54：SIGNALED / signal 9，没有进一步说明；不能仅凭这一项断定是谁发出 SIGKILL。
- 19:39:29：OTHER KILLS BY SYSTEM / UNDELIVERED BROADCAST，说明 `Can't deliver broadcast`。
- 19:40:27、19:58:29 等：USER REQUESTED / FORCE STOP，说明 `SwipeUpClean`。

这些记录比“音乐停了”的描述更具体，但缺少对应的切换壁纸完整时间窗口，不能把所有退出归为同一原因。本次尚未进行网易云播放切换复测。

## 尚未完成的实机项目

| 项目 | 本次状态 |
| --- | --- |
| 新包安装及真实版本标签 | ui25 已证实；ui26 待安装 |
| AOD JNI 回归 | 已在实机定位；ui26 已移除路径，系统内待复测 |
| UID 10231 配置 IPC | 当前小米 15 为 UID 1000，仍需对应设备验证 |
| 网易云 + HyperOS 原生音乐控件显示/暂停/关闭 | 未测试 |
| Alive HOME→AOD 收起、稳定小球、亮屏展开 | 未测试 |
| Infinity 相同流程 | 未测试 |
| 空间同图重应用、裁切/时钟编辑、真正换图 | 未测试 |
| 光栅在原生音乐控件出现/消失时保持主体和桌面 | 未测试 |
| 指纹认证、系统解锁、空间淡出的时间与画面配对 | 未测试，需要用户实际指纹认证 |
| 原生不支持 AOD 设备的强制显示 | 无对应设备 |

手机电量从约 6% 降至 1%，用户决定先充电并要求继续构建测试包。因此本次没有继续反复重启或开展低电量下的显示结论。不能将未测项目标记为通过。

## ui26 本地交付结果

- APK：`deliverables/HyperExtreme-Alive-0.5-alive.7-ui.26-bugfix.apk`，versionCode 41，169,645,077 字节。
- SHA-256：`88dcacfe55aeea78f54a24f43e543477692fd9c46f49eaf98795ea17a354bf5e`。
- AOD 专项 19 项单元测试通过，0 失败/错误/跳过；生产访问器的独立主机 JVM 探针通过。没有把主机 JVM 结果当作手机运行时结果。
- Release 构建与 `lintVitalRelease` 通过；本次未重跑完整 `lintRelease`，上轮记载的 4 项既有 lint 错误仍未宣称解决。
- APK 内 versionCode/versionName、V2 签名、ZIP CRC、ZIP 16KB 对齐和全部打包 ELF LOAD 16KB 对齐校验通过；证书与旧测试包一致，可保留数据覆盖安装。
- 与 ui25 比较，只删除 `lib/arm64-v8a/libaod_control.so`；其余 575 项 assets/lib 字节未变。48 个外置资源没有重新内置，资源索引和资源包哈希一致，无需重新导入资源。
- ui26 的映射文件与校验值已单独保存。ui25 留存作证据并添加 `deliverables/UI25-DO-NOT-INSTALL.txt`，避免误装。
- 当前手机仍安装 ui25，并处于用户确认的 LSPosed 安全模式；强制 AOD 临时为 0。未将新包安装/推送到正在充电的手机。

验证索引：`ui26-release-verification.json`、`ui26-build.log`、`ui26-junit/`、`ui26-signature.log`、`ui26-alignment.log`、`ui26-badging.log`。
