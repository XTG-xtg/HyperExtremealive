package com.frederic22.hyperextreme.data

/** Recheck the baseline and committed template before a cross-process automatic stop. */
internal object WallpaperChangeGuard {
    fun accepts(active: Boolean, base: String, expected: String?, observed: String?, current: String): Boolean =
        active && base.isNotEmpty() && base != MaskPrefs.FINGERPRINT_WEAK && base == expected &&
            !observed.isNullOrEmpty() && observed != base && observed == current &&
            WallpaperVersion.kindOf(base).isNotEmpty() &&
            WallpaperVersion.kindOf(base) == WallpaperVersion.kindOf(observed)
}
