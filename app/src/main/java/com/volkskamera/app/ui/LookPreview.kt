package com.volkskamera.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.render.CountdownRenderer
import com.volkskamera.app.render.FilmLook
import com.volkskamera.app.render.FilmLookRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Animierte Vorschau: das Standbild aus dem Video läuft durch denselben FilmLookRenderer
 * wie der Export – Korn, Staub, Leaks, Flackern und Wackeln bewegen sich also schon hier.
 */
@UnstableApi
@Composable
fun LookPreview(still: Bitmap, look: FilmLook, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember { LookPreviewView(context) }
    DisposableEffect(view) {
        onDispose { view.releaseGl() }
    }
    AndroidView(factory = { view }, modifier = modifier, update = {
        it.setStill(still)
        it.setLook(look)
    })
}

@UnstableApi
private class LookPreviewView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    @Volatile private var pendingStill: Bitmap? = null
    @Volatile private var pendingLook: FilmLook? = null
    private var currentStill: Bitmap? = null

    private var renderer: FilmLookRenderer? = null
    private var countdown: CountdownRenderer? = null
    private var current: FilmLook? = null
    // Zwischenbild für den Countdown (wird danach wie ein Filmbild durch den Look geschickt)
    private var fbo = 0
    private var fboTex = 0
    private var fboW = 0
    private var fboH = 0
    private var stillTex = 0
    private var stillW = 0
    private var stillH = 0
    private var hasLook = false
    /** Filmtakt der Vorschau = gewählte Film-Bildrate (Original: 30) */
    private var filmFps = 18f
    private var viewW = 1
    private var viewH = 1
    private val t0 = System.nanoTime()

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setStill(b: Bitmap) {
        if (b !== currentStill) {
            currentStill = b
            pendingStill = b
        }
    }

    fun setLook(l: FilmLook) {
        pendingLook = l
    }

    fun releaseGl() {
        queueEvent {
            renderer?.release()
            renderer = null
            countdown?.release()
            countdown = null
            if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            if (fboTex != 0) GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
            fbo = 0; fboTex = 0
            if (stillTex != 0) GLES20.glDeleteTextures(1, intArrayOf(stillTex), 0)
            stillTex = 0
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // neuer GL-Kontext: alles neu anlegen (alte Texturen sind mit dem Kontext weg)
        renderer = FilmLookRenderer(context, maxCachedSequences = 6)
        countdown = CountdownRenderer()
        fbo = 0; fboTex = 0
        stillTex = 0
        hasLook = false
        currentStill?.let { pendingStill = it }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val r = renderer ?: return
        pendingStill?.let { b ->
            pendingStill = null
            if (stillTex != 0) GLES20.glDeleteTextures(1, intArrayOf(stillTex), 0)
            stillTex = FilmLookRenderer.uploadTexture(b)
            stillW = b.width
            stillH = b.height
        }
        pendingLook?.let {
            pendingLook = null
            r.setLook(it)
            current = it
            filmFps = it.targetFps ?: 30f
            hasLook = true
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (stillTex == 0 || !hasLook) return

        // Ausgabe unverzerrt in die View einpassen
        val (ow, oh) = r.outputSize(stillW, stillH)
        val scale = minOf(viewW / ow.toFloat(), viewH / oh.toFloat())
        val w = (ow * scale).toInt()
        val h = (oh * scale).toInt()
        GLES20.glViewport((viewW - w) / 2, (viewH - h) / 2, w, h)
        // Vorschau als Schleife: [Countdown 5 s] + Clip 8 s (+ Filmriss am Ende, falls gewählt)
        val l = current ?: return
        val cdUs = if (l.countdown) CountdownRenderer.SECONDS * 1_000_000L else 0L
        val clipUs = if (l.burnEnd != null) 10_000_000L else 8_000_000L
        val pos = ((System.nanoTime() - t0) / 1000) % (cdUs + clipUs)
        if (pos < cdUs) {
            // Countdown ins Zwischenbild zeichnen, dann mit dem Look (ohne Leak/Filmriss) darüber
            ensureFbo(stillW, stillH)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, fboW, fboH)
            countdown?.draw(pos, fboW, fboH)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport((viewW - w) / 2, (viewH - h) / 2, w, h)
            r.setLook(l.copy(leak = null, burnEnd = null))
            r.draw(fboTex, pos, fboW, fboH, inputFlipY = false, filmFps = filmFps)
            r.setLook(l)
        } else {
            r.draw(stillTex, pos - cdUs, stillW, stillH, inputFlipY = true, filmFps = filmFps, clipDurationUs = clipUs)
        }
    }

    private fun ensureFbo(w: Int, h: Int) {
        if (fbo != 0 && fboW == w && fboH == h) return
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (fboTex != 0) GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        fboTex = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val f = IntArray(1)
        GLES20.glGenFramebuffers(1, f, 0)
        fbo = f[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        fboW = w; fboH = h
    }
}
