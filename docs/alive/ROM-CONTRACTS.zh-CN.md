# 当前 HyperOS ROM 的只读契约核对

2026-11-06，通过 ADB 只读获取系统 APK 并检查 DEX。没有安装或替换系统包，没有
修改手机配置。原始 APK 仅在本机 `.reference/rom/`，不放进交付源码或候选 APK。

| 系统包 | SHA-256 |
| --- | --- |
| `/product/priv-app/MIUIAod/MIUIAod.apk` | `4f3b62bc17600beb3b732dbbfa4e3eb42a690c4050bbc722782a553200d3cc84` |
| `/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` | `a0e738e41fe599b97950cbf52a9e2ddc6ae2ceff986efbacb1c9840bea78768d` |

## 已据此修正

- 系统锁屏控件是 `com.android.keyguard.clock.KeyguardClockContainer`，确有
  `onAttachedToWindow()`，不是旧候选中的 `com.android.keyguard.KeyguardClockContainer`。
- `com.miui.aod.AODView` 和 `com.miui.aod.linkagestyle.LinkageStyleClockView` 均有
  `onAttachedToWindow()`。新增后者作为几何观察入口，观察器按根 View 去重。
- `DozeHost.disableShowWallpaper()` 返回 **void**，其实现清除 `mNeedShowWallpaper`
  并把 `mAODView` 设为黑色背景。原先只匹配 boolean 的 Hook 不会生效。
- `AODView.makeNormalPanel()` 在调用该方法后还会自行设置黑色背景。因此只短接
  disable 方法不够。本轮仅在正常时钟模板调用期间、Alive 运行中且系统已经允许
  显示壁纸时保留透明背景。退出调用后 ThreadLocal 立即清理。
- `mNeedShowWallpaper`、`mKeyguardOccluded`、`mFingerprintPressed` 三个布尔字段在
  当前 DEX 中存在。必须分别为 true / false / false 才会保留壁纸；字段未知则不干预。
- 时钟搜索先遍历完整树寻找有效时钟容器，再尝试时间 View，避免先命中状态栏小钟。
  两项 Robolectric 测试覆盖优先级、隐藏控件及全屏容器排除。

## 不跨越的边界

`showSecurityIdentityViewAt()` 会结合时钟类型、系统 AOD 开关及壁纸 ambient 支持来
决定 `mNeedShowWallpaper`。本轮不把这些系统设置伪装成开启，也不强制替换系统时钟
样式。不影响来电遮挡、指纹遮罩、通知隐私、息屏时段、超时或面板亮度策略。

以上是**静态代码契约核对**，不是已在 SystemUI/AOD 进程中命中 Hook 的证据。
仍需覆盖安装候选后，按 REPAIR-07 的步骤检查系统状态顺序、布局、防烧屏及指纹。
其他 HyperOS 版本不保证使用同一类名、方法或字段，不能将本机结论推广成全版本支持。
