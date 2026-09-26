package com.volkskamera.app.render

import android.content.Context
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Film-Look als Media3-Effekt: die ganze Kette aus FilmLookRenderer plus Bildraten-Umrechnung. */
@UnstableApi
class FilmLookEffect(private val look: FilmLook, private val clipDurationUs: Long = 0) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FilmLookShaderProgram(context, useHdr, look, clipDurationUs)
}

@UnstableApi
private class FilmLookShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val look: FilmLook,
    private val clipDurationUs: Long,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    // Läuft auf dem GL-Thread von Media3: Programm und Texturen können direkt angelegt werden
    private val renderer = try {
        FilmLookRenderer(context).apply { setLook(look) }
    } catch (e: Exception) {
        throw VideoFrameProcessingException(e)
    }
    private var inW = 0
    private var inH = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        inW = inputWidth
        inH = inputHeight
        val (w, h) = renderer.outputSize(inputWidth, inputHeight)
        return Size(w, h)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (firstUs < 0) {
                firstUs = presentationTimeUs
                android.util.Log.d("VolksKameraTest", "FilmLook: erstes Bild bei $presentationTimeUs µs, Cliplänge $clipDurationUs µs")
            }
            // Zeit relativ zum Clipanfang (in einer Sequenz mit Countdown zählt Media3 weiter)
            renderer.draw(inputTexId, presentationTimeUs - firstUs, inW, inH, inputFlipY = false,
                filmFps = look.targetFps ?: sourceFps, clipDurationUs = clipDurationUs)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    // --- Bildrate: auf ein exaktes Raster (z.B. 18 fps) bringen ---
    // Media3s FrameDropEffect behält ein Bild erst, wenn seit dem letzten >= 1/Ziel vergangen ist;
    // bei 30-fps-Material ist das jedes zweite Bild = 15 fps statt 18. Hier stattdessen: für jeden
    // Rasterpunkt das nächstliegende Quellbild nehmen und genau auf den Rasterpunkt setzen.
    // Verschiebung max. eine halbe Quell-Bilddauer (~17 ms), der Ton bleibt also synchron.
    private var gridStartUs = -1L
    private var nextGrid = 0L
    private var lastInputUs = -1L
    /** Bildrate des Originals, geschätzt aus den Zeitstempeln (für "Original"-Bildrate). */
    private var sourceFps = 30f
    private var firstUs = -1L
    private var avgIntervalUs = 0.0

    override fun queueInputFrame(glObjectsProvider: GlObjectsProvider, inputTexture: GlTextureInfo, presentationTimeUs: Long) {
        val fps = look.targetFps
        if (fps == null) {
            if (lastInputUs >= 0 && presentationTimeUs > lastInputUs) {
                val d = (presentationTimeUs - lastInputUs).toDouble()
                avgIntervalUs = if (avgIntervalUs == 0.0) d else avgIntervalUs * 0.9 + d * 0.1
                sourceFps = (1_000_000.0 / avgIntervalUs).toFloat()
            }
            lastInputUs = presentationTimeUs
            super.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeUs)
            return
        }
        if (gridStartUs < 0) gridStartUs = presentationTimeUs
        val step = 1_000_000.0 / fps
        val srcInterval = if (lastInputUs >= 0) presentationTimeUs - lastInputUs else 0L
        lastInputUs = presentationTimeUs
        val gridUs = gridStartUs + (nextGrid * step).toLong()
        if (presentationTimeUs + srcInterval / 2 >= gridUs) {
            // Rasterpunkte überspringen, falls das Quellmaterial Lücken hat
            while (gridStartUs + ((nextGrid + 1) * step).toLong() <= presentationTimeUs - srcInterval / 2) nextGrid++
            val outUs = gridStartUs + (nextGrid * step).toLong()
            nextGrid++
            super.queueInputFrame(glObjectsProvider, inputTexture, outUs)
        } else {
            // Bild verwerfen: Eingang sofort zurückgeben, damit der Vorgänger weiterliefert
            getInputListener().onInputFrameProcessed(inputTexture)
            getInputListener().onReadyToAcceptInputFrame()
        }
    }

    override fun flush() {
        gridStartUs = -1L; nextGrid = 0L; lastInputUs = -1L
        super.flush()
    }

    override fun release() {
        super.release()
        try {
            renderer.release()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}
