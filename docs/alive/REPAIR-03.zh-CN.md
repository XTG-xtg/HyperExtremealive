# 第三轮：启航、流光、圆景与留光

候选版本：`0.5-alive.3` / versionCode 8。日期：2026-11-05。

## 改动不是更换封面或调整圆形遮罩

- **启航**：将 Cosmic AOD anchor 对齐原版 `.165`；不再把桌面 offset / 手指坐标当作时钟锚点。真实双槽共享 CPU 运动实例，GL 资源仍分别归各自线程。
- **流光**：取消额外 17ms sleep 和误作用于前台预览的 1fps 限制，改用显示 vsync。初始化不再从 uptime 0 跳到系统运行时间，形状与颜色使用同一时间基准。
- **圆景**：删除原简化 `PhotoRenderer` 的 disk/full-image 混合 shader。接入 OpenAlive `SceneMotion`、`SceneMatrices`、`TextureMotion` 和 Rust GLES frame graph，包含透镜畸变、模糊、装饰、涟漪、旋转及三态混合。
- **留光**：接入 `FrameMotion`、`FrameSample`、`MaskFrames`，使用 `leave_light` 的真实起止帧、时间索引、前后照片纸张变换。异步解码时锁定整个采样包；蒙版未就绪不交换窗口缓冲，避免旧蒙版与新几何拼接。
- 照片动画使用原 Android Animator 曲线，由 `SceneTimeline` 按事件时间 seek；不跨 GL 线程共享 Animator。已有隐藏引擎接收 CPU 状态事件但不绘制；新引擎通过有界事件历史重建未完成的 AOD → 锁屏 → 桌面链。
- 中文预览加入三态演示、整图取景、留光相框独立取景/旋转、锁屏原图/条纹玻璃/曲面玻璃。默认原图，预览与应用使用同一渲染器；编辑只写草稿，应用后才复制参数。
- 移除缺照片时自动合成假壁纸的 fallback，缺图明确报错。退出时先释放运动会话再关闭渲染器，避免 mask/animator 生命周期悬空。

## 来源与构建

OpenAlive commit：`2d54c72a573b2f66e20b558283bef58cdb0ada60`。

`alive-photo/` 包含 Rust 源码、锁定 Cargo 依赖和工具链版本；没有引入原版 HyperAlive 的反编译类、JAR 或 APK。JNI 包名迁移到本项目，省略无关的小米 surface lease，补充取景入口。Java 层增加本宿主的生命周期、事件时间驱动与草稿配置。完整来源哈希在 `PROVENANCE.json`。

OpenAlive 自实现部分 MIT；官方 shader、蒙版与其他资产保留原权利人的权利，不能把来源标注视为再分发/商用授权。新增原生库编译后约 0.5MB，不引入 414MB 场景运行库。

## 已做验证

测试设备：Xiaomi 15 / Adreno 830 / Android 17 API 37。以下 GPU 测试均用隔离 `app_process`，未注册或应用 WallpaperService，未读取用户照片。

| 项目 | 结果与边界 |
| --- | --- |
| 启航/流光参考比较 | 22 个 Cosmic + 3 个 Gradient 样式，亮/暗 50 组，7 段 mode 序列、每段 4 个采样，共 1,400 次 GPU 图像比较；同资产、同尺寸、同时间点，与本机原 HyperAlive 参考实现的采样 MAE 均为 0。不是整机 AOD 一致性证明。 |
| 圆景/留光 | 横/竖人工测试图 × 两种效果，7 段状态序列，每组 1,897 帧，共 7,588 帧，无 GL 错误；每组留光覆盖 28 个不同中间蒙版。验证 pending 包稳定、照片方向、曲面玻璃锁屏与原图桌面不同、缺帧等待、取景边界与重建时序。**没有与原 HyperAlive 做照片像素一致性比较。** |
| 实际 EglWorker 窗口 | 使用被持续消费的 ImageReader Surface，圆景/留光能连续提交帧；隐藏停止，关闭不崩溃。显示处于前台时接近当前刷新率；不等于真实硬件 AOD 刷新率，不是功耗测试。 |
| 回归 | Gradient 双 worker 的共享运动、连续帧、隐藏停止再次通过；旧版本前台 AOD 模拟路径实测 1fps，修复版跟随 vsync。 |
| 静态/JVM | 状态 18、权限/裁切 56、共享运动 23、立体 6 个断言；结构/资产检查和新增 Java SDK34 编译结果见 `verification.json`。 |
| 构建 | `assembleDebug` 成功；原生 ELF 为 AArch64、16KB 页对齐。 |
| Lint | 3 errors / 226 warnings / 17 hints。3 个 error 位于原 `DesktopWallpaperInjector.kt`、`ParallaxBg.kt`、`ScreenFlash.kt`；未通过隐藏 lint 或改无关文件伪造通过。 |

可复跑照片/窗口测试（先构建，已有整合 APK 的资产需在设备可用，需 shell root）：

```powershell
python tools/test_alive_photo_device.py --serial YOUR_SERIAL --sdk E:/AndroidSdk --java-home E:/AndroidStudio/android-studio/jbr
```

它只在明确命名的 `/data/local/tmp/hyperextreme-photo-test` 与 `hyperextreme-photo-worker` 写测试文件，不安装 APK、不设置壁纸、不改 LSPosed、不重启。日志/PNG 位于 `build/alive-photo-device/`。

## 当前手机上“无法应用”的状态

2026-11-05 18:19 左右的只读检查：

- 已安装 `0.5-alive.2` / vc7，更新时刻 **17:33:04**。
- 当前设备 uptime 约 4,910 秒，对应本次启动约 **16:57**；启动日志仍显示 `Alive integration 01`，新 system_server 适配器未在本次日志中出现。
- LSPosed 本地副本查询确认模块已启用、作用域**已包含 `system`**。不能再说用户没有勾选系统框架。
- UID1000 helper query：两个服务声明有效，`AMBIENT_WALLPAPER=-1`；当前两侧仍是原系统静态壁纸。

因此“已更新安装包/已勾 scope”和“新代码已进入本次 system_server”仍是两回事。此次没有安装 v8、重启、替换壁纸或修改数据库。需要协调安装/重启后再次预检；权限转为 0 也仅是前置条件，不是绑定成功证明。

## 仍需整机验收

1. 用户协调覆盖安装候选包并重启。已有 `system` 不必反复勾选；查新 system_server adapter 日志和 helper `AMBIENT_WALLPAPER=0`。
2. 获准替换当前壁纸后，实际应用并回读两侧组件，确认首帧、预览/桌面/锁屏取景一致。
3. 检查息屏黑底是否遮挡、时钟位置、指纹直达桌面的双槽接力、横竖屏与引擎重建。
4. 检查原分辨率 GPU/内存压力、AOD 停帧策略、电量/温度和重启恢复。当前窗口探针不是这些指标的替代品。

时钟/AOD 厂商适配与系统壁纸快照等历史未完成项目并未因本轮渲染修复而自动完成。不得据此宣称整套 HyperAlive 已完整复刻。
