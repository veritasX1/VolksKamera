package com.volkskamera.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import kotlin.math.floor

/**
 * Klassischer Film-Vorspann ("Academy Leader"): grauer Grund, zwei Kreise, Fadenkreuz,
 * umlaufender Zeiger mit abgedunkeltem Sektor, große Zahl 5 … 1 – eine Zahl pro Sekunde.
 * Komplett selbst gezeichnet (Shader + Ziffern per Canvas), keine fremden Grafiken.
 * Der Film-Look (Korn, Kratzer, Rahmen …) kommt danach wie beim eigentlichen Film darüber.
 */
@UnstableApi
class CountdownRenderer {
    private val program = GlProgram(VERTEX, FRAGMENT).apply {
        setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }
    private val digits = IntArray(SECONDS) { i -> digitTexture(SECONDS - i) }

    /** [timeUs] 0 … SECONDS s; [w]/[h] Größe des Ziels (für runde Kreise). */
    fun draw(timeUs: Long, w: Int, h: Int) {
        val t = (timeUs / 1_000_000.0).coerceIn(0.0, SECONDS - 1e-6)
        val sec = floor(t).toInt()
        program.use()
        program.setSamplerTexIdUniform("uDigit", digits[sec], 0)
        program.setFloatUniform("uPhase", (t - sec).toFloat())
        program.setFloatUniform("uAspect", w / h.toFloat())
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        program.delete()
        GLES20.glDeleteTextures(digits.size, digits, 0)
    }

    private fun digitTexture(n: Int): Int {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 210f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        c.drawText(n.toString(), 128f, 128f - (p.descent() + p.ascent()) / 2, p)
        return FilmLookRenderer.uploadTexture(b).also { b.recycle() }
    }

    companion object {
        const val SECONDS = 5

        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() {
                gl_Position = aFramePosition;
                vUv = aFramePosition.xy * 0.5 + 0.5;
            }
        """

        private const val FRAGMENT = """
            precision highp float;
            varying vec2 vUv;
            uniform sampler2D uDigit;
            uniform float uPhase;
            uniform float uAspect;

            float ring(float d, float r, float w) { return 1.0 - smoothstep(w * 0.5, w * 0.5 + 0.004, abs(d - r)); }

            void main() {
                // Koordinaten in Bildhöhen-Einheiten, Mitte = 0
                vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
                float d = length(p);
                vec3 c = vec3(0.62);

                // Sektor, den der Zeiger schon überstrichen hat (ab 12 Uhr im Uhrzeigersinn), etwas dunkler
                float ang = atan(p.x, p.y);                    // 0 oben, positiv im Uhrzeigersinn
                float a01 = fract(ang / 6.2831853 + 1.0);
                if (a01 < uPhase && d < 0.47) c = vec3(0.42);
                // der Zeiger selbst
                float hand = abs(a01 - uPhase) * 6.2831853 * d;
                if (d < 0.47 && hand < 0.004) c = vec3(0.1);

                // Kreise und Fadenkreuz
                float lines = max(ring(d, 0.33, 0.012), ring(d, 0.40, 0.012));
                lines = max(lines, 1.0 - smoothstep(0.002, 0.006, abs(p.x)));
                lines = max(lines, 1.0 - smoothstep(0.002, 0.006, abs(p.y)));
                c = mix(c, vec3(0.95), lines * 0.9);

                // Zahl in der Mitte (Textur: Zeile 0 oben -> y spiegeln)
                vec2 duv = p / 0.52 + 0.5;
                if (duv.x > 0.0 && duv.x < 1.0 && duv.y > 0.0 && duv.y < 1.0) {
                    float a = texture2D(uDigit, vec2(duv.x, 1.0 - duv.y)).a;
                    c = mix(c, vec3(0.05), a);
                }
                gl_FragColor = vec4(c, 1.0);
            }
        """
    }
}
