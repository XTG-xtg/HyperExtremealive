# Alive 第二轮故障定位与修复候选

日期：2026-11-05。APK：0.5-alive.2 / versionCode 7。

## 结论

上一版不仅缺少实机验收，还存在会阻断应用的代码与作用域错误。本轮修复已编译并做无修改实机预检和 GPU 离屏测试，但**尚未安装 v7、加载新的系统框架 Hook 或执行实际双侧应用**。不能宣称所有功能修复完成。

## 证据

设备：Xiaomi 15，HyperOS OS4.0.0.19.XOCCNXM，Android API 37，LSPosed 2.2.1 (7912)，KernelSU 3.3.1。

1. 手机已安装 v6 (0.5-alive.1)，并非用户装错或未重启。
2. 只读检查 LSPosed 数据库副本：HyperExtreme 已启用，作用域包含 `android`，但不含 `system`。原 HyperAlive 的作用域包含 `system`；它当前停用，不能把这次失败归咎于两个模块重叠。
3. LSPosed 启动日志能看到 HyperExtreme 的 SystemUI/AOD Hook，找不到它在 system_server 的加载记录，与作用域结果一致。
4. 16:59:31 与 17:01:01 的系统日志已进入 `WPMS.bindWallpaperComponentLocked`，目标是 HyperExtreme 的 HomeWallpaperService。说明“获取了 root”或“调用到了设置方法”不等于绑定成功。
5. 从本机 `/system/framework/services.jar` 提取的 `bindWallpaperDescriptionLocked` 代码明确检查支持 ambient 的壁纸服务包是否拥有 `android.permission.AMBIENT_WALLPAPER`；不满足系统豁免条件时拒绝绑定，错误字符串为 `Selected service does not have android.permission.AMBIENT_WALLPAPER`。
6. UID 1000 的只读预检实测返回 `AMBIENT_WALLPAPER=-1`，两个服务声明检查均通过。上一版确实没有对应的本包权限 Hook。这是必要条件不满足的直接证据；旧设置调用的完整异常输出未保留，不能声称已拿到当次完整异常栈。

完整 LSPosed 数据库、其他模块清单和原始系统日志只保留在本机 work 排查目录，不随源码交付。

## 代码修正

- 推荐作用域 `android` → `system`，添加结构回归检查。
- 将息屏权限适配限制在本包与唯一权限名，额外验证其他包和权限不被放行。没有全局权限绕过。
- helper 增加 `check` 操作；在任一壁纸设置调用前检查权限和服务。App 在复制照片/保存场景前先执行预检。失败返回明确的系统框架提示。
- 设置后对组件名有界等待并检查两侧最终状态，不把调用未抛异常当作成功。组件校验仍不等同于首帧/视觉验收。
- 使用 `su -M`；Root 结果持久化并写入 `HyperExtremeAlive` 日志，不因离开页面丢失。
- 预览使用全屏渲染区域，按钮不再改变渲染宽高比；启用上游 MSAA EGL 配置；可见性回调不再重置动画，转场中改变目标保留当前帧。
- 照片 UV 裁切从错误的放大采样范围改为不超过 1 的中心裁切，消除缩小照片后重复采样边缘的问题。

## 本轮验证

| 检查 | 结果与边界 |
| --- | --- |
| assembleDebug | 成功，版本 7 |
| JVM | 状态机 18、立体运动 6、权限/裁切回归 56 个断言通过 |
| 结构/资产 | 571 项通过；38 个新增 Java 文件在 SDK 34 另行编译通过 |
| 新 helper 实机 query | UID 1000 查询成功，显示息屏权限 -1 |
| 新 helper 实机 check | 返回 10，明确未就绪；没有调用 apply |
| GPU 离屏 | Adreno 830 上用新 Java 字节码绘制 1/201/301/501/601 五个样式的三种状态，共 15 帧，无 GL 错误，均非全黑 |
| 照片裁切对照 | 同一人工测试图分别经已装 v6 和新字节码渲染，已人工检查裁切结果；未读取用户照片 |
| 签名 | 已装 v6 与新 v7 证书 SHA-256 相同，apksigner verify 通过 |
| 对齐 | zipalign -c -P 16 4 通过 |
| 壁纸保护 | 排查前后 dumpsys 中组件与 id=40 保持一致；没有安装/卸载、清数据、写作用域或重启 |
| lint | 仍为原工程 3 errors / 226 warnings / 17 hints，未隐藏；非 Alive 文件中的旧问题，详见历史验证记录 |

GPU 测试采用独立 app_process + EGL pbuffer，不是 Activity 截图，也不是 WallpaperService 的系统绑定测试。没有检验视频、留光 602、暗色变体、全部风格、真实转场或功耗。新 UI 的全屏布局仍待安装后检查。

## 未完成与下一步

1. 由用户协调覆盖安装 v7（签名相同，无需卸载），在 LSPosed 为 HyperExtreme **手动勾选系统框架**，然后重启。不要把旧 `android` 项当作已经选中。
2. 先执行诊断页只读查询：权限应为 0；核对 LSPosed 系统日志出现 `Alive system_server adapter v2 starting` 和 PM 权限 Hook。否则停在诊断，不继续替换壁纸。
3. 用户确认允许替换当前桌面/锁屏后，先用启航样式测试：验证两侧组件、服务引擎和真实画面，再测熄屏、亮屏、指纹与重启。
4. 照片效果仍未完成：当前修复的是裁切，不是原版镜头/留光管线。完整还原需要继续移植 OpenAlive 的照片渲染与矩阵/蒙版时序，或在用户提供原 HyperAlive 源码后对照实现；不能把简化效果作为该项验收通过。

测试入口：`tests/alive/device/RenderProbe.java`。这是单独的开发探针，不打包到正式应用；只在 `/data/local/tmp/hyperextreme-render-probe` 写入人工测试图和 GPU 帧，不注册壁纸服务、不改应用设置。
