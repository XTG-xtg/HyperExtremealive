# HyperExtreme Alive ui37

`0.5-alive.7-ui.37-adaptive-glide` · versionCode `52` · Android 13+ / ARM64。

实机测试候选。参考 [tangtang-dotcom/HyperExtremealive_go](https://github.com/tangtang-dotcom/HyperExtremealive_go)，接入桌面跟随修正、自适应 AOD 亮度与唤醒背光缓动。

## 修改

- Infinity 支持小米桌面自动步长 -1；预览内的保持开关应用成功后保存，仅维护当前 Infinity 桌面，关闭或换壁纸恢复原设置。
- 保留 0–5、20 格／每格 0.25。受支持的小米全屏 AOD 跟随原生环境亮度，暗处最高增强 5 倍，亮处最高 2.5 倍，面板接管时不重复叠加画面曝光。0 保留原始亮度。
- 本模块全屏 AOD 唤醒时同步缓动背光与 SDR，短暂保持后渐变恢复系统目标，整个窗口最多 2.5 秒。连续息屏、黑屏、0 值、失去壁纸归属或 DIM 会取消；温控及低电量上限优先。
- 空间／光栅独立壁纸进程读取统一面板状态，避免重复增益。未知系统接口回退原生。

实现范围及参考提交见 [ui37 记录](UI37-ADAPTIVE-GLIDE.zh-CN.md)。ui36 已由用户确认重启后不再二次变暗，ui37 采用新的自适应策略，需要重新实测。

## 安装与验证

安装 `HyperExtreme-Alive-0.5-alive.7-ui.37.apk` 后需**完整重启手机**加载 system_server 新钩子。覆盖安装保留配置，沿用此前 Android Debug 证书，以保持安装兼容；Release 已关闭 debuggable、开启 R8 和资源收缩，没有新增独立生产签名密钥。

已导入相同 v1 动态资源包时无需重复导入。未自动操作手机息屏、解锁或重启。

- 396 项 JVM／Robolectric／Compose 测试通过，0 失败、错误和跳过。
- Release 构建及 lintVital 通过，签名与 ui36 一致，native 入口 16 KB 对齐。
- APK 内 566 个源资源、ZIP 内 48 个资源及清单摘要校验一致。
- 真实唤醒闪烁、自动亮度变化及桌面手势效果由用户在小米 15 验收。

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| HyperExtreme-Alive-0.5-alive.7-ui.37.apk | 169677845 | `e024356ed64b79f4ff9fcd390e3d05b52e60e0ac72b22eb8beb2f793b4563559` |
| HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip | 44752604 | `a8a30f9c7476d2c8d9f7bfcf36848f76e952e63da3f1d9b060f76464ad6fb4aa` |
