package com.volkskamera.app.render

import android.content.Context
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Projektor-Modelle. Jedes beschreibt die Mechanik; Material, Klang und Lautstärke stellt
 * der Nutzer zusätzlich ein.
 *
 * @param strikes    Anschläge pro Filmbild (1 = nur Greifer, 2 = Greifer + Verschluss)
 * @param strikeGap  Abstand des zweiten Anschlags als Anteil der Bilddauer
 * @param pitch      Tonhöhe der Gehäuseresonanzen (1 = normal, < 1 = größeres Gerät)
 * @param ring       Nachklang (1 = normal)
 * @param jitter     Unregelmäßigkeit der Anschläge (0 = Uhrwerk, 0.3 = wackelig)
 * @param wobble     Tempo-Schwankung (Handkurbel, alter Motor) 0 … 0.3
 * @param hum        Motorbrummen, [whirr] Laufgeräusch, [whine] Riemen-/Getriebejaulen
 */
enum class ProjectorModel(
    val label: String,
    val strikes: Int,
    val strikeGap: Float,
    val pitch: Float,
    val ring: Float,
    val jitter: Float,
    val wobble: Float,
    val hum: Float,
    val whirr: Float,
    val whine: Float,
) {
    /** Das allererste Rattern (Rauschklick + Brummen + Laufgeräusch), unverändert */
    KLASSISCH("Klassisch (erstes Rattern)", 1, 0f, 1.0f, 1.0f, 0f, 0f, 0f, 0f, 0f),
    /** Eigene Handyaufnahmen: Metall/Plastik-Anschläge, gemischt nach dem Material-Regler */
    AUFNAHMEN("Eigene Aufnahmen", 1, 0f, 1.0f, 1.0f, 0.03f, 0f, 0.06f, 0.5f, 0f),
    HEIMKINO("Heimkino 8mm", 1, 0f, 1.0f, 1.0f, 0.03f, 0.0f, 0.10f, 0.8f, 0.0f),
    SUPER8("Super 8", 1, 0f, 1.15f, 0.8f, 0.02f, 0.0f, 0.06f, 0.6f, 0.02f),
    SCHULPROJEKTOR("16mm Schulprojektor", 2, 0.42f, 0.85f, 1.2f, 0.02f, 0.0f, 0.14f, 1.0f, 0.03f),
    KINO("Kinoprojektor 35mm", 2, 0.5f, 0.7f, 1.4f, 0.01f, 0.0f, 0.22f, 1.2f, 0.0f),
    SPIELZEUG("Spielzeugprojektor", 1, 0f, 1.3f, 0.6f, 0.15f, 0.04f, 0.03f, 0.4f, 0.0f),
    KURBEL("Handkurbel", 1, 0f, 1.05f, 0.9f, 0.10f, 0.25f, 0.0f, 0.3f, 0.0f),
    ALTER_MOTOR("Alter Motor", 1, 0f, 0.9f, 1.1f, 0.05f, 0.06f, 0.18f, 1.0f, 0.10f),
}

/**
 * Die geschnittenen Anschläge aus den eigenen Handyaufnahmen (klicks_schneiden.py),
 * 48 kHz Mono, einmal geladen und dann geteilt.
 */
class ClickSamples private constructor(val sr: Int, val metal: List<FloatArray>, val plastic: List<FloatArray>) {
    companion object {
        @Volatile private var cached: ClickSamples? = null

        fun load(context: Context): ClickSamples = cached ?: synchronized(this) {
            cached ?: runCatching {
                val j = context.assets.open("klick/klicks.json").bufferedReader().use { JSONObject(it.readText()) }
                fun list(k: String) = j.getJSONArray(k).let { a ->
                    (0 until a.length()).map { i ->
                        val bytes = context.assets.open(a.getString(i)).use { it.readBytes() }
                        FloatArray(bytes.size / 2) { n -> ((bytes[2 * n].toInt() and 0xFF) or (bytes[2 * n + 1].toInt() shl 8)).toShort() / 32768f }
                    }
                }
                ClickSamples(j.optInt("sr", 48000), list("metall"), list("plastik"))
            }.getOrElse { ClickSamples(48000, emptyList(), emptyList()) }.also { cached = it }
        }
    }
}

/**
 * Klangerzeuger für das Projektor-Rattern (Export UND Vorschau-Wiedergabe).
 *
 * Nach Messung eigener Aufnahmen (Metall: Energie 2–8 kHz, viele dichte unharmonische Teiltöne,
 * nach ~20 ms auf −20 dB = "Chunk"; Plastik: Energie 110–500 Hz, weicher Anschlag, nach ~10 ms
 * abgeklungen = hohles Klacken) gibt es zwei Klangkörper, zwischen denen "Material" überblendet:
 *   Metall  – 8 unharmonische Teiltöne 1,5–8 kHz + kurzer heller Rauschanteil + tiefer Körperschlag
 *   Plastik – 5 Hohlraum-Resonanzen 180–1400 Hz, angeregt von einem weichen (tiefpassgefilterten) Impuls
 * (Die erste Fassung verschob stattdessen drei Resonanzen – das klang nach "Ping" bzw. Holzstab.)
 * "Klang" (dumpf … klar) ist ein Tiefpass über alles. Dazu Motor, Laufgeräusch und ggf. Jaulen.
 *
 * @param rate       Anschläge pro Sekunde (Film-fps oder frei eingestellt)
 * @param material   0 = Metall, 1 = Plastik
 * @param tone       −1 = dumpf, +1 = klar
 * @param samples    für das Modell "Eigene Aufnahmen"
 */
class ProjectorSynth(
    private val sr: Int,
    private val model: ProjectorModel,
    private val rate: Float,
    private val material: Float,
    private val tone: Float,
    private val volume: Float,
    private val samples: ClickSamples? = null,
    seed: Long = 7,
    /** echte Projektoraufnahme als Schleife (48 kHz) statt Klangerzeuger */
    private val recording: ShortArray? = null,
    /** gemessener Klicktakt der Aufnahme, 0 = unbekannt */
    recRate: Float = 0f,
    recConfidence: Float = 0f,
) {
    // Aufnahme: Abspielgeschwindigkeit so, dass der Takt zur Film-fps passt – nur sanft
    // (Oktavfehler der Taktmessung werden weggefaltet, max. ±33 %), bei unsicherer Messung gar nicht
    private val recStep: Double = run {
        var ratio = if (recRate > 0f && recConfidence >= 0.15f) rate.toDouble() / recRate else 1.0
        while (ratio > 1.414) ratio /= 2
        while (ratio < 0.707) ratio *= 2
        ratio.coerceIn(0.75, 1.33) * 48000.0 / sr
    }
    private var recPos = 0.0
    // Aufnahme: Klang 0…+1 = wie aufgenommen, −1 = dumpf (Tiefpass bis 900 Hz)
    private val recDull = tone < -0.01f
    private val recLp1 = Biquad()
    private val recLp2 = Biquad()

    private val rnd = java.util.Random(seed)
    private var n = 0L
    private var nextStrike = 0.0
    private var strikeInFrame = 0
    private var burst = 0
    private var burstAmp = 0f

    // Metall: 8 Teiltöne; Plastik: 5 Hohlraum-Resonanzen
    private val metalModes = Array(8) { Biquad() }
    private val metalGain = FloatArray(8)
    private val plasticModes = Array(5) { Biquad() }
    private val plasticGain = FloatArray(5)
    private val softExc = Biquad()      // weicher Anschlag für Plastik
    private val body = Biquad()         // tiefer Körperschlag für Metall
    private var noiseEnv = 0f           // kurzer heller Rauschanteil (Metall)
    private val noiseDecay: Float

    private val toneLp1 = Biquad()
    private val toneLp2 = Biquad()
    private val whirrLp = Biquad()
    private var whinePhase = 0.0
    private var humPhase = 0.0

    // Eigene Aufnahmen: gerade laufende Anschläge
    private var sMetal: FloatArray? = null
    private var sPlastic: FloatArray? = null
    private var sPos = 0
    private var sAmp = 1f

    // Klassisch (erstes Rattern)
    private var classicEnv = 0f
    private val classicDecay = Math.exp(-1.0 / (sr * 0.004)).toFloat()
    private val classicLp = Biquad()

    init {
        val p = model.pitch.toDouble()
        val ring = model.ring.toDouble()
        // Güte aus gewünschter Abklingzeit (−20 dB): Q ≈ 1.364 · f · T
        fun q(f: Double, t: Double) = (1.364 * f * t).coerceAtLeast(0.7)
        val metalF = doubleArrayOf(1530.0, 2310.0, 2890.0, 3710.0, 4560.0, 5480.0, 6770.0, 7920.0)
        for (i in metalF.indices) {
            val f = metalF[i] * p
            val qq = q(f, 0.028 * ring * (1.0 - i * 0.05))   // ~21 ms wie die eigenen Metall-Aufnahmen
            metalModes[i].bandpass(sr, f, qq)
            // Energieausgleich (schmalbandig nimmt weniger auf) und zu hohen Tönen hin etwas leiser
            metalGain[i] = (Math.pow(qq, 0.8) * 0.9 * (1.0 - i * 0.06)).toFloat()
        }
        val plasticF = doubleArrayOf(180.0, 330.0, 520.0, 900.0, 1400.0)
        val plasticT = doubleArrayOf(0.030, 0.022, 0.016, 0.011, 0.008)
        val plasticA = doubleArrayOf(1.0, 1.0, 0.8, 0.45, 0.25)
        for (i in plasticF.indices) {
            val f = plasticF[i] * p
            val qq = q(f, plasticT[i] * ring)
            plasticModes[i].bandpass(sr, f, qq)
            plasticGain[i] = (Math.pow(qq, 0.8) * 1.6 * plasticA[i]).toFloat()
        }
        softExc.lowpass(sr, 1800.0)
        body.bandpass(sr, 140.0 * p, 2.5)
        noiseDecay = Math.exp(-1.0 / (sr * 0.006)).toFloat()
        // dumpf (−1): 900 Hz … klar (+1): 12 kHz, logarithmisch
        val cut = 900.0 * Math.pow(12000.0 / 900.0, (tone.coerceIn(-1f, 1f) + 1) / 2.0)
        toneLp1.lowpass(sr, cut)
        toneLp2.lowpass(sr, cut)
        whirrLp.lowpass(sr, 180.0 + 220.0 * (1 - material))
        classicLp.lowpass(sr, 260.0)
        val recCut = 900.0 * Math.pow(20000.0 / 900.0, (tone.coerceIn(-1f, 0f) + 1).toDouble())
        recLp1.lowpass(sr, recCut)
        recLp2.lowpass(sr, recCut)
    }

    /** Nächstes Sample (−1 … 1). */
    fun next(): Float {
        recording?.let { if (it.isNotEmpty()) return playRecording(it) }
        val t = n.toDouble() / sr
        val r = (rate * (1 + model.wobble * (0.6 * sin(2 * PI * 0.7 * t) + 0.4 * sin(2 * PI * 1.9 * t + 1)))).coerceAtLeast(1.0)
        val frame = sr / r
        var strike = false
        if (n >= nextStrike) {
            strike = true
            burst = (sr * 0.0012).toInt().coerceAtLeast(1)
            burstAmp = (if (strikeInFrame == 0) 1f else 0.6f) * (0.8f + 0.4f * rnd.nextFloat())
            strikeInFrame++
            val gap = if (model.strikes == 2 && strikeInFrame == 1) frame * model.strikeGap else
                frame * (if (model.strikes == 2) 1 - model.strikeGap else 1f)
            if (strikeInFrame >= model.strikes) strikeInFrame = 0
            nextStrike += gap * (1 + model.jitter * (rnd.nextFloat() - 0.5f) * 2)
        }
        n++

        if (model == ProjectorModel.KLASSISCH) return classic(t, r, strike)

        val m = material.coerceIn(0f, 1f)
        val click: Float
        val s = samples
        if (model == ProjectorModel.AUFNAHMEN && s != null && (s.metal.isNotEmpty() || s.plastic.isNotEmpty())) {
            if (strike) {
                sMetal = s.metal.randomOrNull(rnd)
                sPlastic = s.plastic.randomOrNull(rnd)
                sPos = 0
                sAmp = burstAmp
            }
            val a = sMetal?.let { if (sPos < it.size) it[sPos] else 0f } ?: 0f
            val b = sPlastic?.let { if (sPos < it.size) it[sPos] else 0f } ?: 0f
            click = (a * (1 - m) + b * m) * sAmp * 0.8f
            sPos++
        } else {
            var exc = 0f
            if (burst > 0) {
                exc = (rnd.nextFloat() * 2 - 1) * burstAmp
                burst--
            }
            // Metall: viele dichte Teiltöne + heller Rauschanteil + Körperschlag = "Chunk"
            var metal = 0f
            if (m < 1f) {
                for (i in metalModes.indices) metal += metalModes[i].run(exc) * metalGain[i]
                if (strike) noiseEnv = burstAmp
                metal = metal * 0.22f + (rnd.nextFloat() * 2 - 1) * noiseEnv * 0.35f + body.run(exc) * 2.2f
                noiseEnv *= noiseDecay
            }
            // Plastik: weicher Anschlag regt tiefe Hohlraum-Resonanzen an
            var plastic = 0f
            if (m > 0f) {
                val soft = softExc.run(exc) * 2f
                for (i in plasticModes.indices) plastic += plasticModes[i].run(soft) * plasticGain[i]
                plastic *= 0.3f
            }
            click = metal * (1 - m) + plastic * m
        }

        humPhase += 2 * PI * r / sr
        val hum = (model.hum * (sin(humPhase) + 0.4 * sin(2 * humPhase))).toFloat()
        val whirr = whirrLp.run(rnd.nextFloat() - 0.5f) * model.whirr * 1.6f
        whinePhase += 2 * PI * (r * 23.0) / sr
        val whine = (model.whine * 0.15 * sin(whinePhase) * (0.6 + 0.4 * sin(2 * PI * 0.3 * t))).toFloat()

        val y = toneLp2.run(toneLp1.run(click + hum * 0.5f + whirr + whine))
        return (y * volume * 0.9f).coerceIn(-1f, 1f)
    }

    private fun playRecording(pcm: ShortArray): Float {
        val i = recPos.toInt()
        val fr = (recPos - i).toFloat()
        val x = (pcm[i % pcm.size] * (1 - fr) + pcm[(i + 1) % pcm.size] * fr) / 32768f
        recPos += recStep
        if (recPos >= pcm.size) recPos -= pcm.size
        // Aufnahmen liegen bei −20 dBFS; Lautstärke wie beim Klangerzeuger, Klang-Filter gilt auch hier
        val y = if (recDull) recLp2.run(recLp1.run(x * 2.2f)) else x * 2.2f
        return (y * volume).coerceIn(-1f, 1f)
    }

    /** Das erste Rattern aus Phase 3, nur mit Klang-Filter und Lautstärke. */
    private fun classic(t: Double, r: Double, strike: Boolean): Float {
        if (strike) classicEnv = 1f
        val click = classicEnv * (rnd.nextFloat() - 0.5f) * 1.2f
        classicEnv *= classicDecay
        val hum = (0.10 * sin(2 * PI * r * t) + 0.05 * sin(2 * PI * 2 * r * t)).toFloat()
        val whirr = classicLp.run(rnd.nextFloat() - 0.5f) * 0.8f
        val y = toneLp2.run(toneLp1.run((click * 0.55f + hum * 0.5f + whirr) * 0.6f))
        return (y * volume * 1.4f).coerceIn(-1f, 1f)
    }

    private fun <T> List<T>.randomOrNull(r: java.util.Random) = if (isEmpty()) null else this[r.nextInt(size)]

    /** RBJ-Biquad (Tief- und Bandpass). */
    private class Biquad {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        fun bandpass(sr: Int, f: Double, q: Double) {
            val w = 2 * PI * f.coerceIn(40.0, sr * 0.45) / sr
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            b0 = alpha / a0; b1 = 0.0; b2 = -alpha / a0
            a1 = -2 * cos(w) / a0; a2 = (1 - alpha) / a0
        }

        fun lowpass(sr: Int, f: Double) {
            val w = 2 * PI * f.coerceIn(40.0, sr * 0.45) / sr
            val alpha = sin(w) / (2 * 0.7071)
            val cw = cos(w)
            val a0 = 1 + alpha
            b0 = (1 - cw) / 2 / a0; b1 = (1 - cw) / a0; b2 = (1 - cw) / 2 / a0
            a1 = -2 * cw / a0; a2 = (1 - alpha) / a0
        }

        fun run(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
            return y.toFloat()
        }
    }

    companion object {
        /** Anschlagtempo für einen Look: an die Film-fps gebunden oder frei eingestellt. */
        fun rateFor(look: FilmLook): Float =
            if (look.projectorSyncFps) (look.targetFps ?: 24f) else look.projectorSpeed

        fun forLook(sr: Int, look: FilmLook, samples: ClickSamples? = null): ProjectorSynth {
            val rec = look.projectorRecording?.let { com.volkskamera.app.data.SoundLibrary.cached(it) }
            val e = com.volkskamera.app.data.SoundLibrary.peek(look.projectorRecording)
            return ProjectorSynth(
                sr, look.projectorModel, rateFor(look), look.projectorMaterial, look.projectorTone, look.projectorVolume, samples,
                recording = rec, recRate = e?.rate ?: 0f, recConfidence = e?.confidence ?: 0f,
            )
        }
    }
}
