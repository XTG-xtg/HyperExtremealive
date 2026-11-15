# HyperExtreme Alive ui33

> 本版本未公开发布 Release；按后续反馈加入去遮罩调整，正式发布由 ui34 替代。

`0.5-alive.7-ui.33-aod-wake` · versionCode `48` · Android 13+ / ARM64。

## 主要更新

- 修正高息屏倍率残留到唤醒阶段的曝光逻辑：进入锁屏／桌面或确认唤醒后，首帧撤销额外曝光，避免叠加到已恢复的锁屏画面；保留展开和压暗过渡。滞后的 DOZE 通知不再重新启用高曝光。
- 加入 20 款 Infinity 壁纸，完善三态转场，支持桌面手势跟随、松手回弹及页面视差。
- 光栅、空间、视差接入 Alive 风格的全屏三态预览；息屏亮度移入预览面板，应用时保存，取消不改设置。
- 所有壁纸共用固定 0–5 倍亮度滑块，20 格、每格 0.25；0 表示保留原始息屏亮度，移除快捷倍率按钮。旧高倍率配置按 5 倍生效，预设与封面保留。
- 非零倍率保持本次息屏的壁纸压暗基准，避免环境亮度回调重新压暗；继续系统唤醒和自动息屏流程。
- 调整 Alive 指纹直接解锁的展开时序，并加强连续息屏／解锁时的旧帧、渲染任务与手势事件保护。
- 动态壁纸资源拆分为独立 ZIP，支持完整性校验及导入；照片壁纸、Infinity、Vivo 系列仍随 APK 提供。
- README 与应用“关于”页补齐 [HyperAlive 开源仓库](https://github.com/Askt-clot/HyperAlive)，保留原作者署名和许可。

## 下载与使用

1. 安装 `HyperExtreme-Alive-0.5-alive.7-ui.33.apk`。这是关闭调试、开启 R8 优化与资源收缩的 Release 构建，沿用此前发布签名，可覆盖安装并保留配置。
2. 使用启航、流光、视频、立体等动态壁纸时，同时下载 `HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip`。进入 Alive 的动态壁纸页，点击“导入动态壁纸”并选择 ZIP，无需解压；此前已导入相同 v1 资源包的用户无需重复导入。
3. 更新后重启系统界面或手机，使新的系统钩子加载。LSPosed 作用域与使用要求见 [README](../../README.md)。

空间预览需要先生成深度缓存；视差预览显示背景与移动效果，系统主体以实机为准。

## 发布验证

- 366 项 JVM／Robolectric／Compose 测试通过，0 失败、0 错误，包含唤醒首帧、滞后 DOZE、低倍率渐变和 AOD 持续保持回归。
- 5 项资源打包测试通过；Release 的 R8、资源收缩、lintVital 和签名检查通过。
- 校验 APK 内 566 个源资源、独立 ZIP 内 48 个资源，以及二者一致的清单与 SHA-256；LSPosed native 入口按 16 KB 对齐。
- 发布签名与此前 GitHub ui7 一致。本轮未新增手机 UI、息屏或压力测试；AOD 唤醒闪亮的实机视觉效果仍需确认。

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| HyperExtreme-Alive-0.5-alive.7-ui.33.apk | 169661461 | `aca39d1be732628111d925b815a35138e1a2e7e0db3b3c2afbe53440f9e1c341` |
| HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip | 44752604 | `a8a30f9c7476d2c8d9f7bfcf36848f76e952e63da3f1d9b060f76464ad6fb4aa` |

感谢 [HyperExtreme / Frederic22](https://github.com/22Frederic/HyperExtreme)、[HyperAlive / AXLES](https://github.com/Askt-clot/HyperAlive) 与 [OpenAlive / Maga-King](https://github.com/Maga-King/OpenAlive) 的开源与分享。
