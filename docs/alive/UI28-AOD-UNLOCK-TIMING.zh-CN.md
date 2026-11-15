# ui28：息屏指纹直达桌面的照片展开时机

2026-11-15，用户反馈 Alive 壁纸在桌面已经出现后才开始展开。

## 定位

只读获取小米 15 的现有日志与配置，当前为 601 透镜照片样式、默认桌面及锁屏效果。没有操作指纹、屏幕电源或更换壁纸。

一次现有指纹解锁日志中，壁纸于 18:06:00.965 收到 `HOME / keyguard-going-away`，系统于 18:06:01.238 才报告 `onKeyguardExitFinished`。因此该次状态通知并未等到桌面完成后才送达。

照片主画面 `SceneMotion` 和照片形状动画 `FrameMotion` 在稳定 AOD 展开时额外设置了 150ms 的顶层启动等待；桌面纹理效果也经由 `SceneMotion` 继承等待。它与系统解锁动画叠加，造成展开起步偏晚。新增 CPU 动画测试在修改前复现首帧几何、纹理和形状时间线停留在息屏状态。

## 修改

- 去掉两处顶层 150ms 等待，从注册表提供的唤醒／已确认解锁时间立即推进动画。
- 普通唤醒也采用同样的即时起步。各样式自身的曲线、时长和内部轨道错峰保持不变。
- 桌面引擎晚可见时继续按事件时间追赶，快速重新息屏从当前几何状态连续收回。
- 本轮不改系统认证判断、解锁状态通知、电源保护或 SystemUI Hook。

## 验证与交付

- `PhotoUnlockTimingTest` 修改前 4 项失败、修改后 4 项通过；覆盖透镜首帧、桌面效果、5 种形状动画、桌面接管与反向息屏。
- 连同 `AlivePowerLifecycleTest` 8 项和 `AliveIntegrationTest` 6 项，共 18 项通过，无失败／错误／跳过。
- Debug 构建与签名校验通过，版本 43 / `0.5-alive.7-ui.28-unlock-timing`，证书与 ui27 相同。
- 日志和回归 XML 保存于 `build/aod-fingerprint-unlock-20261115/`。
- APK：`deliverables/HyperExtreme-Alive-ui28-unlock-timing-debug.apk`。安装后本轮动画代码随应用进程更新，不需要再次重启系统；实际显示由用户自行测试。
