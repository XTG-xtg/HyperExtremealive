package com.frederic22.hyperextreme.alive

import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/** Live time/date and observed clock geometry; never a screenshot of private lockscreen content. */
@Composable
internal fun AliveSceneOverlay(mode: Int, modifier: Modifier = Modifier, variant: Int = 1) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000L) }
    }
    val locale = LocalConfiguration.current.locales[0]
    val time = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", now).toString()
    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale).format(Date(now))
    val snapshot = EngineRegistry.clock(mode == 0)
    Canvas(modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val w = size.width; val h = size.height
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            if (mode != 0) setShadowLayer(w * .008f, 0f, w * .003f, 0x80000000.toInt())
        }
        if (mode != 2) {
            val bounds = snapshot?.bounds?.takeIf { it.fits(w.toInt(), h.toInt()) }
            val cx = if (bounds != null) (bounds.left + bounds.right) * .5f / bounds.width * w else w * .5f
            val top = if (bounds != null) bounds.top.toFloat() / bounds.height * h
                else h * if(mode == 0 && variant in 601..606) .43f else .14f
            paint.textSize = w * if (mode == 0) .16f else .2f
            val baseline = top - paint.fontMetrics.ascent
            canvas.drawText(time, cx, baseline, paint)
            paint.textSize = w * .034f
            canvas.drawText(date, cx, baseline + w * .055f, paint)
            if (mode == 1) {
                paint.clearShadowLayer(); paint.alpha = 155; paint.style = Paint.Style.STROKE; paint.strokeWidth = w * .003f
                canvas.drawRoundRect(w * .477f, h * .88f, w * .523f, h * .88f + w * .07f, w * .018f, w * .018f, paint)
                canvas.drawLine(w * .5f, h * .88f + w * .018f, w * .5f, h * .88f + w * .052f, paint)
            }
        }
        if (mode != 0) {
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL; paint.alpha = 190
            canvas.drawRoundRect(w * .37f, h * .973f, w * .63f, h * .973f + w * .009f, w * .005f, w * .005f, paint)
        }
    }
}
