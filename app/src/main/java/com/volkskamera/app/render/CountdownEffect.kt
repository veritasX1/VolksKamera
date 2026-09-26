package com.volkskamera.app.render

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Countdown-Vorspann als Media3-Effekt: ersetzt das (schwarze) Platzhalterbild durch den
 * gezeichneten Vorspann. Die Zeit zählt ab dem ersten Bild dieses Abschnitts.
 */
@UnstableApi
class CountdownEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(useHdr)

    private class Program(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val renderer = try {
            CountdownRenderer()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
        private var w = 1
        private var h = 1
        private var startUs = -1L

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            w = inputWidth; h = inputHeight
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            if (startUs < 0) startUs = presentationTimeUs
            try {
                renderer.draw(presentationTimeUs - startUs, w, h)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
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
}
