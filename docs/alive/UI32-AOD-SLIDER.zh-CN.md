# ui32：全屏预览与固定息屏亮度刻度

版本：47 / `0.5-alive.7-ui.32-aod-slider`。

## 用户可见变化

- 光栅、空间、视差接入 Alive 共用的全屏预览布局，可切换息屏／锁屏／桌面，息屏亮度放在预览底部面板。功能页不再单独放亮度卡片。
- 所有壁纸共用固定 0–5 的线性滑块，分成 20 格，每格 0.25。0 表示不修改系统原始息屏亮度。数值显示与滑块实际值一致，拖动不再扩大量程。
- 移除快捷倍率按钮与多余的数字输入／设置按钮。应用时保存，取消保留原值。
- 非零倍率在本次 AOD 会话中保持进入时的壁纸压暗基准；系统环境亮度更新不能重新改变该基准。全黑自动关闭、唤醒和 0 值仍保留系统行为。
- 历史超过 5 倍的配置按 5 倍生效；旧预设校验原内容摘要后迁移为新摘要，并保留封面及 `.json.ui31` 原记录供回退。

## 验证

- 定向构建与 34 项测试通过：固定范围与数值刻度、连续向右拖动不回退、三类预览草稿／取消／应用、小屏布局、0 值语义、高倍率旧数据、预设迁移、系统方法契约与壁纸归属、AOD 会话压暗保持。
- 额外验证服务稳定息屏持续 60 秒后仍保留所选倍率、不新增空闲绘制；切到 0 恢复曝光 1。定向服务测试覆盖转场反向与稳定保持。
- 使用实际导出的 SpatialGlCore、RasterGlCore 着色器进行桌面 GL 验证：0 为原始曝光、最大 5、亮屏不受倍率影响、空间裁剪和三类运动可见，无 GL 错误。
- Compose 截图是布局测试图片；桌面 GL 使用合成测试素材。未操作手机上的壁纸选择、息屏、指纹或压力测试。

构建日志：`build/aod-slider-20261115/validation.log`、`hold-validation.log`。
布局图：`app/build/reports/aod-brightness-ui/brightness-control.png`、`app/build/reports/effect-preview/`。
GL 验证：`build/effect-preview-20261115/validate_gl.py`、`app/build/reports/effect-preview/desktop-gl-validation.json`。

## 交付

APK：`deliverables/HyperExtreme-Alive-ui32-aod-slider-debug.apk`。
SHA-256：`ed8cb374d1b17d9e5859d4bb836bf18ded706e5bf5961ebd058bf8bbd95f6806`。
签名 SHA-256：`52395a7301d94176099f0a1c610406bf0291abad9a2198858f32bc9b9f46942d`，与此前版本一致。

已覆盖安装到小米 15（197fb721），ADB 返回 Success；包管理器确认 versionCode 47、ui32，更新时间 2026-11-15 20:30:41。安装记录：`build/aod-slider-20261115/install.log`。

应用内预览安装即可使用；新 SystemUI 亮度保持和视差背景亮度代码需重启系统界面或手机后加载。由用户自行重启、测试，未自动执行。
