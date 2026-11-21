# HyperExtreme Alive ui36

`0.5-alive.7-ui.36-aod-panel` · versionCode `51` · Android 13+ / ARM64。

当前为修复候选，保留 Release 草稿，实机息屏效果待确认。

## 本次修复

- 针对 ui35 仍会变暗的问题，接入小米实际采用的系统服务 AOD 亮度策略。小米全屏 AOD 会绕过 SystemUI 的 Doze 亮度请求，直接读取独立光感控制器，之前的保持未覆盖这条路径。
- 非零倍率下，本模块原生壁纸的主屏全屏 AOD 保持入场面板亮度请求；倍率仍由壁纸曝光处理，不重复乘到面板。唤醒、自动 OFF、壁纸切换或设为 0 时释放。系统温控与硬件限制继续生效，未知接口保留原生值。
- 保留 Infinity 星点脉冲／分组闪烁修正，以及 AOD 唤醒首帧撤销额外曝光的处理。

## 累计功能更新

- 20 款 Infinity、三态转场、桌面手势跟随、松手回弹与页面视差。
- 光栅、空间、视差接入全屏三态预览；息屏亮度统一为 0–5、20 格、每格 0.25，0 保留原始亮度，取消快捷倍率按钮。
- 非零亮度移除全屏 AOD 壁纸遮罩，保留全黑隐藏、唤醒与自动关闭；加强 Alive 连续息屏／指纹解锁时序和渲染保护。
- 动态壁纸资源拆为独立 ZIP；README 和关于页补齐 [HyperAlive 作者](https://github.com/Askt-clot)与[开源仓库](https://github.com/Askt-clot/HyperAlive)。

## 安装

1. 安装 `HyperExtreme-Alive-0.5-alive.7-ui.36.apk`，沿用此前发布签名，保留配置。这是关闭调试、开启 R8 与资源收缩的 Release 构建。
2. **安装 ui36 后完整重启手机**。新钩子位于 `system_server`，仅重启 SystemUI 或应用不能加载。
3. 启航、流光、视频、立体等动态壁纸需导入 `HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip`，无需解压；已导入相同 v1 包的用户无需重复导入。照片、Infinity 和 Vivo 系列资源随 APK 提供。

LSPosed 作用域与兼容性要求见 [README](../../README.md)。

## 验证

- 379 项 JVM／Robolectric／Compose 测试通过。首次全套中壁纸库界面等待超时，独立重跑全套通过；未改动该界面测试。
- Release 构建、签名与资源校验完成；APK 内 566 个源资源、ZIP 内 48 个资源及清单摘要一致，native 入口按 16 KB 对齐。
- 未代用户执行手机息屏、解锁或重启。ui36 实机视觉效果尚未确认。

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| HyperExtreme-Alive-0.5-alive.7-ui.36.apk | 169661461 | `c8a9b18d49b4f2e73c7b662892a2b7b75c836b55ae090d933a064a8814aee32b` |
| HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip | 44752604 | `a8a30f9c7476d2c8d9f7bfcf36848f76e952e63da3f1d9b060f76464ad6fb4aa` |

感谢 [HyperExtreme](https://github.com/22Frederic/HyperExtreme)、[HyperAlive](https://github.com/Askt-clot/HyperAlive) 与 [OpenAlive](https://github.com/Maga-King/OpenAlive) 的开源与分享。
