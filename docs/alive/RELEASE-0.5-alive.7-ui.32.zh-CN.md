# HyperExtreme Alive ui32

`0.5-alive.7-ui.32-aod-slider` · versionCode `47` · Android 13+ / ARM64。

## 主要更新

- 加入 20 款 Infinity 壁纸，完善三态转场，支持桌面手势跟随、松手回弹及页面视差。
- 光栅、空间、视差接入 Alive 风格的全屏三态预览；息屏亮度移入预览面板，应用时保存，取消不改设置。
- 所有壁纸共用固定 0–5 倍亮度滑块，20 格、每格 0.25；0 表示保留系统原始息屏亮度，移除快捷倍率按钮。旧高倍率配置按 5 倍生效，预设与封面保留。
- 非零倍率保持本次息屏的壁纸压暗基准，避免环境亮度回调重新压暗；继续系统唤醒和自动息屏流程。
- 调整 Alive 指纹直接解锁的展开时序，并加强连续息屏／解锁时的旧帧、渲染任务与手势事件保护。
- 动态壁纸资源拆分为独立 ZIP，支持完整性校验及导入；照片壁纸、Infinity、Vivo 系列仍随 APK 提供。
- README 与应用“关于”页补齐 [HyperAlive 开源仓库](https://github.com/Askt-clot/HyperAlive)，保留原作者署名和许可。

## 下载与使用

1. 安装 `HyperExtreme-Alive-0.5-alive.7-ui.32.apk`。这是关闭调试、开启 R8 优化与资源收缩的 Release 构建，沿用此前发布签名，可覆盖安装并保留配置。
2. 使用启航、流光、视频、立体等动态壁纸时，同时下载 `HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip`。进入 Alive 的动态壁纸页，点击“导入动态壁纸”并选择 ZIP，无需解压。
3. 更新后重启系统界面或手机，使新的系统钩子及息屏亮度保持逻辑加载。LSPosed 作用域与使用要求见 [README](../../README.md)。

空间预览需要先生成深度缓存；视差预览显示背景与移动效果，系统主体以实机为准。

## 发布验证

- 363 项 JVM／Robolectric／Compose 测试通过，0 失败、0 错误。
- 5 项资源打包测试通过；Release 的 R8、资源收缩、lintVital 和签名检查通过。
- 校验 APK 内 566 个源资源、独立 ZIP 内 48 个资源，以及二者一致的清单与 SHA-256；LSPosed native 入口按 16 KB 对齐。
- 发布签名与 GitHub 已发布 ui7 APK 一致。本轮未新增手机 UI、息屏或压力测试；自动化检查不等于实机视觉验收。

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| HyperExtreme-Alive-0.5-alive.7-ui.32.apk | 169661461 | `71105460bb2e33f068ed9056a9aa3c60972e9dfef9456f567ba552d4a45224f7` |
| HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip | 44752604 | `a8a30f9c7476d2c8d9f7bfcf36848f76e952e63da3f1d9b060f76464ad6fb4aa` |

感谢 [HyperExtreme / Frederic22](https://github.com/22Frederic/HyperExtreme)、[HyperAlive / AXLES](https://github.com/Askt-clot/HyperAlive) 与 [OpenAlive / Maga-King](https://github.com/Maga-King/OpenAlive) 的开源与分享。
