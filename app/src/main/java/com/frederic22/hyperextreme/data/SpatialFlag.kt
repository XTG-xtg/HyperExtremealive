package com.frederic22.hyperextreme.data

/**
 * 空间壁纸开关的**跨进程传递**（2026-10-23 建立）。
 *
 * ## 为什么不用 prefs
 *
 * 实测（2026-10-23，SystemUI 进程日志）：
 *
 * ```
 * 景深开关通道：getRemotePreferences=ok；可读文件=[]
 * SPATIAL 轮询 tick=1 active=null key=null
 * ```
 *
 * * `getRemotePreferences` 拿到的句柄是**陈旧快照** —— 明明 prefs 里
 *   `depth_occlusion_removed=true`，读出来却是 `false`（同一条日志里写着「当前=false」）；
 * * `openRemoteFile` 在 SystemUI 里**什么都读不到**（`可读文件=[]`），文件兜底那条路是死的。
 *
 * ⇒ SystemUI 里「读模块 prefs」不可用。改用**系统属性**：所有进程都能读、不需要
 * Context、不依赖框架通道 —— 与 [ParallaxCoverFlag] 同一套路，那条已被真机验证过。
 *
 * ## 写入时机（缺一不可）
 *
 * 1. App 点「立即替换 / 关闭替换」时（[com.frederic22.hyperextreme.ui.SpatialWallpaperPage]）；
 * 2. App 每次进程启动时按 prefs 重新同步一次（属性用 `debug.` 前缀，**不持久化**，
 *    重启手机后会丢）—— 见 `App.kt`。
 */
object SpatialFlag {
    private val writes = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "SpatialFlags").apply { isDaemon = true }
    }

    /** 1 = 生效，0 = 关闭。 */
    const val PROP_ACTIVE = "debug.hyperextreme.spatial_active"

    /** 图层缓存键（16 位 hex）；关闭时置空。 */
    const val PROP_KEY = "debug.hyperextreme.spatial_key"

    /**
     * 生效范围（`MaskPrefs.SPATIAL_SCOPE_*`）：0 锁屏 / 1 桌面 / 2 全局。
     *
     * 单独一条属性的原因：SystemUI 侧轮询要把「active / key / scope」三者**原子地**读一遍，
     * 若 scope 混在 prefs 里，就会出现"active 已刷新、scope 还是旧的"的半新半旧状态。
     */
    const val PROP_SCOPE = "debug.hyperextreme.spatial_scope"

    /**
     * 「**启用空间壁纸锁屏景深**」：`1` = 开，`0` = 关（2026-10-29）。
     *
     * 与 [PROP_SCOPE] 放同一批推送，理由相同：SystemUI 启动渲染器时要把
     * 「active / key / scope / lockdepth」读成**一致的一组**，任何一项晚半拍都会让装配
     * 用上新旧混合的参数（比如开关刚开、scope 还是旧的 ⇒ 桌面档也去建主体层）。
     *
     * **语义与 scope 完全一致**：生效中改不动渲染，下一次替换才随 push 一起下发。
     */
    const val PROP_LOCKDEPTH = "debug.hyperextreme.spatial_lockdepth"

    /**
     * 锁屏是否在前台（`1` = 是，`0` / 空 = 桌面或未知）。
     *
     * 由 `SpatialUnlockFade` 在 `notifyKeyguardState` 里写，`SpatialWallpaperAlpha` 读。
     * **用途**：miwallpaper 只在锁屏前台时才把系统壁纸的 alpha 压 0 —— 桌面态压 0
     * 一旦我们的图画慢了就是一片黑，而锁屏态压 0 是消重影的必要手段。
     */
    const val PROP_KEYGUARD = "debug.hyperextreme.spatial_keyguard"

    /**
     * **渲染器是否真的在跑**（1 = SystemUI 里的 GL 层已挂上并出过帧）。
     *
     * 与 [PROP_ACTIVE] 的区别：`ACTIVE` 只是"用户在界面上开了开关"，启动可能失败
     * （层图读不到、EGL 起不来、Context 拿不到）。
     *
     * **用途（2026-10-28）**：miwallpaper 侧要「淡出期间把系统壁纸 alpha 压 0」，
     * 这是**唯一**能安全压的前提 —— 我们的图没在显示时压 0 就是锁屏黑屏。
     * 所以这个属性是那条改动的硬前置。
     */
    const val PROP_RENDERING = "debug.hyperextreme.spatial_rendering"

    /** Serial writes prevent a slow enable request from arriving after a later disable. */
    fun push(active: Boolean, key: String?, scope: Int, lockDepth: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        require(key.isNullOrEmpty() || key.matches(Regex("[0-9a-fA-F]{16}")))
        val safeScope = scope.coerceIn(0, 2)
        val safeKey = key.orEmpty()
        val waitForRenderer = !active && onComplete != null
        val script = script(active, safeKey, safeScope, lockDepth, waitForRenderer)
        val expected = "SPATIAL_FLAG:${if (active) 1 else 0}:$safeKey:$safeScope:${if (lockDepth) 1 else 0}"
        writes.execute {
            val result = runCatching { RootShell.run(script) }.getOrNull()
            val verified = result?.ok == true && result.output.lineSequence().any { it.trim() == expected }
            if (!verified) {
                android.util.Log.w("HyperExtreme", "SpatialFlag: setprop 失败（${result?.output?.take(160) ?: "异常"}）")
            }
            runCatching { onComplete?.invoke(verified) }
        }
    }

    /**
     * One `&&` chain. A failed setprop must not reach the read-back echo, and the renderer wait
     * must not be split off by a semicolon.
     */
    internal fun script(active: Boolean, key: String, scope: Int, lockDepth: Boolean, waitForRenderer: Boolean): String {
        val activeBit = if (active) 1 else 0
        val depthBit = if (lockDepth) 1 else 0
        val wait = if (!waitForRenderer) "" else
            "i=0 && while [ \"$(getprop $PROP_RENDERING)\" = 1 ] && [ \"${'$'}i\" -lt 30 ]; do sleep 0.1; i=\$((i+1)); done && " +
                "[ \"$(getprop $PROP_RENDERING)\" != 1 ] && "
        return "setprop $PROP_ACTIVE 0 && " +
            "setprop $PROP_KEY '$key' && " +
            "setprop $PROP_SCOPE $scope && " +
            "setprop $PROP_LOCKDEPTH $depthBit && " +
            "setprop $PROP_ACTIVE $activeBit && " +
            wait +
            "echo SPATIAL_FLAG:\$(getprop $PROP_ACTIVE):\$(getprop $PROP_KEY):\$(getprop $PROP_SCOPE):\$(getprop $PROP_LOCKDEPTH)"
    }
}
