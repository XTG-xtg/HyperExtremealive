package com.frederic22.hyperextreme.alive

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Bundle
import android.widget.TextView
import java.io.File
import kotlin.concurrent.thread

/** Debug-only, app-UID AI acceptance probe. Uses bundled art, never user images or preferences. */
class VivoDeviceProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { textSize = 18f; setPadding(40, 100, 40, 40); text = "Testing on-device subject recognition…" }
        setContentView(text)
        val context = applicationContext
        thread(name = "vivo-ai-acceptance") {
            val output = File(cacheDir, "vivo-device-ai").apply { mkdirs() }
            val report = File(output, "result.txt")
            report.writeText("APP_UID=${android.os.Process.myUid()}\n")
            val photo = assets.open("vivo/preview/liquid.jpg").use(BitmapFactory::decodeStream)
            val input = File(output, "input.png")
            input.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
            try {
                for (engine in listOf(1, 2, 0)) {
                    val start = android.os.SystemClock.elapsedRealtime()
                    try {
                        val result = VivoSubjectRecognition().generate(context, photo, RectF(0f, 0f, 1f, 1f), engine, input) {
                            message -> runOnUiThread { text.text = message }
                        }
                        try {
                            File(output, "mask-$engine.png").outputStream().use { result.mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            report.appendText("ENGINE=$engine PASS SOURCE=${result.source} SIZE=${result.mask.width}x${result.mask.height} MS=${android.os.SystemClock.elapsedRealtime() - start} DETAIL=${result.detail}\n")
                        } finally { result.mask.recycle() }
                    } catch (e: Exception) { report.appendText("ENGINE=$engine FAIL ${e.javaClass.simpleName}: ${e.message}\n") }
                }
            } finally {
                photo.recycle(); report.appendText("DONE\n")
                runOnUiThread { text.text = report.readText() }
            }
        }
    }
}
