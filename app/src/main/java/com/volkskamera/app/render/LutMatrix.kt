package com.volkskamera.app.render

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * Eine LUT als einfache Farbmatrix nähern – für den Sucher. Die Matrix wird als
 * ColorMatrixColorFilter über die Sucher-View gelegt: das erledigt die Grafik-Composition,
 * die Kamera-Pipeline (und damit die 60 fps) bleibt unberührt. Ein echter LUT-Shader im
 * Sucher bräuchte einen Live-Effekt in CameraX – genau das hat RetroCam instabil gemacht.
 *
 * Außerdem: Erkennung von Schwarzweiß-LUTs (alle Ausgabefarben grau).
 */
object LutMatrix {
    class Info(
        /** affine 3x4-Matrix zeilenweise: r' = m0*r + m1*g + m2*b + m3 (Werte 0..1) */
        val matrix: FloatArray,
        val mono: Boolean,
    )

    private const val N = 33
    private val cache = LruCache<String, Info>(32)

    fun info(context: Context, lutFile: String): Info = cache.get(lutFile) ?: compute(context, lutFile).also { cache.put(lutFile, it) }

    private fun compute(context: Context, lutFile: String): Info {
        val bmp = LutFile.open(context, lutFile).use { BitmapFactory.decodeStream(it) }
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        bmp.recycle()

        // Stichproben auf einem 9er-Gitter im Farbwürfel, Kleinste-Quadrate-Anpassung
        val ata = Array(4) { DoubleArray(4) }
        val atb = Array(3) { DoubleArray(4) }
        var maxChroma = 0
        val steps = (0 until N step 4).toList() + (N - 1)
        for (bi in steps) for (gi in steps) for (ri in steps) {
            val c = px[gi * N * N + bi * N + ri]
            val out = intArrayOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)
            maxChroma = maxOf(maxChroma, out.max() - out.min())
            val x = doubleArrayOf(ri / (N - 1.0), gi / (N - 1.0), bi / (N - 1.0), 1.0)
            for (i in 0..3) {
                for (j in 0..3) ata[i][j] += x[i] * x[j]
                for (ch in 0..2) atb[ch][i] += x[i] * out[ch] / 255.0
            }
        }
        val m = FloatArray(12)
        for (ch in 0..2) {
            val sol = solve4(ata, atb[ch])
            for (i in 0..3) m[ch * 4 + i] = sol[i].toFloat()
        }
        // Toleranz: ein Hauch Tönung (Sepia-Graustufen) gilt noch nicht als Farbe
        return Info(m, mono = maxChroma <= 6)
    }

    /** 4x4-Gleichungssystem per Gauß-Elimination (Normalengleichungen sind gut konditioniert). */
    private fun solve4(a0: Array<DoubleArray>, b0: DoubleArray): DoubleArray {
        val a = Array(4) { a0[it].copyOf() }
        val b = b0.copyOf()
        for (col in 0..3) {
            val piv = (col..3).maxBy { kotlin.math.abs(a[it][col]) }
            a[col] = a[piv].also { a[piv] = a[col] }
            b[col] = b[piv].also { b[piv] = b[col] }
            for (r in col + 1..3) {
                val f = a[r][col] / a[col][col]
                for (k in col..3) a[r][k] -= f * a[col][k]
                b[r] -= f * b[col]
            }
        }
        val x = DoubleArray(4)
        for (r in 3 downTo 0) {
            var s = b[r]
            for (k in r + 1..3) s -= a[r][k] * x[k]
            x[r] = s / a[r][r]
        }
        return x
    }

    /**
     * Android-ColorMatrix (4x5, Offset in 0..255) für den Sucher: LUT-Näherung mit Stärke,
     * danach ggf. Schwarzweiß. null = kein Filter nötig.
     */
    fun viewfinderMatrix(info: Info?, mix: Float, mono: Boolean, look: FilmLook? = null): FloatArray? {
        val adj = look?.let { adjustmentMatrix(it) }
        if (info == null && !mono && adj == null) return null
        // Start: Einheitsmatrix, dann Richtung LUT-Matrix mischen
        var m = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
        if (info != null) {
            val base = if (info.mono) lumaMatrix() else m.copyOf()
            for (i in 0 until 12) m[i] = base[i] + (info.matrix[i] - base[i]) * mix
        }
        // Bildkorrektur wirkt VOR dem Farblook: erst Korrektur, dann LUT
        if (adj != null) m = compose(m, adj)
        if (mono || info?.mono == true) {
            // Graustufen nach der Farbmatrix: Luma der drei Ausgangszeilen
            val w = floatArrayOf(0.299f, 0.587f, 0.114f)
            val g = FloatArray(4) { i -> w[0] * m[i] + w[1] * m[4 + i] + w[2] * m[8 + i] }
            for (ch in 0..2) for (i in 0..3) m[ch * 4 + i] = g[i]
        }
        return floatArrayOf(
            m[0], m[1], m[2], 0f, m[3] * 255f,
            m[4], m[5], m[6], 0f, m[7] * 255f,
            m[8], m[9], m[10], 0f, m[11] * 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /**
     * Die linearen Teile der Bildkorrektur als affine Matrix (wie im Shader: Helligkeit ->
     * Farbtemperatur -> Kontrast -> Sättigung). Schatten/Lichter, Schärfe und Vignette sind
     * nicht linear und erscheinen nur in Vorschau und Film, nicht im Sucher. null = neutral.
     */
    private fun adjustmentMatrix(l: FilmLook): FloatArray? {
        if (l.brightness == 0f && l.temperature == 0f && l.contrast == 0f && l.saturation == 0f) return null
        val e = Math.pow(2.0, l.brightness * 0.8).toFloat()
        val g = floatArrayOf(e * (1 + l.temperature * 0.10f), e * (1 + l.temperature * 0.02f), e * (1 - l.temperature * 0.12f))
        val gain = floatArrayOf(g[0], 0f, 0f, 0f, 0f, g[1], 0f, 0f, 0f, 0f, g[2], 0f)
        val k = 1 + l.contrast * 0.6f
        val con = floatArrayOf(k, 0f, 0f, 0.5f * (1 - k), 0f, k, 0f, 0.5f * (1 - k), 0f, 0f, k, 0.5f * (1 - k))
        val s = 1 + l.saturation
        val w = floatArrayOf(0.299f, 0.587f, 0.114f)
        val sat = FloatArray(12)
        for (r in 0..2) for (c in 0..2) sat[r * 4 + c] = (1 - s) * w[c] + if (r == c) s else 0f
        return compose(sat, compose(con, gain))
    }

    /** a ∘ b: erst b, dann a (beide affine 3x4, zeilenweise). */
    private fun compose(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(12)
        for (r in 0..2) {
            for (c in 0..2) o[r * 4 + c] = (0..2).sumOf { (a[r * 4 + it] * b[it * 4 + c]).toDouble() }.toFloat()
            o[r * 4 + 3] = (0..2).sumOf { (a[r * 4 + it] * b[it * 4 + 3]).toDouble() }.toFloat() + a[r * 4 + 3]
        }
        return o
    }

    private fun lumaMatrix() = floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f,
        0.299f, 0.587f, 0.114f, 0f,
        0.299f, 0.587f, 0.114f, 0f,
    )
}
