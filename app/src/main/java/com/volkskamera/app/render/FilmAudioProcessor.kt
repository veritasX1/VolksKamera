package com.volkskamera.app.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

enum class AudioMode { STUMM, ORIGINAL, ALT }

/**
 * Ton des Films: Stumm / Original / "alte Aufnahme", optional mit Projektor-Rattern.
 * Alles synthetisch – keine fremden Tonaufnahmen.
 *
 * Alte Aufnahme (Regler "Alterung" 0..1 steuert alles gemeinsam):
 *   Mono -> Gleichlaufschwankungen (Wow & Flutter, modulierte Verzögerung) -> Bandpass
 *   (dumpf, wenig Bass) -> weiche Sättigung -> Rauschen + Knistern
 * Projektor: Klick genau im Filmtakt (18 fps = 18 Klicks/s), Motorbrummen, Laufgeräusch.
 */
@UnstableApi
class FilmAudioProcessor(
    private val mode: AudioMode,
    private val aging: Float,
    /** Look mit den Projektor-Einstellungen; null = kein Projektor */
    private val projectorLook: FilmLook?,
    /** Look mit Hintergrundgeräuschen; null = keine */
    private val backgroundLook: FilmLook? = null,
    private val samples: ClickSamples? = null,
    /** Countdown-Vorspann: kurzer 1-kHz-Piepton zu jeder vollen Sekunde (jede Zahl) */
    private val countdownBeeps: Boolean = false,
    /** Look mit Mikrofonprofil (Aufnahmekette der Zeit); null = keins. Nicht bei Stumm. */
    private val micLook: FilmLook? = null,
    /** Ambient-Regler und Effekte dieses Stücks (neu erzeugt bei jedem Zurücksetzen) */
    private val soundMix: ((sr: Int) -> SoundMix)? = null,
) : BaseAudioProcessor() {

    private var sr = 48000
    private var ch = 2
    private var n = 0L   // Sample-Zähler (Zeit)
    private val rnd = java.util.Random(42)

    // Alte Aufnahme
    private var delayBuf = FloatArray(1)
    private var delayPos = 0
    private val hp = Biquad()
    private val lp1 = Biquad()
    private val lp2 = Biquad()
    private var crackle = 0f
    private var hiss = 0f

    // Projektor (gemeinsamer Klangerzeuger mit der Vorschau in den Einstellungen)
    private var projector: ProjectorSynth? = null
    private var background: BackgroundSynth? = null
    private var mic: MicChain? = null
    private var sound: SoundMix? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        setup(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    private fun setup(sampleRate: Int, channels: Int) {
        sr = sampleRate
        ch = channels
        delayBuf = FloatArray((sr * 0.06).toInt())   // bis 60 ms Verzögerung
        val a = aging.coerceIn(0f, 1f)
        hp.highpass(sr, 180.0 + 220.0 * a)
        lp1.lowpass(sr, 5200.0 - 2400.0 * a)
        lp2.lowpass(sr, 5200.0 - 2400.0 * a)
        projector = projectorLook?.let { ProjectorSynth.forLook(sr, it, samples) }
        background = backgroundLook?.let { BackgroundSynth(sr, it) }
        mic = micLook?.let { MicChain(sr, it) }
        sound = soundMix?.invoke(sr)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val out = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frames = bytes / (2 * ch)
        val samples = FloatArray(ch)
        for (f in 0 until frames) {
            for (c in 0 until ch) samples[c] = input.short / 32768f
            val y = frame(samples)
            for (c in 0 until ch) {
                val v = ((if (y.isNaN()) samples[c] else y) + mix).coerceIn(-1f, 1f)
                out.putShort((v * 32767f).toInt().toShort())
            }
        }
        out.flip()
    }

    /** Dazugemischte Geräusche des letzten [frame]. */
    private var mix = 0f

    /** Ein Zeitpunkt: neuer Ton (NaN = Original unverändert je Kanal); Geräusche landen in [mix]. */
    private fun frame(samples: FloatArray): Float {
        val m = mic
        val snd = sound
        var pre = 0f
        var post = 0f
        if (snd != null) { snd.next(); pre = snd.pre; post = snd.post }
        // "beeinflusst" (pre) läuft mit durch Mikrofon und Alterung – sofern es die gibt
        var preUsed = true
        val y = when (mode) {
            AudioMode.ORIGINAL -> if (m != null) m.run(samples.average().toFloat() + pre) else { preUsed = false; Float.NaN }
            AudioMode.STUMM -> { preUsed = false; 0f }
            AudioMode.ALT -> { val x = samples.average().toFloat() + pre; aged(if (m != null) m.run(x) else x) }
        }
        var pj = (projector?.next() ?: 0f) + (background?.next() ?: 0f) + post + (if (preUsed) 0f else pre)
        if (countdownBeeps) pj += beep()
        mix = pj
        n++
        return y
    }

    // ---------- Vorschau in den Einstellungen (ohne Media3) ----------

    private val one = FloatArray(1)

    /** Für die Tonprobe: mono, [sampleRate]. Danach [previewSample] je Sample. */
    fun preparePreview(sampleRate: Int) {
        setup(sampleRate, 1)
        onReset()
    }

    fun previewSample(x: Float): Float {
        one[0] = x
        val y = frame(one)
        return ((if (y.isNaN()) x else y) + mix).coerceIn(-1f, 1f)
    }

    private fun aged(x: Float): Float {
        val a = aging.coerceIn(0f, 1f)
        val t = n.toDouble() / sr
        // Wow (langsam, ~0,5 Hz) und Flutter (schnell, ~6 Hz): schwankende Verzögerung = schwankende Tonhöhe
        delayBuf[delayPos] = x
        val delayS = sr * (0.012 + a * (0.0022 * sin(2 * PI * 0.55 * t) + 0.0004 * sin(2 * PI * 6.3 * t)))
        var rp = delayPos - delayS
        while (rp < 0) rp += delayBuf.size
        val i0 = rp.toInt() % delayBuf.size
        val fr = (rp - rp.toInt()).toFloat()
        val d = delayBuf[i0] * (1 - fr) + delayBuf[(i0 + 1) % delayBuf.size] * fr
        delayPos = (delayPos + 1) % delayBuf.size

        // Rauschen VOR dem Filter dazu: klingt dann dumpf wie auf altem Material, nicht hell-digital
        hiss = hiss * 0.6f + (rnd.nextFloat() - 0.5f) * 0.4f
        // dumpf: Bässe und Höhen weg
        var y = lp2.run(lp1.run(hp.run(d + hiss * 0.12f * a)))
        // weiche Sättigung (altes Band/Röhre)
        val drive = 1f + 3f * a
        y = (tanh((y * drive).toDouble()) / tanh(drive.toDouble())).toFloat()
        // Knistern: einzelne abklingende Knackser
        if (rnd.nextFloat() < a * 9f / sr) crackle = (0.1f + 0.3f * rnd.nextFloat()) * (if (rnd.nextBoolean()) 1 else -1)
        val cr = crackle
        crackle *= 0.85f
        return y * (1f - 0.15f * a) + cr * a
    }

    private fun beep(): Float {
        val inSec = (n % sr).toDouble() / sr
        if (inSec > 0.08) return 0f
        val fade = (minOf(inSec, 0.08 - inSec) / 0.005).coerceAtMost(1.0)
        return (0.35 * fade * sin(2 * PI * 1000.0 * inSec)).toFloat()
    }

    override fun onReset() {
        n = 0; delayPos = 0; crackle = 0f; hiss = 0f
        delayBuf.fill(0f)
        hp.clear(); lp1.clear(); lp2.clear()
        projector = projectorLook?.let { ProjectorSynth.forLook(sr, it, samples) }
        background = backgroundLook?.let { BackgroundSynth(sr, it) }
        mic = micLook?.let { MicChain(sr, it) }
        sound = soundMix?.invoke(sr)
    }

    /** RBJ-Biquad-Filter (Hoch-/Tiefpass, Güte 0,707). */
    private class Biquad {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        fun lowpass(sr: Int, f: Double) = set(sr, f, low = true)
        fun highpass(sr: Int, f: Double) = set(sr, f, low = false)

        private fun set(sr: Int, f: Double, low: Boolean) {
            val w = 2 * PI * f.coerceIn(20.0, sr * 0.45) / sr
            val alpha = sin(w) / (2 * 0.7071)
            val cw = cos(w)
            val a0 = 1 + alpha
            if (low) { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2 } else { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2 }
            a1 = -2 * cw; a2 = 1 - alpha
            b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
        }

        fun run(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
            return y.toFloat()
        }

        fun clear() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }
    }

    companion object {
        /** Ton des Countdowns: Pieptöne, dazu der Projektor, falls eingeschaltet. */
        fun forCountdown(look: FilmLook, samples: ClickSamples? = null) = FilmAudioProcessor(
            AudioMode.STUMM, 0f, look.takeIf { it.projector }, countdownBeeps = true,
            backgroundLook = look.takeIf { it.hasBackground }, samples = samples,
        )

        /**
         * null, wenn der Ton unverändert bleiben kann (Original ohne Mikrofon, Projektor, Hintergrund,
         * Ambient und Effekte). [cues]/[offsetMs]: Mitschrift der Aufnahme und Beginn dieses Stücks.
         */
        fun forLook(look: FilmLook, samples: ClickSamples? = null, cues: SoundCues? = null, offsetMs: Long = 0): FilmAudioProcessor? {
            val hasMix = !SoundMix(48000, look, cues, offsetMs).isEmpty
            if (look.audioMode == AudioMode.ORIGINAL && look.mic == null && !look.projector && !look.hasBackground && !hasMix) return null
            return always(look, samples, cues, offsetMs)
        }

        /** Wie [forLook], aber immer ein Prozessor (Tonprobe in den Einstellungen). */
        fun always(look: FilmLook, samples: ClickSamples? = null, cues: SoundCues? = null, offsetMs: Long = 0) = FilmAudioProcessor(
            look.audioMode, look.aging, look.takeIf { it.projector },
            look.takeIf { it.hasBackground }, samples = samples, micLook = look.takeIf { it.mic != null },
            soundMix = { sr -> SoundMix(sr, look, cues, offsetMs) },
        )
    }
}
