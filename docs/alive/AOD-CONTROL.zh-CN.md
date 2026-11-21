# 系统息屏开关与强制支持

ui24 在 Alive / Infinity 的“运行状态”中新增两个开关，沿用现有 Miuix SwitchRow。

- **系统息屏显示**：直接读取和写入当前用户的 Secure 设置 `doze_always_on`、`aod_mode_user_set`。通过 Root 写入，回读确认后才显示成功；失败时恢复已操作的键并报告恢复错误。系统修改会通过 ContentObserver 同步到模块。关闭壁纸或切换预设不更改这两个系统设置。
- **强制息屏支持**：显式写入当前用户 `hyperextreme_force_aod=1`，同时开启系统 AOD 和“与锁屏一致”模式。关闭这个选项只取消强制支持，保留系统 AOD 开关供用户自行选择。开启或关闭后重启手机完整生效，因为全屏 AOD 监听器在系统进程构造时注册。

强制模式通过 LSPosed 跳过 `AmbientDisplayConfiguration.alwaysOnAvailable()`、AOD 应用的设备可用性/ambient 壁纸支持判断，以及已核对的 `support_aod_fullscreen` / `inner_screen_support_aod_fullscreen` 两项 FeatureParser 判断。在 MiuiFullAodManager 构造前准备两个全屏支持标志，并 deoptimize 构造方法，使原生监听器可以被初始化。读取的是当前系统用户的 Secure 开关，与壁纸预设或壁纸 Engine 是否在运行无关。模块必须勾选系统框架、系统界面和息屏显示作用域。

第三方壁纸兼容修正只针对**实际锁屏绑定本模块 HomeWallpaperService 或 LockWallpaperService**、支持 ambient 且使用默认锁屏主题的情况。MiuiFullAodManager.onWallpaperChanged 后修正 isWallpaperTypeSupport；每次 updateSettings 前重新计算以避免旧绑定残留。其他壁纸、未知绑定、第三方主题不放行。设备支持判断只在用户开启强制模式后跳过。

强制支持与“当前是否开启息屏”分开：强制选项保留时，系统息屏显示仍可关闭且不会被模块自动重开。没有伪装 `isAodEnable=true`，没有覆写息屏时段、屏幕亮度、颜色反转、来电遮挡、指纹遮罩或省电状态。强制模式启用的是 ROM 已有的息屏流程，未增加缺失的系统息屏服务或显示驱动。

ROM 契约来自小米 15 当前 MIUIAod、MiuiSystemUI 和 framework.jar；反编译诊断文件保存在 build/aod-control-20261115，不打包进 APK。设备报告不支持的情况有单元测试模拟；真正不支持 AOD 的实机显示效果需要在对应设备验证。

2026-11-15 实机纠正：ui25 的 `libaod_control.so` 在小米 15 / HyperOS 4 上触发 ART `EnsureModifiable` 致命检查，`SetStaticBooleanField` 导致 SystemUI 连续 SIGABRT，不能由 Java catch 捕获。ui25 禁止继续安装或开启强制 AOD；此前“失败保留原判断”的描述不成立。

ui26 删除该 JNI 库和调用。`AodSupportFlags` 先完整验证两个 static boolean 字段，已为 true 时不写；需要修改时，通过 `jdk.internal.misc.Unsafe` 提供的 `staticFieldBase/staticFieldOffset/putBooleanVolatile` 访问并回读，不硬编码 ART 内存布局。API 或字段不匹配时记录失败并保留原系统判断。构造函数仍 deoptimize，避免沿用编译时旧值。替代访问方式在同一手机独立 ART 进程中已验证；ui26 在实际 SystemUI 构造、原生监听器和真实不支持设备上的效果仍需验证，详见 `DEVICE-REVIEW-20261115.zh-CN.md`。
