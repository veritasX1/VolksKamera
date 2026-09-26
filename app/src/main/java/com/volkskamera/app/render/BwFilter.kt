package com.volkskamera.app.render

import android.content.Context
import java.io.File

/**
 * Farbfilter für Schwarzweißfilm. Ein Filter lässt seine eigene Farbe durch und sperrt die
 * Gegenfarbe: die Farbkanäle des Motivs werden vor der Umsetzung in Grau unterschiedlich
 * gewichtet. Die Belichtung wird um den Filterfaktor ausgeglichen (neutrales Grau bleibt gleich hell).
 *
 * Bezeichnungen: Kodak-Wratten-Nummer (mit alter Kodak-Buchstabenbezeichnung), B+W-Nummer, Hoya-Name.
 * Durchlässigkeit je Kanal ist eine Näherung der Filterkurven auf die drei Farbkanäle.
 */
enum class BwFilter(
    private val labelDe: String,
    val wratten: String,
    val others: String,
    /** Belichtungsverlängerung (Tageslicht, Richtwert) */
    private val factorDe: String,
    val r: Float, val g: Float, val b: Float,
    private val useDe: String,
) {
    KEINER("Kein Filter", "–", "", "1×", 1f, 1f, 1f,
        "Film sieht wie er ist: Blau wird hell wiedergegeben, Himmel oft fast weiß."),
    GELB_HELL("Gelb hell", "Wratten 6 (K1)", "B+W 021", "1,5×", 1f, 0.97f, 0.6f,
        "Leichte Korrektur der Blauempfindlichkeit, Himmel minimal dunkler. Für Porträts im Freien."),
    GELB("Gelb mittel", "Wratten 8 (K2)", "B+W 022 · Hoya K2", "2×", 1f, 0.93f, 0.35f,
        "Der Standardfilter: Tonwerte wie das Auge sie empfindet, Wolken heben sich vom Himmel ab, Haut angenehm."),
    GELB_DUNKEL("Gelb dunkel", "Wratten 15 (G)", "B+W 023", "2,5×", 1f, 0.85f, 0.12f,
        "Deutlich dunklerer Himmel, kräftige Wolken, dämpft Dunst in der Ferne. Landschaft und Architektur."),
    GELBORANGE("Gelborange", "Wratten 16", "B+W 040 · Hoya G", "3×", 1f, 0.65f, 0.06f,
        "Dramatischer Himmel, Sommersprossen und Hautunreinheiten verschwinden, Backstein hell. Landschaft, Stadt."),
    ROTORANGE("Rotorange", "Wratten 22", "B+W 041", "5×", 1f, 0.4f, 0.04f,
        "Sehr dunkler Himmel, starke Wolken, durchdringt Dunst. Grün wird dunkel."),
    ROT("Rot", "Wratten 25 (A)", "B+W 090 · Hoya 25A", "8×", 1f, 0.12f, 0.03f,
        "Fast schwarzer Himmel, weiße Wolken, harte Kontraste. Nachtähnliche Stimmung am Tag, rote Lippen fast weiß."),
    DUNKELROT("Dunkelrot", "Wratten 29 (F)", "B+W 091", "16×", 1f, 0.03f, 0.01f,
        "Extremer Kontrast, schwarzer Himmel, Laub dunkel – Tag-für-Nacht-Effekt („Day for Night“)."),
    GELBGRUEN("Gelbgrün", "Wratten 11 (X1)", "B+W 060 · Hoya X0", "4×", 0.55f, 1f, 0.3f,
        "Hellt Laub und Wiesen auf, Himmel etwas dunkler, Hauttöne kräftiger. Frühling und Sommer im Grünen."),
    GRUEN("Grün", "Wratten 13 (X2)", "B+W 061 · Hoya X1", "5×", 0.35f, 1f, 0.2f,
        "Laub sehr hell und differenziert, Rot dunkel, Haut wirkt gebräunt. Männerporträts, Pflanzen."),
    GRUEN_DREIFARB("Grün (Dreifarben)", "Wratten 58 (B)", "", "ca. 6×", 0.08f, 1f, 0.1f,
        "Reiner Grünauszug: Grün leuchtend, Rot und Blau fast schwarz. Technische Aufnahmen, Farbauszüge."),
    BLAU("Blau", "Wratten 47 (C5)", "", "ca. 6×", 0.05f, 0.15f, 1f,
        "Betont Dunst und Nebel, Rot und Haut sehr dunkel – wirkt wie früher orthochromatischer Film."),
    ;

    val label get() = com.volkskamera.app.t(labelDe)
    val use get() = com.volkskamera.app.t(useDe)
    /** Filterfaktor; Dezimalkomma nur auf Deutsch/Französisch/Russisch */
    val factor get() = if (com.volkskamera.app.I18n.lang == com.volkskamera.app.I18n.Lang.EN) factorDe.replace(',', '.').replace("ca.", "approx.") else factorDe.replace("ca.", com.volkskamera.app.t("ca."))
    val fullName get() = if (this == KEINER) label else "$label · $wratten"

    companion object {
        fun byName(n: String?) = entries.firstOrNull { it.name == n } ?: KEINER

        /**
         * LUT-Datei mit vorgeschaltetem Filter (zwischengespeichert). [base] ist Asset- oder Dateipfad.
         * Kein Filter -> [base] unverändert.
         */
        fun lutWith(context: Context, base: String, f: BwFilter): String {
            if (f == KEINER) return base
            val dir = File(context.filesDir, "luts_filter").apply { mkdirs() }
            val out = File(dir, "v2_${Integer.toHexString(base.hashCode())}_${f.name}.png")
            if (!out.isFile) runCatching { LutFile.write(apply(LutFile.load(context, base), f), out) }
                .onFailure { return base }
            return out.absolutePath
        }

        /**
         * Filter vor die LUT schalten. Der S/W-Film sieht das Motiv als gewichtete Summe der Kanäle
         * (seine Farbempfindlichkeit w, aus der LUT gemessen); der Filter multipliziert diese
         * Gewichte mit seiner Durchlässigkeit. Gerechnet in linearem Licht, normiert auf neutrales Grau
         * (= Belichtung um den Filterfaktor verlängert). Der Grauwert läuft dann durch die Tonkurve
         * des Films: neu(rgb) = LUT(y, y, y). So läuft kein Kanal über.
         */
        fun apply(cube: FloatArray, f: BwFilter): FloatArray {
            val n = LutFile.N
            fun lum(c: FloatArray) = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2]
            val tmp = FloatArray(3)
            // Gewichte: welche Graustufe liefert die LUT für reines Rot/Grün/Blau? (über die Tonkurve zurückgerechnet)
            val ramp = FloatArray(n) { i -> val v = i / (n - 1f); lum(LutFile.sample(cube, v, v, v, tmp)) }
            fun grayFor(out: Float): Float {
                for (i in 1 until n) if (ramp[i] >= out) {
                    val d = (ramp[i] - ramp[i - 1]).coerceAtLeast(1e-6f)
                    return (i - 1 + ((out - ramp[i - 1]) / d).coerceIn(0f, 1f)) / (n - 1f)
                }
                return 1f
            }
            val w = listOf(
                grayFor(lum(LutFile.sample(cube, 1f, 0f, 0f, tmp))),
                grayFor(lum(LutFile.sample(cube, 0f, 1f, 0f, tmp))),
                grayFor(lum(LutFile.sample(cube, 0f, 0f, 1f, tmp))),
            ).map { it.coerceAtLeast(0.005f) }
            val wr = w[0] * f.r; val wg = w[1] * f.g; val wb = w[2] * f.b
            val sum = wr + wg + wb
            val lut = FloatArray(n)   // Kanal-Werte in linearem Licht je Gitterstufe
            for (i in 0 until n) lut[i] = lin(i / (n - 1f))
            val out = FloatArray(cube.size)
            for (bi in 0 until n) for (gi in 0 until n) for (ri in 0 until n) {
                val y = gam((wr * lut[ri] + wg * lut[gi] + wb * lut[bi]) / sum)
                LutFile.sample(cube, y, y, y, tmp)
                val i = ((bi * n + gi) * n + ri) * 3
                out[i] = tmp[0]; out[i + 1] = tmp[1]; out[i + 2] = tmp[2]
            }
            return out
        }

        private fun lin(v: Float): Float { val c = v.coerceIn(0f, 1f); return if (c <= 0.04045f) c / 12.92f else Math.pow((c + 0.055) / 1.055, 2.4).toFloat() }
        private fun gam(v: Float): Float { val c = v.coerceIn(0f, 1f); return if (c <= 0.0031308f) c * 12.92f else (1.055 * Math.pow(c.toDouble(), 1 / 2.4) - 0.055).toFloat() }
    }
}
