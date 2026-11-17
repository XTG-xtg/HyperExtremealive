# ui36：接入小米实际的 AOD 面板亮度路径

版本：51 / `0.5-alive.7-ui.36-aod-panel`。用户完整重启后确认息屏不再二次变暗。以下保留 ui36 实现记录；ui37 自适应亮度与背光缓动见 `UI37-ADAPTIVE-GLIDE.zh-CN.md`。

## 依据

ui35 已加载，壁纸遮罩日志确认从 0.5／0.3 改为 0。一次记录中，亮屏约 250 nits，进入全屏 AOD 后约 32.86 nits，后续 DOZE_SUSPEND 仍为该值。单凭该记录不能把肉眼感知的每次变暗归因于传感器更新。

核对本机系统服务后确认：`DozeBrightnessStrategy.updateBrightness` 优先返回小米策略；小米全屏 AOD 分支直接读取 `DozeAutoBrightnessController.getDozeBrightness`，忽略 `DisplayPowerRequest.dozeScreenBrightness`。因此 ui35 对 SystemUI `DozeService.setDozeScreenBrightness` 的保持无法控制这条实际路径。入场后冻结首个已经压低的 Doze 值，也无法维持刚进入 AOD 时的亮度。

## 修改

- 在系统服务的 Doze 策略返回处克隆原生亮度状态，只替换亮度请求，保留原因、上限、HDR、写设置标志等原生信息；后续温控／电源约束继续处理。
- 非零倍率取本次入场的普通亮度请求作为基准；若不可用，回退到首次有效 Doze 值。0.25–5 倍继续由壁纸 shader 处理，面板不重复乘倍率。
- 限定主屏、非交互、实际绑定本模块 Home／Lock／Spatial／Raster 服务且开启小米全屏 AOD。入场的 ON 和稳定的 DOZE／DOZE_SUSPEND 均覆盖；独立静态锁屏和其他服务不覆盖。
- 唤醒、自动 OFF、普通显示策略、壁纸／用户切换、0 值释放保持。无效值、黑屏请求及未知 ROM 接口保留原生值。
- 壁纸归属在配置工作线程读取，显示策略钩子内不查询 WallpaperManager。记录范围判断和原生／保持亮度，方便下一次实机日志核对。

## 验证边界

新增入场保持、连续传感器更新、无双重倍率、0 值、唤醒／超时、主屏范围、原生字段保留及未知接口回退的回归检查。现有 Infinity 星点和唤醒首帧曝光修复继续保留。

本次未自动操作手机息屏、解锁或重启。ui36 的新钩子位于 `system_server`，安装后需要用户完整重启手机，仅重启 SystemUI 不足以加载。保持请求不等同于绕过硬件或温控上限，也不保证与预览的绝对物理亮度一致。

本机 ROM 摘录与原始日志仅留在本地 `build/aod-fixed-followup-20261115/`，不进入开源仓库或 Release。
