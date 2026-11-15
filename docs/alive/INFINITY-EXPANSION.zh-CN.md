# Infinity 样式扩展

测试版本：`0.5-alive.7-ui.23-infinity2`，versionCode 38。

从用户提供的 `Downloads/Infinity` 两份 APK 提取图像、GLSL、图集坐标、OBJ 网格、配色及场景配置，接入已有 Infinity 独立入口。共 20 个选项，保留 S8 的 801–806 身份与资产。

| ID | 来源 | 样式 |
|---|---|---|
| 807–810 | N8 Infinity Wallpaper 1.2 | 黑、蓝、金、兰紫；11 / 9 / 9 / 5 个分层光影 |
| 811 / 812 | Infinity Wallpaper 1.0 | 黑色星空 / 渐变 |
| 813 / 814 | Infinity Wallpaper 1.0 | 蓝色星空 / 渐变 |
| 815 / 816 | Infinity Wallpaper 1.0 | 金色星空 / 渐变 |
| 817 / 818 | Infinity Wallpaper 1.0 | 兰紫星空 / 渐变 |
| 819 / 820 | Infinity Wallpaper 1.0 | 紫色星空 / 渐变 |

Infinity 1.0 原始的 `_1` 服务使用锁屏 mode 1（星空）、桌面 mode 2（多面体）；`_2` 使用锁屏 mode 2、桌面 mode 3（渐变）。项目将两组配置都保留，实际息屏统一使用 mode 1。黑色引用 `vertex_data_grey.obj`、兰紫引用 `vertex_data_purple.obj`、紫色引用 `vertex_data_black.obj`，映射来自各自 Resource 类。

N8 使用原图集、旋转图块坐标、独立颜色遮罩及补光层，不把它转换为单张静态背景。几何样式保留原始多面体颜色、星点和边线着色器。动态转场和传感器通过项目的 EGL / MotionSession 驱动，原 APK 的 GLSurfaceView、WakeLock、主线程 Handler 和 WallpaperService 不进入项目。

几何转场保留原始模式、缩放目标、旋转目标、星点分组显现及主要时长；N8 的分层显现、移动和传感器响应适配项目现有的事件时间动画。尚未验证与原 APK 在小米 15 上的逐帧一致性，也未验证 HyperOS 4 Rust 桌面能否发送滑动事件。

纹理采用完整尺寸无损 WebP，导出时逐像素比较 RGBA。五种几何配色的两组配置共享网格和背景，新增资源约 40 MiB。原始 PNG、OBJ、GLSL、JADX 和 Smali 保留在 `build/infinity-expansion-20261115/`。原 S8 资产以及 Alive 外置动态资源包保持一致。时钟与通知扩展保持删除状态。

Infinity 的样式库、封面、预览编辑器复用现有 Miuix 页面；预设仍归家族 800。范围判断统一使用 `InfinityTheme.supports`，包括应用、导入预设、伴随渲染槽位、触摸、传感器与停绘策略。

反编译使用 JADX 1.5.6 和 baksmali 2.5.2。JADX 对部分库代码及 N8 `onDrawFrame` 的 Java 输出不完整，原始 Smali 已完整提取。Infinity 1.0 的 `parseData` 在写入三角形索引前分配点分组，导致全部分组只写到顶点 0；已用 Smali 核对并保留其实际显示数据，不使用其中超出实际面数的旧分组索引。

验证记录见 `build/infinity-expansion-20261115/validation.json`。桌面 OpenGL 成功编译 8 组相关着色器并渲染 42 张新增样式画面；这不是实机截图。回归包含 Infinity、预设、资源包、家族隔离、Vivo、时钟 / 通知移除及伴随槽位策略。Release 使用现有签名并检查 16 KB ZIP 对齐。

完整 `lintRelease` 扫描仍报告 5 个现有错误：DesktopWallpaperInjector 缺少受限读取壁纸权限、MusicWatch 的 API 34 调用、local.properties 路径转义，以及 ParallaxBg / ScreenFlash 的动态广播标志。本次保留这些文件；Release 构建的 Lint Vital 检查单独执行。

资源来源及哈希见 `INFINITY-EXPANSION-PROVENANCE.json`；导出与桌面验证脚本为 `tools/extract_infinity_expansion.py`、`tools/render_infinity_expansion.py`。
