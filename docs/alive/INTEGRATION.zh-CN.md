# HyperExtreme × Alive：首轮源码整合

> 当前修复候选：0.5-alive.2 / versionCode 7。首轮版本的实际应用失败已复现到必要权限检查，不能视为可用成品。详见 `REPAIR-02.zh-CN.md`；本页的首轮设备状态保留为历史记录。

## 第二轮修正

- 本机 LSPosed 的系统框架标识是 `system`，不是首轮误写的 `android`。升级 APK 不保证已有选择自动迁移，需要手动勾选系统框架并重启。
- 在 system_server 的 `PackageManagerService.checkPermission` 上仅为本包的 `AMBIENT_WALLPAPER` 返回通过；不绕过包归属、不授予任意包权限、不写 LSPosed 数据库。
- helper 新增无修改的 `check USER_ID`；应用前检查息屏权限和两个服务声明，失败返回 10，在改照片/配置/壁纸前终止。`query` 也展示权限状态；原始结果持久化到独立诊断 prefs。
- Root 命令使用 KernelSU 已确认支持的 `-M` 全局挂载命名空间，仍以 UID 1000 和 callingPackage `android` 调用壁纸接口（这里的调用包与 LSPosed 作用域不是同一概念）。
- 预览渲染区域改为全屏，控制条覆盖在上面；接上上游 MSAA EGL 选择；可见性变化不重放动画；正在进行的动画从当前帧转向新目标。
- 修正照片中心裁切公式。照片仍是简化实验渲染器，锁屏/桌面同构图，缺少完整光学与留光时序；在库、预览和应用确认中明确标注，不冒充原版还原。

## 来源和范围

- HyperExtreme 基线：`7321d76102da566264f8773671ec64172eae7849`，原始工作区干净。
- 渲染源码上游：OpenAlive `2d54c72a573b2f66e20b558283bef58cdb0ada60`。
- 中间实现：本工作区的 HyperAlive-Lab；这不是原版 `com.qihang.aod` 的源码。
- 主工程 AGPL-3.0 许可不变；导入的 MIT 源码保留许可。素材权利另计。
- `IMPORT.json` 记录 Lab 源码输入哈希与重命名路径；`PROVENANCE.json` 保留原上游台账，其 Java 目标路径是历史 Lab 路径，整合路径应结合 `IMPORT.json` 阅读。

## 用户入口

功能页增加“Alive 动态壁纸”，沿用 `MaskDetailActivity`、Miuix 主题、MiSans 字重、深浅主题、可收起标题与系统返回转场。此分支独立于 `MaskUiState`，不触发原有遮罩选图流程。

壁纸库 / 设置 / 诊断在同一二级页内切换。使用真实素材封面，分类展示；封面不假装是实时渲染。选择场景和设置只改变待应用值，进入预览不写已应用配置。Root 输出仅在诊断页展示。双侧替换需确认，文案明确指出两次 Binder 调用并非原子事务。

照片最大边限制 2560 像素，只存应用私有目录。导入写 `files/alive/photo-draft.png`，预览优先读草稿；应用才复制到 `files/alive/photo.png`。原项目候选与快照目录不复用。

## 架构与防冲突边界

| 层 | 整合后实现 | 边界 |
|---|---|---|
| UI | `alive/AlivePage.kt`、`AlivePreviewActivity.kt` | 中文、沿用现有 Miuix；不移植 Lab 的原生测试按钮页 |
| 配置 | `alive/Config.java`、`ConfigProvider.java` | 私有 `alive` prefs；Hook 仅通过 Provider.call 获取配置和发状态 |
| 服务 | `alive/HomeWallpaperService`、`LockWallpaperService` | 同一 App 进程；不加入原空间服务的 `:wallpaper` 进程 |
| 状态 | `EngineRegistry`、`core/AliveState` | 0 息屏 / 1 锁屏 / 2 桌面，单调时间，180ms 迟到锁屏提示抑制 |
| Hook | 原 `HookEntry` 分发给 `AliveHooks` | 仅 API 102；system 使用 `onSystemServerStarting` 提供的 loader |
| 电源 | PMS / BinderService 前馈 + Scrim 状态 | 不改原方法结果；Provider 调用放工作线程，避免持有系统锁时跨进程 |
| AOD | 可选 DozeHost hook + 实验 TextClock 叠加 | 仅配置开启且真实 Alive 引擎存在时介入；不是完整时钟替换 |
| Root | `alive-root/.../WallpaperTool.java` | system UID 1000、callingPackage android；只能设置两个固定 Alive 服务 |
| 打包 | `prepareAliveHelper` | 构建时 Java → D8 → DEX 039，产物放生成 assets；无核心预编译 JAR |

### 合并中特别修复的点

1. **空间壁纸判定不能再只看包名**：`SpatialServiceMode.isRunning` 和系统 picker fallback 改成精确匹配 `SpatialWallpaperService`，避免把新 Alive 服务误当成空间服务。
2. **预览不注册双槽状态**：独立预览直接使用自己的 EGL worker；系统壁纸选择器创建的 preview Engine 也不注册到 `EngineRegistry`，不向真实引擎发送状态。
3. **EGL display 的共享生命周期**：同进程 Alive worker 引用计数管理 display，关闭一个预览不应直接 terminate 另一个 Alive worker 的 display；每个 worker 仍拥有自己的 context / surface / GL 线程。
4. **立体传感器真实接线**：陀螺仪四参数接到 `StereoMotion.gyroscope`，不再误接无操作的 `follow`；增加纯 JVM motion 测试。
5. **运行统计隔离**：预览帧数与错误不冒充真实壁纸帧数；诊断的 engine 存在不等于真实 AOD 功能全部通过。
6. **配置开关的生效边界**：Hook 只在保存了 enabled 且存在真实非预览引擎时获得 enabled=true。销毁引擎通知观察者清除时钟叠加。
7. **双侧结果校验**：每次设置回读后，最终再检查一次 home + lock，只有都匹配才输出 `PAIR_VERIFIED`。失败不声称成功，也不擅自恢复旧壁纸。
8. **双向启用保护**：Alive 应用检查空间叠加 / 视差 / 遮罩保护；原视差、遮罩替换、空间叠加的启用入口检查 Alive 服务。切换到其他系统壁纸由用户操作，停用 Hook 不等于恢复壁纸。

## 实机已做和未做

设备为已连接的 Xiaomi 15，Android 17 / HyperOS 4。当前原版 HyperAlive 的两侧组件在此次只读验证前后保持一致。

- 已将新源码编译出的受限 helper 暂存到 `/data/local/tmp/hyperextreme-alive-audit/`，以 system UID 执行 `query 0`，成功读到原版两侧组件。证据：`device/helper-query.json`。
- 完整 APK 生成后，又对 APK 内同 SHA-256 的 helper 执行了只读查询，返回码 0；证据：`device/apk-helper-query.json`。
- 已确认 `com.miui.aod` 在本设备 UID 为 1000，符合新 Provider 的 UID 白名单。
- 没有执行 helper 的 `apply`，没有修改 LSPosed scope 数据库，没有重启或卸载应用。
- 合并 APK 的安装、中文布局实测、真实渲染截图、熄屏 / 指纹直达 / 横屏 / 重启恢复与功耗必须独立验收；只读 query 通过不能当成这些项目通过。

### 安装前注意

本项目仍使用原包名。需要先比较 APK 签名和已装版本；不使用降级 / 卸载 / 数据清除绕过覆盖限制。KernelSU 里 Shell 获授权不等于 HyperExtreme App 已获授权。

本次实测签名不一致，故未执行安装：手机原包证书 SHA-256 为 `32fd4934289e2abde750d831b0e449fec7d4a2a929a62615d34d8b9126506cc7`，本机 debug 包为 `52395a7301d94176099f0a1c610406bf0291abad9a2198858f32bc9b9f46942d`。需要原签名 keystore 的本地路径进行正式签名，无需将私钥内容发到聊天里。

准备实测时，先由用户在 LSPosed 中停用原 HyperAlive 的重叠 Hook，再启用整合版推荐作用域并重启。仍保留原版应用和壁纸组件，便于用户手动切回。

## 验收顺序

1. 验证 APK 完整构建和签名，不先动手机现有安装。
2. 同意更新并解决签名后，检查功能页入口、深浅主题、返回、分类选中和照片选取。
3. 在独立预览页逐类切换息屏 / 锁屏 / 桌面，核对 GL 错误与可见效果，前后比较系统壁纸组件不变。
4. 检查 Root 查询；准备替换时再确认应用，核对 helper 最终双侧回读。
5. 检查 LSPosed 中 system / SystemUI / AOD 的 hook 命中记录，不能只看“模块已激活”。
6. 分别测试真实息屏、亮屏到锁屏、指纹直达、横屏、关闭联动、切回空间壁纸、重启与耗电。

## 尚未完成的原版能力

没有照搬全局权限绕过、冻结拦截或 LSPosed 数据库修改；没有完整全屏 AOD / 黑底清除策略、通知前景效果、系统壁纸分块快照、精确 FlymeClock 替换、完整镜头矩阵 / 留光光学效果。当前共享动画起点是首轮状态同步，不能宣称已做到原版所有跨槽交接的视觉一致性。
