// Adapted from OpenAlive VivoSubjectMask; MIT. Model provenance: assets/vivo/models/source.json.
package com.frederic22.hyperextreme.alive

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal class VivoSubjectMask {
    private var cancelled=false
    private var running: OrtSession.RunOptions?=null
    @Synchronized fun cancel() { cancelled=true;runCatching { running?.setTerminate(true) } }
    @Synchronized private fun check() { if(cancelled) throw CancellationException() }
    @Synchronized private fun begin(run: OrtSession.RunOptions) { check();running=run }
    @Synchronized private fun end() { running=null }

    fun generate(context: Context, source: Bitmap, selection: RectF): Bitmap = synchronized(inference) {
        check();val model=model(context);check()
        val l=(selection.left*source.width).toInt().coerceIn(0,source.width-1)
        val t=(selection.top*source.height).toInt().coerceIn(0,source.height-1)
        val r=(selection.right*source.width).toInt().coerceIn(l+1,source.width)
        val b=(selection.bottom*source.height).toInt().coerceIn(t+1,source.height)
        val cropped=Bitmap.createBitmap(source,l,t,r-l,b-t)
        val scaled=Bitmap.createScaledBitmap(cropped,320,320,true)
        val pixels=IntArray(320*320)
        try { scaled.getPixels(pixels,0,320,0,0,320,320) }
        finally { if(scaled!==cropped) scaled.recycle();if(cropped!==source) cropped.recycle() }
        val input=ByteBuffer.allocateDirect(3*320*320*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val maximum=pixels.maxOf { maxOf(Color.red(it),Color.green(it),Color.blue(it),1) }.toFloat()
        val mean=floatArrayOf(.485f,.456f,.406f);val std=floatArrayOf(.229f,.224f,.225f)
        for(channel in 0..2) for(pixel in pixels) input.put((((pixel shr (16-8*channel)) and 255)/maximum-mean[channel])/std[channel])
        input.rewind()
        val environment=OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { settings ->
            settings.setIntraOpNumThreads(2);settings.setInterOpNumThreads(1)
            settings.setCPUArenaAllocator(false);settings.setMemoryPatternOptimization(false)
            settings.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            check()
            environment.createSession(model.path,settings).use { session ->
                OnnxTensor.createTensor(environment,input,longArrayOf(1,3,320,320)).use { tensor ->
                    OrtSession.RunOptions().use { run ->
                        begin(run)
                        try {
                            session.run(mapOf(session.inputNames.first() to tensor),setOf(session.outputNames.first()),run).use { result ->
                                check()
                                val scores=FloatArray(320*320);(result[0] as OnnxTensor).floatBuffer.get(scores)
                                val lo=scores.minOrNull()?:0f;val hi=scores.maxOrNull()?:1f
                                for(i in scores.indices) {
                                    val v=(((scores[i]-lo)/maxOf(1e-6f,hi-lo)).coerceIn(0f,1f)*255).toInt()
                                    pixels[i]=Color.rgb(v,v,v)
                                }
                                val small=Bitmap.createBitmap(pixels,320,320,Bitmap.Config.ARGB_8888)
                                val mask=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
                                mask.eraseColor(Color.BLACK)
                                try { Canvas(mask).drawBitmap(small,null,Rect(l,t,r,b),Paint(Paint.FILTER_BITMAP_FLAG)) }
                                finally { small.recycle() }
                                mask
                            }
                        } finally { end() }
                    }
                }
            }
        }
    }
    companion object {
        private val inference=Any()
        private const val HASH="309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"
        private fun hash(file: File): String {
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream -> val bytes=ByteArray(65536);while(true) { val n=stream.read(bytes);if(n<0) break;digest.update(bytes,0,n) } }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        private fun model(context: Context): File {
            val ready=File(context.codeCacheDir,"u2netp-$HASH.onnx")
            if(ready.isFile && hash(ready)==HASH) return ready
            val temp=File(context.codeCacheDir,"u2netp-import.tmp")
            try {
                context.assets.open("vivo/models/u2netp.onnx").use { input -> FileOutputStream(temp).use { out -> input.copyTo(out);out.fd.sync() } }
                check(hash(temp)==HASH) { "主体模型校验失败" }
                Files.move(temp.toPath(),ready.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temp.toPath()) }
            return ready
        }
    }
}
