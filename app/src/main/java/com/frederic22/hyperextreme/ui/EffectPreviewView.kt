package com.frederic22.hyperextreme.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.*
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.MotionEvent
import com.frederic22.hyperextreme.alive.AodBrightness
import com.frederic22.hyperextreme.data.MaskPrefs
import com.frederic22.hyperextreme.data.MaskStore
import com.frederic22.hyperextreme.data.RasterStore
import com.frederic22.hyperextreme.hook.RasterGlCore
import com.frederic22.hyperextreme.hook.SpatialGlCore
import com.frederic22.hyperextreme.spatial.SpatialCrop
import com.frederic22.hyperextreme.spatial.SpatialDepth
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import org.json.JSONObject

internal data class EffectPreviewSource(
    val request: EffectPreviewRequest, val images: List<File>, val depth: File? = null,
    val crop: SpatialCrop.Crop = SpatialCrop.DEFAULT, val bodyK: Float = .35f,
    val parallaxScale: Float = 1f, val parallaxAmp: Float = 0f, val reverse: Boolean = false,
    val rasterAngle: Int = 25,
) {
    companion object {
        fun load(context: Context, request: EffectPreviewRequest): EffectPreviewSource {
            val prefs = MaskPrefs(context)
            val result = when(request.kind) {
                EffectWallpaperKind.SPATIAL -> {
                    val dir = File(SpatialDepth.cacheRoot(context), request.key).resolve("layers")
                    check(dir.isDirectory) { "请先生成深度缓存，再打开预览" }
                    val meta = JSONObject(dir.resolve("meta.json").readText())
                    val count = meta.optInt("layers", SpatialGlCore.MAX_LAYERS).coerceIn(1, SpatialGlCore.MAX_LAYERS)
                    val files = (0 until count).map { dir.resolve("layer$it.png") }.takeWhile { it.isFile }
                    EffectPreviewSource(request, files, dir.resolve("depth.png").takeIf { it.isFile },
                        SpatialCrop.read(context, request.key), meta.optDouble("bodyK", .35).toFloat().coerceIn(0f, 1f))
                }
                EffectWallpaperKind.RASTER -> {
                    val group = RasterStore.load(File(RasterStore.root(context), request.key))
                        ?: error("光栅搭配已不存在，请重新选择")
                    val root = group.dir.canonicalFile
                    val files = group.items.map { group.fileOf(it).canonicalFile }.onEach {
                        check(it.parentFile == root && it.isFile) { "光栅图片缺失，请重新编辑搭配" }
                    }
                    EffectPreviewSource(request, files, rasterAngle = prefs.rasterMaxAngle)
                }
                EffectWallpaperKind.PARALLAX -> EffectPreviewSource(request, listOf(MaskStore.parallaxFile(context)),
                    parallaxScale = prefs.parallaxBgScale, parallaxAmp = prefs.parallaxBgAmp, reverse = prefs.parallaxReverse)
            }
            check(result.images.isNotEmpty()) { "没有可预览的图片" }
            result.images.forEach { file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                check(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片读取失败，请重新选择" }
            }
            return result
        }
    }
}

internal object EffectPreviewMath {
    fun cover(imageW: Int, imageH: Int, viewW: Int, viewH: Int): FloatArray {
        val image = imageW.toFloat() / imageH.coerceAtLeast(1)
        val screen = viewW.toFloat() / viewH.coerceAtLeast(1)
        val x = min(1f, screen / image); val y = min(1f, image / screen)
        return floatArrayOf(x, y, (1-x)*.5f, (1-y)*.5f)
    }
    fun dim(brightness: Float, weight: Float): Float = AodBrightness.wallpaperDim(1-.5f*weight.coerceIn(0f, 1f), .5f, brightness)
    fun index(position: Int, count: Int): Int = Math.floorMod(position, count.coerceAtLeast(1))
}

/** Local preview only: no registry, wallpaper binding, global preference writes or system commands. */
internal class EffectPreviewView(context: Context, private val source: EffectPreviewSource, status: (String?) -> Unit) :
    GLSurfaceView(context), SensorEventListener {
    private val scene = Scene(source) { post { status(it) } }
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var resumed = false
    private var registered = false
    private var mode = 1
    private var lastSensor = 0L
    private var x = 0f; private var y = 0f
    private var downX = 0f; private var downY = 0f; private var startX = 0f; private var startY = 0f
    private var dragging = false
    private var settle: ValueAnimator? = null
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = false
        setRenderer(scene); renderMode = RENDERMODE_WHEN_DIRTY
    }
    fun configure(next: Int, weight: Float, brightness: Float) {
        if(mode != next) { mode = next; dragging = false; animateTo(0f, 0f); syncSensor() }
        scene.frame = scene.frame.copy(weight = weight, brightness = brightness)
        requestRender()
    }
    fun resumePreview() { if(!resumed) { resumed = true; onResume(); syncSensor(); requestRender() } }
    fun pausePreview() {
        settle?.cancel(); dragging = false; resumed = false; syncSensor(); onPause()
    }
    private fun syncSensor() {
        val enabled = resumed && mode != 0
        if(enabled && !registered) registered = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true
        if(!enabled && registered) { sensors?.unregisterListener(this); registered = false }
        lastSensor = 0
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        val previous = lastSensor; lastSensor = event.timestamp
        if(!resumed || mode == 0 || dragging || previous == 0L) return
        val dt = (event.timestamp-previous)/1e9f
        if(dt <= 0 || dt > .25f || abs(event.values[0])+abs(event.values[1]) < .02f) return
        settle?.cancel()
        val fullScale = if(source.request.kind == EffectWallpaperKind.RASTER) RasterGlCore.radOf(source.rasterAngle) else SpatialGlCore.FULL_SCALE_RAD
        move(x + event.values[1]*dt/fullScale, y + event.values[0]*dt/fullScale)
    }
    private fun move(nextX: Float, nextY: Float) {
        val limit = if(source.request.kind == EffectWallpaperKind.RASTER) max(1, source.images.size-1).toFloat() else 1f
        x = nextX.coerceIn(-limit, limit); y = nextY.coerceIn(-1f, 1f)
        scene.frame = scene.frame.copy(x = x, y = y); requestRender()
    }
    private fun animateTo(toX: Float, toY: Float) {
        settle?.cancel(); val fromX = x; val fromY = y
        settle = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            addUpdateListener { val p = it.animatedValue as Float; move(fromX+(toX-fromX)*p, fromY+(toY-fromY)*p) }
            start()
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(mode == 0) return true
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { settle?.cancel(); dragging = true; downX = event.x; downY = event.y; startX = x; startY = y }
            MotionEvent.ACTION_MOVE -> if(dragging) move(startX+(event.x-downX)/width.coerceAtLeast(1)*2, startY-(event.y-downY)/height.coerceAtLeast(1)*2)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                dragging = false
                animateTo(if(source.request.kind == EffectWallpaperKind.RASTER) x.roundToInt().toFloat() else 0f, 0f)
                if(event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    private data class Frame(val weight: Float = 0f, val brightness: Float = 1.3f, val x: Float = 0f, val y: Float = 0f)
    private data class Texture(val id: Int, val width: Int, val height: Int)
    private class Scene(val source: EffectPreviewSource, val status: (String?) -> Unit) : Renderer {
        @Volatile var frame = Frame()
        private var program = 0
        private var width = 1; private var height = 1
        private var failed = false; private var reported = false
        private var depthTexture = 0
        private val textures = linkedMapOf<Int, Texture>()
        private val locations = mutableMapOf<String, Int>()
        private val spatial get() = source.request.kind == EffectWallpaperKind.SPATIAL
        private val quad = ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)); position(0)
        }
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            program = 0; depthTexture = 0; textures.clear(); locations.clear(); failed = false; reported = false
            runCatching {
                val vert = shader(GL_VERTEX_SHADER, if(spatial) SpatialGlCore.VERT else RasterGlCore.VERT)
                val frag = try { shader(GL_FRAGMENT_SHADER, if(spatial) SpatialGlCore.FRAG else RasterGlCore.FRAG) }
                    catch(e: Exception) { glDeleteShader(vert); throw e }
                program = glCreateProgram(); glAttachShader(program, vert); glAttachShader(program, frag); glLinkProgram(program)
                glDeleteShader(vert); glDeleteShader(frag)
                val linked = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, linked, 0)
                check(linked[0] != 0) { glGetProgramInfoLog(program) }
                if(spatial && source.depth != null) depthTexture = upload(source.depth, true).id
            }.onFailure { fail(it) }
        }
        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h; glViewport(0, 0, w, h) }
        override fun onDrawFrame(gl: GL10?) {
            if(failed || program == 0) return
            runCatching {
                glClearColor(0f, 0f, 0f, 1f); glClear(GL_COLOR_BUFFER_BIT); glUseProgram(program)
                val aPos = glGetAttribLocation(program, "aPos"); quad.position(0)
                glEnableVertexAttribArray(aPos); glVertexAttribPointer(aPos, 2, GL_FLOAT, false, 16, quad)
                val aUv = glGetAttribLocation(program, "aUv")
                if(aUv >= 0) { quad.position(2); glEnableVertexAttribArray(aUv); glVertexAttribPointer(aUv, 2, GL_FLOAT, false, 16, quad) }
                val f = frame
                one("uDim", EffectPreviewMath.dim(f.brightness, f.weight)); one("uExposure", AodBrightness.gain(f.brightness, f.weight))
                if(spatial) drawSpatial(f) else drawFlat(f)
                glDisableVertexAttribArray(aPos); if(aUv >= 0) glDisableVertexAttribArray(aUv); glDisable(GL_BLEND)
                check(glGetError() == GL_NO_ERROR) { "预览绘制失败" }
                if(!reported) { reported = true; status(null) }
            }.onFailure { fail(it) }
        }
        private fun drawSpatial(f: Frame) {
            val first = texture(0)
            val g = SpatialGlCore.geometry(width, height, first.width, first.height)
            glUniform2f(at("uK"), g.kx, g.ky); glUniform2f(at("uSpan"), g.spanX, g.spanY)
            glUniform2f(at("uShift"), f.x*(1-f.weight)*SpatialGlCore.PAN_RAD/g.spanX, f.y*(1-f.weight)*SpatialGlCore.PAN_RAD/g.spanY)
            val crop = source.crop; val scale = 1/crop.zoom
            glUniform2f(at("uUvScale"), scale, scale); glUniform2f(at("uUvOffset"), crop.cx-scale*.5f, crop.cy-scale*.5f)
            bind("uDepth", depthTexture, 1); one("uDkTh", SpatialGlCore.DK_TH); one("uUseDepth", if(depthTexture != 0) 1f else 0f)
            glEnable(GL_BLEND); glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            source.images.indices.forEach { i ->
                bind("uTex", texture(i).id, 0); one("uOpaque", if(i == 0) 1f else 0f)
                one("uDepthCh", if(i == 0) 0f else 1f); one("uRigidK", if(i == 0) 1f else source.bodyK)
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            }
        }
        private fun drawFlat(f: Frame) {
            val parallax = source.request.kind == EffectWallpaperKind.PARALLAX
            val position = if(parallax) 0f else f.x*(1-f.weight)
            val start = floor(position).toInt(); val end = start+1
            val from = EffectPreviewMath.index(start, source.images.size); val to = EffectPreviewMath.index(end, source.images.size)
            val a = texture(from); val b = texture(to)
            bind("uFrom", a.id, 0); bind("uTo", b.id, 1)
            for((name, tex) in listOf("uFromUv" to a, "uToUv" to b)) {
                val uv = EffectPreviewMath.cover(tex.width, tex.height, width, height)
                if(parallax) {
                    uv[0] /= source.parallaxScale; uv[1] /= source.parallaxScale
                    val direction = if(source.reverse) 1 else -1
                    uv[2] = (1-uv[0])*.5f-direction*f.x*(1-f.weight)*source.parallaxAmp/width*uv[0]
                    uv[3] = (1-uv[1])*.5f+direction*f.y*(1-f.weight)*source.parallaxAmp/height*uv[1]
                }
                glUniform4fv(at(name), 1, uv, 0)
            }
            one("uT", position-start); one("uDir", 1f); one("uBand", RasterGlCore.BAND)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            // Raster groups can be large: only retain the displayed pair and one neighbor.
            if(textures.size > 3) textures.keys.filter { it != from && it != to }.forEach { key ->
                glDeleteTextures(1, intArrayOf(textures.remove(key)!!.id), 0)
            }
        }
        private fun at(name: String) = locations.getOrPut(name) { glGetUniformLocation(program, name) }
        private fun one(name: String, value: Float) = glUniform1f(at(name), value)
        private fun bind(name: String, texture: Int, unit: Int) { glActiveTexture(GL_TEXTURE0+unit); glBindTexture(GL_TEXTURE_2D, texture); glUniform1i(at(name), unit) }
        private fun texture(index: Int) = textures.getOrPut(index) { upload(source.images[index], false) }
        private fun upload(file: File, depth: Boolean): Texture {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1; while(max(bounds.outWidth, bounds.outHeight)/sample > 2048) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("图片解码失败：${file.name}")
            try {
                val ids = IntArray(1); glGenTextures(1, ids, 0); glBindTexture(GL_TEXTURE_2D, ids[0])
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                if(depth) {
                    val pixels = IntArray(bitmap.width*bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val bytes = ByteBuffer.allocateDirect(pixels.size*2)
                    pixels.forEach { bytes.put((it shr 16).toByte()); bytes.put((it shr 8).toByte()) }; bytes.position(0)
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE_ALPHA, bitmap.width, bitmap.height, 0, GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, bytes)
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
                } else GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
                return Texture(ids[0], bitmap.width, bitmap.height)
            } finally { bitmap.recycle() }
        }
        private fun shader(type: Int, text: String): Int {
            val id = glCreateShader(type); glShaderSource(id, text); glCompileShader(id)
            val compiled = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, compiled, 0)
            if(compiled[0] == 0) { val error = glGetShaderInfoLog(id); glDeleteShader(id); error(error) }; return id
        }
        private fun fail(error: Throwable) { failed = true; status(error.message ?: "预览暂时无法显示") }
    }
}
