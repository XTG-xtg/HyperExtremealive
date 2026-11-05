# 第四轮：圆景与留光的息屏状态仲裁

候选版本：`0.5-alive.4` / versionCode 9。日期：2026-11-05。

## 本轮反馈与证据

用户在第三轮版本确认圆景、留光基础效果已经实现，但存在息屏后自动展开，以及锁屏再次按电源键无法恢复正确息屏状态的问题；启航没有明显问题。

只读设备检查确认已安装 versionCode 8，启动日志包含 `Alive integration 03`，system_server adapter v2 已加载，`AMBIENT_WALLPAPER=0`。应用日志记录 `PAIR_VERIFIED`，`dumpsys wallpaper` 两侧均为本应用 Alive 服务。因此不能再把本次问题归因于用户未勾选系统框架或没有应用成功。

保留下来的日志中有以下共用状态入口竞态：

```text
18:45:13.595 state=AOD  reason=screen-off
18:45:13.632 visible slot=2 value=true
18:45:13.633 state=LOCK reason=visible-slot-2
18:45:13.713 state=AOD  reason=screen-off
18:45:13.746 state=LOCK reason=scrim-keyguard
18:45:13.786 state=AOD  reason=scrim-aod
```

息屏指令发出仅 38ms 后，壁纸可见回调就把 AOD 覆盖成 LOCK。AOD 面板点亮、壁纸 Surface 可见与可交互锁屏是不同概念，旧逻辑没有区分。

**证据边界：该段日志是在用户已经切换到启航 variant 1 时捕获的共用入口行为，不是圆景自动展开的同步录像或照片专属日志。** 照片异常当时的日志已不在缓存内。它给出了可复现的状态错误路径，但最终视觉修复仍需用户复测。

## 修改范围

- 新增纯 Java `PhotoPowerGate`，仅应用于已生效的圆景 601 / 留光 602，不作用于独立预览。
- 收到 `screen-off` 即建立息屏保护，即使此时 `PowerManager.isInteractive()` 尚未变成 false，也不允许可见性或临时 KEYGUARD 信号触发展开。
- 设备非交互状态下，所有状态提示收敛到 AOD；普通 AOD 点亮和未完成的唤醒前馈不能直接进入 LOCK/HOME。
- 已恢复交互，并收到屏幕亮起、电源唤醒、平台壁纸唤醒、交互边沿或认证解锁事件，才解除保护；随后迟到的 scrim-AOD / DOZE 样本不能反向打断真正的展开。
- 照片壁纸监听 Display 回调中的实际交互边沿，补充真实亮屏与息屏状态；两槽都解绑后重新注册时重置保护，避免沿用无引擎期间的旧锁存状态。
- 保留原有时间戳拒绝、指纹解锁保护、重复状态不重启动画及两槽状态历史。

**没有改动** Cosmic/Gradient/Photo 渲染器、SceneMotion/FrameMotion、Rust 原生库、着色器、蒙版、取景 UI、Root helper、作用域或 IPC 协议。`AliveHooks.java` 也与第三轮相同；HookEntry 仅把版本诊断标记更新为 `Alive integration 04`。源码哈希对比见 `evidence-v4/rendering-unchanged.json`。

## 验证与限制

| 验证 | 结果 |
| --- | --- |
| 原逻辑复现 | JVM 用旧 reducer 重放上述时间差，确认息屏 38ms 后错误进入 LOCK。 |
| 新状态仲裁 | 454 个断言通过，覆盖日志竞态、10 秒 AOD 保持、30 轮睡醒循环、展开中息屏、无效/过期事件、迟到 doze、认证直达桌面和 reducer 联动。 |
| Android 真实 Looper 动画中断 | 隔离 app_process 执行 14 次事件：展开 100ms 后再息屏、隐藏中息屏、桌面接力和快速反转。最终圆景 scale=0.22，留光 mask=0，稳定区间断言通过。没有修改原照片运动曲线。 |
| GPU 回归 | 圆景/留光横竖人工图共 7,588 帧通过；每个留光用例覆盖 28 个中间蒙版，无 GL 错误。圆景/留光实际 EglWorker 消费窗口及 Gradient 双 worker 测试通过，隐藏后停止绘制。 |
| 离线回归 | 原状态 18、权限/裁切 56、共享运动 23、立体 6 个断言通过；604 项结构/资产检查及 48 个 Java 文件 SDK34 编译通过。 |

隔离测试使用本机 Xiaomi 15 / Android 17 / Adreno 830，不安装 APK、不应用壁纸、不操作电源键、不读取用户照片。这里只验证运动层和渲染回归，**没有验证新 APK 的真实 AOD、厂商回调顺序、功耗或所有快速按键组合**。候选版不会被自动安装。

最终构建和包校验记录见 `evidence-v4/`。Lint 仍存在原工程的 3 个 error：`DesktopWallpaperInjector.kt:104` 缺权限，`ParallaxBg.kt:1070`、`ScreenFlash.kt:51` 缺接收器导出标记；没有隐藏 lint 或修改这些无关功能来宣称全绿。

复跑：

```powershell
python tools/verify_alive.py
.\gradlew.bat :app:assembleDebug
python tools/test_alive_photo_device.py --serial YOUR_SERIAL --sdk E:/AndroidSdk --java-home E:/AndroidStudio/android-studio/jbr
# 只跑 Android Looper 动画中断测试：在上一命令末尾添加 --state-only
```

## 用户复测

覆盖安装同签名的第四轮 debug 包，保留应用数据和已有作用域。此轮状态修复在应用/WallpaperService 侧，system_server 协议与 hook 逻辑没有变化，已加载第三轮 hook 时不需要重新勾作用域；可先不重启整机验证。若未重启，LSPosed 启动日志仍显示旧的 integration 03 是正常的，不表示应用侧仍是旧包；以已安装版本号 9 为准。统一整机版本时可再手动重启。

分别对圆景、留光测试：

1. 应用照片壁纸后息屏，观察至少 10 秒：保持圆球/相框收拢态，不能自行展开。
2. 电源键亮屏进入锁屏：才开始展开。
3. 不解锁，在锁屏再按电源键：重新回到收拢态。
4. 展开未完成时再次息屏，重复数次：不能卡在中间形态。
5. 另测指纹直达桌面，检查没有锁屏折返；启航做简单亮灭屏回归。

如仍失败，区分“状态日志误进 LOCK/HOME”和“日志保持 AOD 但画面展开”，这两种情况的下一步修复层不同。原有时钟、快照及完整 HyperAlive 复刻范围不因此扩大。
