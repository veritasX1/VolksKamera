package com.volkskamera.app.render

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.C
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bild-Blende für Intro/Outro im Editor: am Anfang aus Schwarz/Weiß einblenden, am Ende
 * in Schwarz/Weiß ausblenden. Zeit relativ zum ersten Bild des Clips.
 */
@UnstableApi
class FadeEffect(
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    private val clipDurationUs: Long,
    private val white: Boolean,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(useHdr)

    private inner class Program(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val program = try {
            GlProgram(VERTEX, FRAGMENT).apply {
                setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            }
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
        private var firstUs = -1L

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            if (firstUs < 0) firstUs = presentationTimeUs
            val t = presentationTimeUs - firstUs
            var a = 1f
            if (fadeInUs > 0) a = minOf(a, t / fadeInUs.toFloat())
            if (fadeOutUs > 0 && clipDurationUs > 0) a = minOf(a, (clipDurationUs - t) / fadeOutUs.toFloat())
            try {
                program.use()
                program.setSamplerTexIdUniform("uTex", inputTexId, 0)
                program.setFloatUniform("uAlpha", a.coerceIn(0f, 1f))
                program.setFloatUniform("uWhite", if (white) 1f else 0f)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() { gl_Position = aFramePosition; vUv = aFramePosition.xy * 0.5 + 0.5; }
        """
        const val FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            uniform float uAlpha;
            uniform float uWhite;
            void main() {
                vec3 c = texture2D(uTex, vUv).rgb;
                // weiche Kurve statt linear – wirkt natürlicher
                float a = uAlpha * uAlpha * (3.0 - 2.0 * uAlpha);
                gl_FragColor = vec4(mix(vec3(uWhite), c, a), 1.0);
            }
        """
    }
}

/** Ton-Blende passend zur Bild-Blende (16-Bit-PCM). */
@UnstableApi
class FadeAudioProcessor(
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    private val clipDurationUs: Long,
) : BaseAudioProcessor() {
    private var sr = 48000
    private var ch = 2
    private var n = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        sr = inputAudioFormat.sampleRate
        ch = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val out = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        for (f in 0 until bytes / (2 * ch)) {
            val tUs = n * 1_000_000L / sr
            var g = 1f
            if (fadeInUs > 0) g = minOf(g, tUs / fadeInUs.toFloat())
            if (fadeOutUs > 0 && clipDurationUs > 0) g = minOf(g, (clipDurationUs - tUs) / fadeOutUs.toFloat())
            g = g.coerceIn(0f, 1f)
            for (c in 0 until ch) out.putShort((input.short * g).toInt().toShort())
            n++
        }
        out.flip()
    }

    override fun onReset() { n = 0 }
}
