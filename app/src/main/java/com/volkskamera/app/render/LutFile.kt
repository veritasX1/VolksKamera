package com.volkskamera.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow

/**
 * LUT-Streifen (33er-Würfel als 1089×33-Bild, Kachel = Blau, x = Rot, y = Grün) laden, auf
 * Bilder anwenden und mit Bearbeitungen neu backen. Pfade mit führendem „/" sind eigene LUTs
 * im App-Speicher, alle anderen App-Assets.
 */
object LutFile {
    const val N = 33

    fun open(context: Context, path: String): InputStream =
        if (path.startsWith("/")) File(path).inputStream() else context.assets.open(path)

    fun exists(context: Context, path: String): Boolean =
        if (path.startsWith("/")) File(path).isFile
        else runCatching { context.assets.open(path).close(); true }.getOrDefault(false)

    private val cache = LruCache<String, FloatArray>(8)

    /** Würfel als RGB-Floats 0..1, Index ((b*N + g)*N + r)*3. */
    fun load(context: Context, path: String): FloatArray = cache.get(path) ?: run {
        val bmp = open(context, path).use { BitmapFactory.decodeStream(it) } ?: error("LUT $path unlesbar")
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        bmp.recycle()
        val out = FloatArray(N * N * N * 3)
        for (b in 0 until N) for (g in 0 until N) for (r in 0 until N) {
            val c = px[g * N * N + b * N + r]
            val i = ((b * N + g) * N + r) * 3
            out[i] = (c shr 16 and 0xFF) / 255f
            out[i + 1] = (c shr 8 and 0xFF) / 255f
            out[i + 2] = (c and 0xFF) / 255f
        }
        cache.put(path, out)
        out
    }

    /** Identitäts-Würfel (für „ohne LUT"). */
    fun identity(): FloatArray = FloatArray(N * N * N * 3).also { o ->
        for (b in 0 until N) for (g in 0 until N) for (r in 0 until N) {
            val i = ((b * N + g) * N + r) * 3
            o[i] = r / (N - 1f); o[i + 1] = g / (N - 1f); o[i + 2] = b / (N - 1f)
        }
    }

    /** Würfel als PNG-Streifen speichern (App-Format). */
    fun write(cube: FloatArray, file: File) {
        val px = IntArray(N * N * N)
        for (b in 0 until N) for (g in 0 until N) for (r in 0 until N) {
            val i = ((b * N + g) * N + r) * 3
            fun q(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            px[g * N * N + b * N + r] = (0xFF shl 24) or (q(cube[i]) shl 16) or (q(cube[i + 1]) shl 8) or q(cube[i + 2])
        }
        val bmp = Bitmap.createBitmap(px, N * N, N, Bitmap.Config.ARGB_8888)
        file.parentFile?.mkdirs()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    /** Eine Farbe (0..1) trilinear im Würfel nachschlagen, Ergebnis in [out]. */
    fun sample(cube: FloatArray, r0: Float, g0: Float, b0: Float, out: FloatArray): FloatArray {
        val s = N - 1f
        val r = r0.coerceIn(0f, 1f) * s; val g = g0.coerceIn(0f, 1f) * s; val b = b0.coerceIn(0f, 1f) * s
        val ri = min(r.toInt(), N - 2); val gi = min(g.toInt(), N - 2); val bi = min(b.toInt(), N - 2)
        val fr = r - ri; val fg = g - gi; val fb = b - bi
        for (ch in 0..2) {
            fun at(bb: Int, gg: Int, rr: Int) = cube[((bb * N + gg) * N + rr) * 3 + ch]
            val c00 = at(bi, gi, ri) * (1 - fr) + at(bi, gi, ri + 1) * fr
            val c01 = at(bi, gi + 1, ri) * (1 - fr) + at(bi, gi + 1, ri + 1) * fr
            val c10 = at(bi + 1, gi, ri) * (1 - fr) + at(bi + 1, gi, ri + 1) * fr
            val c11 = at(bi + 1, gi + 1, ri) * (1 - fr) + at(bi + 1, gi + 1, ri + 1) * fr
            out[ch] = (c00 * (1 - fg) + c01 * fg) * (1 - fb) + (c10 * (1 - fg) + c11 * fg) * fb
        }
        return out
    }

    /** Würfel trilinear auf ein Bild anwenden (für Vorschau und Beispielbilder). */
    fun apply(src: Bitmap, cube: FloatArray): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val s = N - 1f
        for (p in px.indices) {
            val c = px[p]
            val r = (c shr 16 and 0xFF) / 255f * s
            val g = (c shr 8 and 0xFF) / 255f * s
            val b = (c and 0xFF) / 255f * s
            val r0 = min(r.toInt(), N - 2); val g0 = min(g.toInt(), N - 2); val b0 = min(b.toInt(), N - 2)
            val fr = r - r0; val fg = g - g0; val fb = b - b0
            var o = 0xFF shl 24
            for (ch in 0..2) {
                fun at(bb: Int, gg: Int, rr: Int) = cube[((bb * N + gg) * N + rr) * 3 + ch]
                val c00 = at(b0, g0, r0) * (1 - fr) + at(b0, g0, r0 + 1) * fr
                val c01 = at(b0, g0 + 1, r0) * (1 - fr) + at(b0, g0 + 1, r0 + 1) * fr
                val c10 = at(b0 + 1, g0, r0) * (1 - fr) + at(b0 + 1, g0, r0 + 1) * fr
                val c11 = at(b0 + 1, g0 + 1, r0) * (1 - fr) + at(b0 + 1, g0 + 1, r0 + 1) * fr
                val v = (c00 * (1 - fg) + c01 * fg) * (1 - fb) + (c10 * (1 - fg) + c11 * fg) * fb
                o = o or ((v.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl (16 - 8 * ch))
            }
            px[p] = o
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Bearbeitung auf einen Würfel backen: jede Ausgabefarbe der Basis-LUT wird nachbearbeitet. */
    fun bake(base: FloatArray, e: LutEdit): FloatArray {
        if (e.isNeutral) return base
        val out = base.copyOf()
        val rgb = FloatArray(3)
        for (i in out.indices step 3) {
            rgb[0] = out[i]; rgb[1] = out[i + 1]; rgb[2] = out[i + 2]
            e.applyTo(rgb)
            out[i] = rgb[0]; out[i + 1] = rgb[1]; out[i + 2] = rgb[2]
        }
        return out
    }
}

/**
 * Bearbeitung einer Film-LUT. Alle Regler −1…+1 (0 = unverändert), Farbtöne in Grad.
 * Wirkt auf das Ergebnis der Basis-LUT – der Charakter des Films bleibt die Grundlage.
 */
data class LutEdit(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    /** angehobenes Schwarz (verblasst) > 0, tieferes Schwarz < 0 */
    val blacks: Float = 0f,
    /** gedämpfte Lichter < 0, hellere Lichter > 0 */
    val whites: Float = 0f,
    val temperature: Float = 0f,
    /** > 0 Magenta, < 0 Grün */
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val shadowHue: Float = 210f,
    val shadowAmount: Float = 0f,
    val highlightHue: Float = 40f,
    val highlightAmount: Float = 0f,
    /** Kanal-Mitten: > 0 mehr Rot/Grün/Blau in den Mitteltönen */
    val red: Float = 0f,
    val green: Float = 0f,
    val blue: Float = 0f,
) {
    val isNeutral get() = this == LutEdit(shadowHue = shadowHue, highlightHue = highlightHue)

    fun applyTo(c: FloatArray) {
        // Belichtung (Blendenstufen) und Weißabgleich
        val ex = 2f.pow(exposure * 1.5f)
        c[0] *= ex * (1 + temperature * 0.12f + tint * 0.04f)
        c[1] *= ex * (1 + temperature * 0.02f - tint * 0.10f)
        c[2] *= ex * (1 - temperature * 0.14f + tint * 0.06f)
        // Kanal-Gamma (Mitteltöne)
        if (red != 0f) c[0] = gamma(c[0], red)
        if (green != 0f) c[1] = gamma(c[1], green)
        if (blue != 0f) c[2] = gamma(c[2], blue)
        // Kontrast als S-Kurve um 0,5 (Enden bleiben stabil)
        if (contrast != 0f) for (k in 0..2) c[k] = sCurve(c[k].coerceIn(0f, 1f), contrast)
        // Schwarz- und Weißpunkt
        val lo = blacks * 0.15f
        val hi = 1f + whites * 0.12f
        for (k in 0..2) c[k] = lo + c[k] * (hi - lo)
        // Sättigung um die Luma
        val y = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2]
        if (saturation != 0f) {
            val s = 1f + saturation
            for (k in 0..2) c[k] = y + (c[k] - y) * s
        }
        // Teiltonung: Farbe in Schatten bzw. Lichter mischen (Gewicht nach Helligkeit)
        if (shadowAmount != 0f) tone(c, shadowHue, shadowAmount * (1f - y).coerceIn(0f, 1f).pow(2) * 0.25f)
        if (highlightAmount != 0f) tone(c, highlightHue, highlightAmount * y.coerceIn(0f, 1f).pow(2) * 0.25f)
        for (k in 0..2) c[k] = c[k].coerceIn(0f, 1f)
    }

    private fun gamma(v: Float, amt: Float) = v.coerceIn(0f, 1f).pow(2f.pow(-amt * 0.6f))

    private fun sCurve(v: Float, k: Float): Float {
        // k > 0: steiler um die Mitte; k < 0: flacher
        // flache Seite höchstens bis Steigung 0,15 in der Mitte, sonst kippt die Kurve
        val a = if (k >= 0f) 1f + k * 1.2f else 1f + k * 0.85f
        val x = v - 0.5f
        val out = if (a >= 1f) 0.5f + x * a / (1f + (a - 1f) * abs(2 * x)) else 0.5f + x * (a + (1 - a) * 4 * x * x)
        return out.coerceIn(0f, 1f)
    }

    private fun tone(c: FloatArray, hue: Float, w: Float) {
        val h = Math.toRadians(hue.toDouble())
        // Farbton als Richtung in der Farbebene, luma-neutral
        val r = cos(h).toFloat(); val g = cos(h - 2.0944).toFloat(); val b = cos(h + 2.0944).toFloat()
        c[0] += r * w; c[1] += g * w; c[2] += b * w
    }

    companion object {
        fun hueColor(hue: Float): Int {
            val h = Math.toRadians(hue.toDouble())
            fun ch(o: Double) = ((0.5 + 0.5 * cos(h + o)) * 255).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(0.0) shl 16) or (ch(-2.0944) shl 8) or ch(2.0944)
        }
    }
}
