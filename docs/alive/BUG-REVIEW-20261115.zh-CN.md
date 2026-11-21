# 两位用户的壁纸与动画日志排查

> 实机复核更新：ui25 在小米 15 上暴露出 AOD JNI 导致 SystemUI 连续崩溃的回归，已停止作为测试候选。本文下述“本轮”指早先日志分析与本地验证；最新证据、ui26 修复和未完成项目见 [实机复核记录](DEVICE-REVIEW-20261115.zh-CN.md)。

本轮找到配置 IPC 拒绝合法 SystemUI、音乐逻辑主动替换正常动态锁屏、原生音乐控件误触发主体拆除、后台查询异常未捕获、壁纸身份误判及诊断版本写死等问题，已在本地修复。空间壁纸退出有直接日志证据；其具体身份变化原因、用户感受到的指纹延迟，以及早前网易云退出问题，还不能仅凭现有日志判定全部修复。

早先日志分析阶段没有连接、安装、重启或操作手机，当时的候选为 **0.5-alive.7-ui.25-bugfix / versionCode 40（现已撤回）**。随后实机复核发现 AOD JNI 致命崩溃，当前候选改为 ui26；手机状态见上方实机复核记录。

## 输入与证据边界

输入目录：`C:/Users/Administrator/Downloads/Infinity/bug日志`。

| 来源 | 内容与设备 | 版本可靠性 |
| --- | --- | --- |
| 用户 A | `repro_final.log`、`issue_draft.md`；Xiaomi 2410DPN6CC，Android 17 / SDK 37；11 月 14 日 20:26～20:27 | 复现记录报告 ui14 / vc29；没有 APK 可再次核验 |
| 用户 B | `LSPosed_20261115_015735.zip`；popsicle / 2509FPN0BC，OS4.0.4.0.XPBCNXM，Android 17；当前模块日志约 01:08～01:57 | 日志打印 ui18 / vc33，但源码中的标签被写死，不能据此证明实际安装版本 |

ZIP SHA-256：`077295e049ebc282bc1ef9ccf051382f9783bc6e8860c5f5a9824bd425b631ea`。已验证 ZIP CRC，提取时检查目标路径；没有执行日志中的任何内容。

证据存放在 `build/bug-review-20261115/`：`inventory.json` 是文件清单，`analysis.json` 是事件/错误统计与解锁配对，`system-events.txt` 是关键系统记录索引。以下 `modules:L` 指 `user2/log/modules_2026-11-15T01-08-54.402375.log`，`full:L` 指 `user2/full.log`。模块日志中共识别到 7,175 条本模块记录；未把不同缓冲中重复出现的内容相加。

ZIP 包含旧日期的 ANR、崩溃和 tombstone。旧记录与 11 月 15 日现场分开分析。`issue_draft.md` 的解释只作为用户提供的假设，不作为已证实根因。

## 1. SystemUI 独立 UID 被配置 Provider 拒绝

**日志证实，优先级高。** 用户 B 的 SystemUI UID 是 **10231**，并非 1000。

- `modules:67`，01:09:11.161：`Given calling package android does not match caller's uid 10231`。
- 从 `modules:223` 起：`MUSIC_WATCH heal failed: SecurityException: Untrusted config caller: 10231`，该模块日志中出现 **766 次**。
- 从 `modules:263` 起：配置读取因 `Untrusted config caller: 10231` 失败，出现 **39 次**。

原 `ConfigProvider.trusted()` 只放行自身、root 和 appId 1000；这台 ROM 的合法 SystemUI 被拒绝。配置读取、状态/解锁信号、液态交互和音乐恢复共用这个入口，故影响不限于音乐。

另一个独立问题在 `AliveHooks.context()`：Application 尚未创建时借用 package 为 `android` 的 system Context。这在 UID 10231 进程中产生错误的调用归属；即使修好 Provider 白名单，也需要一起修正。

修复：验证实际 caller UID 所属包，额外放行真正的系统/更新系统应用 `com.android.systemui` 与 `com.miui.aod`；普通同名应用、其他系统应用、未知 UID 仍拒绝。优先用实际 Application，只有 system appId 1000 可使用 system Context；独立 UID 进程等待 Application.attach 后再刷新。

验证覆盖专用 UID、其他用户的专用 UID、系统更新应用、普通同名应用、借用其他包 UID、未知 UID、既有 root/system/自身调用，以及 Context 选择。

## 2. 音乐恢复逻辑主动破坏正常动态壁纸

**当前源码可确定的缺陷；不能据此倒推用户 A 的旧包一定走过相同代码。**

原策略在 `musicScreen=true && lockOurs=true` 时返回 `SEED_LOCK_PHOTO`，随后 `WallpaperManager.setBitmap(FLAG_LOCK)` 主动替换本模块锁屏服务。这会丢失动态锁屏、破坏 AOD/展开收起状态。非照片预设还可能使用以前保存的照片，找不到照片时生成黑色图。原音乐结束后的恢复则重新应用桌面、锁屏两套服务，造成不必要的桌面重建。

修复：

- 原生音乐控件显示时保留正常的动态锁屏；移除静态照片/黑图填充。
- 媒体界面离开后，只有 HOME 仍绑定本模块、LOCK 丢失到已知系统默认服务或静态状态时修复 LOCK。
- 应用侧和特权 helper 在变更前各检查一次；其他动态壁纸和已经离开 Alive 的桌面不会被自动收回。
- helper 新增固定的 `restore-lock` 操作，只设置并验证 LOCK，再检查 HOME 没有变化。普通手动应用仍保留原来的双槽验证。
- Android 13 的共享动态壁纸不尝试独立 LOCK 恢复。

局限：静态锁屏没有 live component，不能只凭组件区分“系统回落到静态”与“用户手动换了一张静态锁屏”。当前策略以 Alive 开启且 HOME 仍属本模块为恢复前提。系统在媒体界面持续可见期间强制换走服务的情况，暂时等待界面离开后恢复，需要实机观察。

## 3. 原生音乐控件误触发空间/光栅主体拆除

**当前源码链路可确定，日志尚未记录完整的可见控件切换。**

原 `MusicWatch` 将任何音乐相关 View 和第三方歌词封面都合并成 `Coexist.mcMusicScreen`。`SpatialForegroundHider.handle()` 见到该标志后调用 `SpatialOverlay.onDisabled()` 与 `RasterOverlay.onDisabled()`，移除主体并插回系统主体。原生音乐控件不需要这样接管前景层。

修复：原生媒体可见状态与第三方歌词/封面占用前景层的状态分开。原生控件继续参与媒体恢复判断，但不会触发主体拆除。HyperMusicCover 与歌词前景层继续保留其兼容行为。

同时修正识别：补上日志出现的 `MiuiMediaHeaderView`；不再因普通 `CoverView`/折叠屏封面时钟含有 `cover` 就当作音乐；检查祖先可见性、累计 alpha、窗口可见性与实际 attachment，排除 GONE 父容器中的控件。

## 4. 音乐轮询在 SystemUI 主线程做 IPC，失败和旧状态积压

**源码确定风险，766 次拒绝请求证明失败路径持续发生；没有完整主线程耗时 trace，不能声称它解释了全部卡顿。**

原 watcher 每 800ms 遍历 View 树，并在主线程同步 `ContentResolver.call()`；背景音乐、锁屏状态和壁纸查询也参与识别。背景播放且 LOCK 丢失会被误当成“音乐界面仍显示”，使退出判断可能一直不成立。

修复：

- UI 线程只做有界 View 扫描；配置 IPC 转到独立 HandlerThread。
- 同时最多一个请求在执行，只保留最新 on/off 状态和一个合并的 heal，不排队重放过期状态。
- Provider 重启/权限异常按 3、6、12、30、60 秒退避，失败日志至少间隔 30 秒。
- 移除“背景播放等于音乐界面”的推断和主线程壁纸查询。
- watcher 启动同步保护，避免入口与兜底并发安装两套轮询。

应用侧恢复任务也合并为一个待执行检查，并即时保存最新媒体状态。特权恢复失败后延长重试间隔。

## 5. 恢复任务查询异常可逃出后台 Runnable

**当前源码确定，现有日志未证明已经造成模块进程崩溃。**

原 `AliveMusicRestore.apply()` 在 try 外查询系统壁纸绑定。WallpaperManager 因服务重启或权限而抛异常时，异常会逃出 HandlerThread Runnable；Alive 两个服务与应用共用进程，后果可能波及正在运行的壁纸。

修复：把设置读取、系统状态查询、决策和恢复全部纳入异常处理；失败保留壁纸状态，限频记录并等待下次检查。新增测试让 WallpaperManager 抛 SecurityException，确认恢复任务不再向外抛异常。

## 6. 空间壁纸被系统图顶掉：先被模块主动关闭

**退出链有直接证据；是哪一个身份字段变化仍待补充记录。**

| 时间 | 事件 |
| --- | --- |
| 01:53:00.592 | 开启空间壁纸，key `7a01c52648de2320`，scope 0（锁屏） |
| 01:53:00.692 | 2 层启动，756×1120，屏幕 1200×2608，启动耗时 96ms |
| 01:54:56.209 | `modules:7943`：编辑器模板配置被判定为壁纸变化，模块申请自动关闭 |
| 01:54:56.650～.669 | `modules:7944～7945`：收到关闭状态，渲染器主动停止 |
| 01:54:56.903 | `full:100088` 等：系统 ImageWallpaper 所在进程退出、壁纸服务断开 |
| 01:55:21.783 | 同 key 再次启动，93ms |
| 01:55:36.700～.719 | 再次主动停止；这次没有同样的自动关闭原因记录，不能断言触发原因相同 |

`SpatialControl.disable()` 在停止得到确认后执行既有的 `killall com.miui.miwallpaper` 重建系统壁纸层；因此第一段日志中的服务退出与默认图出现符合主动关闭链，而不是空间 GL 崩溃。

进一步反编译本地 ROM 的 `TemplateApiImpl`：`writeTemplateInfo` 也用于初始化/更新模板、时钟或 follow 状态，并非每次调用都等于用户换图。原判据拼接 source/imagePath/originResourcePath/sha1/videoName；即便 SHA-1 相同，路径变化仍导致判据改变。文件兜底还把裁切预览和 HOME 文件混入摘要。

本轮修复：

- 有效 40 位 SHA-1 优先作为内容身份，同图复制到新路径不改变判据；保留视频名称以识别视频变化。
- 无有效内容摘要时保留路径判据，不假定所有 ROM 都提供可靠 SHA-1。
- 文件兜底先读一份原图，再退到已应用 LOCK，排除 HOME、裁切预览和重复文件。
- 使用新版本/来源前缀；升级或来源变化只迁移基准，避免因为换了算法而自动关闭。代价是迁移那一次不把不可比较的差异当作换图。
- 模板事件读取新配置时不先执行另一轮缓存 Settings 比较/文件替换。
- 自动关闭请求携带期望基准与观察到的身份；Provider 再核对当前基准和已提交的 Settings，拒绝陈旧事件关闭新选择。
- 空间身份变化日志补充 old/new 指纹和有界身份信息，便于下一次核对具体变化。

不能确认这位用户当时是否只改了时钟：现有日志没有旧/新模板身份。无 SHA-1 的路径迁移仍可能触发差异；已应用 LOCK 文件的裁切变化也仍属于弱兜底的限制。保留用户既定的“真正换壁纸时自动关闭”行为，没有取消自动关闭。

## 7. 指纹和动画延迟的时间证据

当前 full.log 中可配对的 **7 次指纹认证成功 → 系统解锁 CUJ 开始**：41、42、47、39、26、38、36ms，范围 **26～47ms**。例如 `full:78502` 的认证成功在 01:53:20.450，`full:78942` 的解锁开始在 .497。

空间淡出结束日志可反推起点，6 次有对应系统 CUJ 的偏差为 **2、2、6、11、3、2ms**。另一次系统窗口没有对应 CUJ，不纳入延迟结论。已记录的淡出实测为 258～314ms，正常结束由 `fadingAway=false` 收口。

`modules:7643` 的 01:53:20.814 有一帧 **78ms** 的空间绘制慢记录；其他切换补帧出现 0～25ms。这说明有局部渲染等待，不能声称完全无卡顿；失败计数为 0 也不代表每帧流畅。scope 0 只渲染锁屏，因此桌面帧 0 是预期，不能当成桌面渲染丢失。

修复一个确定的时间基准风险：原淡出在 GL 排队和冻结帧绘制后才开始计时，会把等待时间额外加到动画末尾。现在从 Hook 请求时刻计时，诊断中记录 queue 与 freeze 耗时。**没有凭猜测缩短 380ms 常量**。

此外旧 WMShell 诊断探针在动画线程反射多个字段并大量打印。现在只有用户开启详细日志时安装该纯诊断探针，正常状态栏功能不依赖它。此调整减少潜在开销，但缺少 trace，不能量化其实际改善。

待验证：手指触碰到认证成功的耗时、屏幕首次呈现、主体跟随与硬切视觉、偶发 GPU/SF 等待、其他模块影响。现有日志没有足够的显示帧时间或完整 Perfetto trace 将这些阶段全部归因。

## 8. 日志版本标签写死

**确定的诊断 Bug。** `HookEntry.BUILD_TAG` 原先固定为 `ui.18-music1 (33)`，之后构建版本变化也不会更新这条日志。本轮启用 Gradle BuildConfig，Hook 标签自动使用 VERSION_NAME / VERSION_CODE；新增测试确认标签与当前构建一致。

因此用户 B 现场的 ui18 标签只能视为标签本身，不能作为“现场一定是旧 ui18、当前新包没问题”的依据。需要实际安装包信息才能核定现场版本。

## 历史 ANR 与其他模块

11 月 8 日两次 system_server（pid 4868、8903）的 Watchdog 堆栈均有明确锁顺序反转：

1. 电源唤醒线程持有 PowerManager NamedLock，在 `com.qihang.aod.hook.StateCoordinator.sendBroadcastSmart` 同步发广播，等待 AMS 锁。
2. AMS/OomAdjuster 线程持有 AMS/进程锁，进入 `PowerManagerService.startUidChangesInternal`，等待上面的电源锁。

第二次证据在 `anr/traces_SystemServer_WDT08_11_16_13_20.403_pid8903:417～445`（线程 100）和 `:756` 起（线程 386）；第一次有相同调用链。模块数据库中存在独立包 `com.qihang.aod`。这是另一模块的历史死锁，可能解释此前严重唤醒卡死，但不能据此证明 11 月 15 日视觉延迟也是同一原因。本模块电源 Hook 的 Provider 调用已在独立 worker 中，未在持有电源锁的调用线程同步做 IPC。

11 月 9 日 SystemUI 输入 ANR 的主线程在控制中心插件初始化和 LSPosed class-loading/反射异常链中，没有本模块具名栈帧。多层框架包装不足以可靠定位具体模块，不作强行归因。

18 份 tombstone 主要为较早的 B 站子进程 ART/Gson 崩溃、调试 app_process ClassNotFound，以及 guardprovider/HyperCeiler DexKit native 崩溃；另有旧 LSPosed 管理器 ResourcesNotFound。未发现可据以确认本轮 Alive/空间渲染器原生崩溃的记录。miwallpaper 的部分 vendor 属性读取被 SELinux 拒绝属于系统进程路径；没有因此放宽 SELinux。

## 用户 A 的绑定丢失与日志缺口

用户 A 的两个绑定快照证明：HOME 始终是本模块，LOCK 在 20:27:19 与 20:27:40 均为 `MiuiKeyguardPictorialWallpaper`，丢失是持续状态。`WPROBE.hideKeyguardWallpaper` 只观察系统调用，当前实现完整调用 `chain.proceed()`，没有主动替换组件；不能把这条观察日志解释成“模块调用它关掉自己”。

用户目录没有草稿提到的 `run3_hx.log` 原始窗口，也没有引发绑定更换的调用栈。本轮新增的 LOCK 恢复覆盖这个绑定结果，但最初由哪个系统/媒体/模块分支替换仍未确定。该复现的媒体来自抖音，不能当成网易云实测；早前网易云在切换 Alive 时退出/重启也缺少对应 ApplicationExitInfo/完整退出窗口，继续保留为待实测问题。

## 本地验证与交付

完整应用回归已通过 **302 项测试，0 失败、0 错误、0 跳过**。随后追加版本标签与 Provider 陈旧关闭请求的测试，并复测媒体队列/可见性，共 **6 项最终专项测试通过**；外置资源脚本 **5 项测试通过**。最终包验证结果、签名和资源校验记录在 `build/bug-review-20261115/release-verification.json` 与构建日志中。

本轮验证包括配置调用者、音乐保持/恢复策略、最新状态合并与失败退避、隐藏音乐控件、模板身份和文件兜底、自动关闭基准校验、恢复查询异常，以及既有 Alive/Infinity/液态/AOD/UI 回归。中间新增测试的 fixture/访问权限问题已修正，以上计数只引用通过后的完整回归结果。

Release 构建与 `lintVitalRelease` 通过。另行运行完整 `lintRelease`，结果为 **4 个错误、316 个警告、17 个提示**，因此完整 Lint 未通过。对比之前 5 个错误，本轮消除了 MusicWatch 无 API 34 闸门调用独立锁屏查询的问题，未新增 Lint 错误。剩余四项是：未调用的 DesktopWallpaperInjector 旧备份代码读取壁纸权限、本地 local.properties 转义、ParallaxBg 和 ScreenFlash 两处带标志注册失败后的旧两参广播注册兜底。后两处正常路径已使用 RECEIVER_EXPORTED，现有日志没有证明兜底导致本轮故障。本轮没有新增 baseline 或关闭检查来掩盖这些问题；原始结果在 `lint-current-errors.json` 与 `full-lint.log`。

历史候选 APK 位于 `deliverables/HyperExtreme-Alive-0.5-alive.7-ui.25-bugfix.apk`，已撤回，仅保留取证；不要安装。当前候选为同目录 ui26。Alive 48 款动态资源仍外置，Infinity 20 款仍内置；已导入资源可沿用，覆盖安装无需重新导入。此前资源包仍为 `HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip`。

APK：169,645,154 字节；SHA-256 `ce532d946b3bb91fa2391663dadd64c01d4e6a7417b0644f5fca9dc1a9b939e8`。V2 签名验证通过，签名证书与前版相同（`52395a7301d94176099f0a1c610406bf0291abad9a2198858f32bc9b9f46942d`）；ZIP CRC、16KB ZIP 对齐与所有打包 JNI 的 ELF LOAD 16KB 对齐均通过。对比 ui23 的 assets/lib，574 项字节保持一致，仅更新特权 helper 并新增 AOD JNI；48 个外置文件没有重新内置，两个 gzip runtime 的原始摘要验证通过。资源包 SHA-256 仍为 `a8a30f9c7476d2c8d9f7bfcf36848f76e952e63da3f1d9b060f76464ad6fb4aa`。本轮 R8 mapping 已保存在同目录的 ui25 mapping ZIP，供后续日志还原。

## 明天实机验证顺序

1. 覆盖安装 ui26 后重启，确认退出 LSPosed 安全模式，并让 SystemUI/AOD/system 加载同一新版本；先确认日志自动显示 ui26 / vc41。
2. 使用**网易云音乐、HyperOS 原生音乐控件**，分别播放、暂停、关闭应用。Alive 和 Infinity 各检查桌面→息屏收起、稳定息屏形态、亮屏展开、指纹直接解锁；记录 HOME/LOCK 绑定是否变化。
3. 空间壁纸开启后，只进入/退出编辑器、修改时钟、调整裁切、重复应用同图，确认保持开启；真正换图应自动关闭。若仍误关，读取新增 old/new 身份日志。
4. 光栅壁纸在原生音乐控件可见/隐藏之间检查桌面图与主体，确认不再因控件模式拆除主体。
5. 对比详细日志关闭时的动画；若还有延迟，录屏并抓认证成功、CUJ、queue/freeze/绘制慢记录，必要时再抓 Perfetto。另一个 AOD 模块可单独停用做对照，但本轮没有改变其配置。
6. AOD 普通开关与强制支持各回读实际设置；ui26 已删除 JNI 写入路径，新字段访问方式在实际 SystemUI 构造中的效果及真正不支持设备上的显示仍需对应实机确认。

这些是待实测项目，不表示本地单元测试已经证明真实系统的全部视觉问题消失。
