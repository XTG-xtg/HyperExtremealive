package com.frederic22.hyperextreme.hook

import android.view.View
import android.view.ViewGroup

internal object MusicViewScan {
    data class Result(val visible: Boolean, val foregroundCover: Boolean, val names: List<String>)
    fun scan(roots: List<View>): Result {
        var visible = false
        var foregroundCover = false
        var visited = 0
        val names = ArrayList<String>(8)
        fun walk(view: View, depth: Int, parentAlpha: Float) {
            if (depth > 16 || visited++ >= 1024 || view.visibility != View.VISIBLE) return
            val alpha = parentAlpha * view.alpha
            if (alpha <= .05f) return
            if (MusicScreenNames.matches(view.javaClass.name)) {
                visible = true
                if (MusicScreenNames.ownsForeground(view.javaClass.name)) foregroundCover = true
                if (names.size < 12) names.add(view.javaClass.simpleName)
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), depth + 1, alpha)
        }
        for (root in roots) {
            if (root.isAttachedToWindow && root.windowVisibility == View.VISIBLE && root.isShown)
                walk(root, 0, 1f)
        }
        return Result(visible, foregroundCover, names)
    }
}
