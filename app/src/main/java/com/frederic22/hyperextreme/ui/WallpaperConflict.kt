package com.frederic22.hyperextreme.ui

import android.content.Context
import android.widget.Toast
import com.frederic22.hyperextreme.alive.AliveSettings
import com.frederic22.hyperextreme.data.MaskPrefs
import com.frederic22.hyperextreme.data.RasterControl
import com.frederic22.hyperextreme.data.SpatialControl
import com.frederic22.hyperextreme.data.WallpaperFeatures
import com.frederic22.hyperextreme.data.WallpaperFeaturePolicy.Feature
import com.frederic22.hyperextreme.wallpaper.RasterServiceTarget
import com.frederic22.hyperextreme.wallpaper.RasterWallpaperService
import com.frederic22.hyperextreme.wallpaper.SpatialServiceMode
import com.frederic22.hyperextreme.wallpaper.SpatialServiceTarget
import com.frederic22.hyperextreme.wallpaper.SpatialWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Alive、视差、空间和光栅壁纸的切换检查。
 *
 * 四者**互斥**：都靠"盖住系统壁纸"工作，同时开就是互相遮挡 / 双份渲染。
 */
internal enum class WallpaperChain {
    ALIVE,
    PARALLAX,
    SPATIAL,
    RASTER,
}

/** 同一条链的两种挂载方式（只有空间 / 光栅有；视差只有 hook 一条）。 */
internal enum class WallpaperMode {
    HOOK,
    SERVICE,
}

/** 正在运行的那条链 + 它的挂载方式。 */
internal data class RunningWallpaper(
    val chain: WallpaperChain,
    val mode: WallpaperMode,
) {
    /** 弹窗文案里用的名字（用户 2026-11-04：正在运行的名字要对）。 */
    val label: String
        get() = when (chain) {
            WallpaperChain.ALIVE -> "Alive 壁纸"
            WallpaperChain.PARALLAX -> "视差壁纸"
            WallpaperChain.SPATIAL -> "空间壁纸"
            WallpaperChain.RASTER -> "光栅壁纸"
        }
}

/**
 * 壁纸链互斥检测（2026-11-04）。
 *
 * ## 为什么需要
 *
 * 视差、空间、光栅都通过"压住系统壁纸"显示，同时开就会互相盖 / 双份渲染。
 * 之前只有**视差页检测空间 LSPosed** 这一条（2026-10-29），另外四个方向全是空的：
 * * 空间页不知道光栅；
 * * 光栅页什么都不知道；
 * * 三条链都各自新增了「系统壁纸服务」模式，也都没互相检测。
 *
 * ## 判据（都是"真的在跑"，不是"开关开着"）
 *
 * | 链 | HOOK | SERVICE |
 * |---|---|---|
 * | 视差 | `state.parallaxActive` | —— |
 * | 空间 | `prefs.spatialActive` | `WallpaperManager` 的壁纸 component 是它 |
 * | 光栅 | `rasterActiveId` 非空且 `mountMode == LSPOSED` | 同上 |
 */
internal object WallpaperConflict {

    suspend fun clearCaches(ctx: Context, chain: WallpaperChain, remove: () -> Int): Int? = try {
        if (!stopForRemoval(ctx, chain)) null else withContext(Dispatchers.IO) {
            val feature = if (chain == WallpaperChain.SPATIAL) Feature.SPATIAL else Feature.RASTER
            WallpaperFeatures.removeInactiveCaches(ctx, feature) {
                if (chain == WallpaperChain.SPATIAL) SpatialServiceTarget.clear(ctx) else RasterServiceTarget.clear(ctx)
                remove()
            }
        }
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        Toast.makeText(ctx, failure.message ?: "清除失败，图片已保留", Toast.LENGTH_LONG).show()
        null
    }

    /** Destructive cache operations must wait for both service and hook renderers to stop. */
    private suspend fun stopForRemoval(ctx: Context, chain: WallpaperChain): Boolean = try {
        require(chain == WallpaperChain.SPATIAL || chain == WallpaperChain.RASTER)
        val spatial = chain == WallpaperChain.SPATIAL
        val prefs = MaskPrefs(ctx)
        withContext(Dispatchers.IO) {
            WallpaperFeatures.clearOwned(ctx, if (spatial) Feature.SPATIAL else Feature.RASTER)
        }
        if (spatial) SpatialControl.disable(ctx, null)
        else if (prefs.rasterMountMode == MaskPrefs.RASTER_MOUNT_LSPOSED || prefs.rasterStopPending) RasterControl.disable(ctx, null)
        var attempts = 0
        while ((if (spatial) prefs.spatialStopPending else prefs.rasterStopPending) && attempts++ < 50) delay(100)
        check(!(if (spatial) prefs.spatialStopPending else prefs.rasterStopPending)) { "未确认关闭，已保留图片，请重试" }
        true
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        Toast.makeText(ctx, failure.message ?: "未确认关闭，已保留图片", Toast.LENGTH_LONG).show()
        false
    }

    /**
     * 除 [self] 之外正在运行的链；没有返回 null。
     *
     * 同一 chain 的两种模式一起排除 —— 页面自己不需要被自己拦住。
     */
    fun running(ctx: Context, state: MaskUiState, self: WallpaperChain): RunningWallpaper? {
        val prefs = MaskPrefs(ctx)
        if (self != WallpaperChain.ALIVE && AliveSettings.isRunning(ctx)) {
            return RunningWallpaper(WallpaperChain.ALIVE, WallpaperMode.SERVICE)
        }
        if (self != WallpaperChain.PARALLAX && state.parallaxActive) {
            return RunningWallpaper(WallpaperChain.PARALLAX, WallpaperMode.HOOK)
        }
        if (self != WallpaperChain.SPATIAL) {
            if (prefs.spatialActive || prefs.spatialStopPending) {
                return RunningWallpaper(WallpaperChain.SPATIAL, WallpaperMode.HOOK)
            }
            if (SpatialServiceMode.isRunning(ctx, SpatialWallpaperService::class.java)) {
                return RunningWallpaper(WallpaperChain.SPATIAL, WallpaperMode.SERVICE)
            }
        }
        if (self != WallpaperChain.RASTER) {
            if (prefs.rasterStopPending || (prefs.rasterMountMode == MaskPrefs.RASTER_MOUNT_LSPOSED &&
                prefs.rasterActiveId.isNotEmpty())
            ) {
                return RunningWallpaper(WallpaperChain.RASTER, WallpaperMode.HOOK)
            }
            if (SpatialServiceMode.isRunning(ctx, RasterWallpaperService::class.java)) {
                return RunningWallpaper(WallpaperChain.RASTER, WallpaperMode.SERVICE)
            }
        }
        return null
    }

    /**
     * 关闭指定那条链（幂等）。
     *
     * **suspend**：服务模式走 `WallpaperManager.clear()`，同步 binder 要几百毫秒，
     * 必须放 IO 线程（空间页 2026-10-31 踩过"点一下卡一下"）。
     */
    suspend fun disable(ctx: Context, state: MaskUiState, target: RunningWallpaper) {
        try {
            when (target.chain) {
                WallpaperChain.ALIVE -> withContext(Dispatchers.IO) { AliveSettings.disable(ctx) }
                WallpaperChain.PARALLAX -> state.disableParallax()
                WallpaperChain.SPATIAL -> if (target.mode == WallpaperMode.SERVICE) {
                    withContext(Dispatchers.IO) {
                        SpatialServiceMode.disable(ctx) { SpatialServiceTarget.clear(it) }.getOrThrow()
                    }
                } else {
                    SpatialControl.disable(ctx)
                }
                WallpaperChain.RASTER -> if (target.mode == WallpaperMode.SERVICE) {
                    withContext(Dispatchers.IO) {
                        SpatialServiceMode.disable(ctx, RasterWallpaperService::class.java) { RasterServiceTarget.clear(it) }.getOrThrow()
                    }
                } else {
                    // 冲突关闭保持**静默**（用户点的是"关闭并应用"，紧接着会有自己的 toast）。
                    RasterControl.disable(ctx, null)
                }
            }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            Toast.makeText(ctx, failure.message ?: "未确认关闭，请重试", Toast.LENGTH_LONG).show()
        }
    }

    /** 弹窗正文：「当前**XX**已生效，应用**YY**需先关闭」。 */
    fun summary(other: RunningWallpaper, self: WallpaperChain): String {
        val selfLabel = when (self) {
            WallpaperChain.ALIVE -> "Alive 壁纸"
            WallpaperChain.PARALLAX -> "视差壁纸"
            WallpaperChain.SPATIAL -> "空间壁纸"
            WallpaperChain.RASTER -> "光栅壁纸"
        }
        return "当前${other.label}已生效，应用${selfLabel}需先关闭"
    }
}
