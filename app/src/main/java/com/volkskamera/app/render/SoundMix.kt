package com.volkskamera.app.render

import com.volkskamera.app.data.SoundLibrary

/** Beim Filmen mitgeschrieben: Effekt-Taste und Stellungen der beiden Ambient-Regler (Zeit = Aufnahmezeit). */
data class SoundCues(
    val fx: List<Cue> = emptyList(),
    val a: List<Level> = emptyList(),
    val b: List<Level> = emptyList(),
) {
    data class Cue(val ms: Long, val id: String)
    data class Level(val ms: Long, val value: Float)

    val isEmpty get() = fx.isEmpty() && a.isEmpty() && b.isEmpty()
    val soundIds get() = fx.map { it.id }.distinct()
}

/**
 * Mischt die beiden Ambient-Regler und die Effekte eines Stücks. Pro Sample liefert [next] zwei
 * Anteile: [pre] läuft danach durch Mikrofon & Alterung, [post] kommt unverändert dazu – je nach
 * Einstellung "von den Toneinstellungen beeinflusst".
 *
 * Ohne mitgeschriebene Reglerbewegungen gilt die Reglerstellung aus dem Look (z.B. beim Entwickeln
 * eines vorhandenen Videos). [offsetMs] = Beginn dieses Stücks in der Aufnahmezeit.
 */
class SoundMix(private val sr: Int, look: FilmLook, cues: SoundCues?, private val offsetMs: Long = 0) {
    private val slots = listOfNotNull(
        look.ambA?.let { Slot(sr, it, look.ambALevel, cues?.a.orEmpty(), look.ambAChain) },
        look.ambB?.let { Slot(sr, it, look.ambBLevel, cues?.b.orEmpty(), look.ambBChain) },
    )
    private val fxChain = look.fxChain
    private val fxGain = look.fxVolume
    private val step = SoundLibrary.SR.toDouble() / sr

    /** Effekte dieses Stücks: Startsample (kann negativ sein, dann läuft ein früherer Effekt noch) + Ton. */
    private val voices = cues?.fx.orEmpty().mapNotNull { c ->
        val pcm = SoundLibrary.cached(c.id) ?: return@mapNotNull null
        Voice((c.ms - offsetMs) * sr / 1000, pcm)
    }.filter { it.start + (it.pcm.size / step).toLong() > 0 }.sortedBy { it.start }

    private var n = 0L
    var pre = 0f; private set
    var post = 0f; private set

    val isEmpty get() = slots.isEmpty() && voices.isEmpty()

    fun next() {
        var p = 0f
        var q = 0f
        val ms = offsetMs + n * 1000 / sr
        for (s in slots) {
            val v = s.next(ms)
            if (s.chain) p += v else q += v
        }
        var fx = 0f
        for (v in voices) {
            if (v.start > n) break
            val pos = (n - v.start) * step
            val i = pos.toInt()
            if (i + 1 < v.pcm.size) {
                val fr = (pos - i).toFloat()
                fx += (v.pcm[i] * (1 - fr) + v.pcm[i + 1] * fr) / 32768f
            }
        }
        fx *= fxGain
        if (fxChain) p += fx else q += fx
        pre = p
        post = q
        n++
    }

    private class Voice(val start: Long, val pcm: ShortArray)

    /** Ein Ambient-Regler: Schleife aus Datei oder erzeugtes Rauschen/Brummen, Pegel folgt den Reglerbewegungen. */
    private class Slot(private val sr: Int, id: String, private val level: Float, points: List<SoundCues.Level>, val chain: Boolean) {
        private val pts = points.sortedBy { it.ms }
        private var pi = 0
        private var gain = gainOf(if (pts.isNotEmpty()) pts.first().value else level)
        private val smooth = (1.0 / (sr * 0.03)).toFloat()   // 30 ms, Reglerbewegungen ohne Knacken
        private val pcm = SoundLibrary.cached(id)
        private val step = SoundLibrary.SR.toDouble() / sr
        private var pos = 0.0
        private val synth: BackgroundSynth? = if (pcm == null) SoundMix.synthFor(sr, id) else null

        fun next(ms: Long): Float {
            val target = if (pts.isEmpty()) gainOf(level) else {
                while (pi + 1 < pts.size && pts[pi + 1].ms <= ms) pi++
                gainOf(pts[pi].value)
            }
            gain += (target - gain) * smooth
            val raw = when {
                pcm != null && pcm.isNotEmpty() -> {
                    val i = pos.toInt()
                    val fr = (pos - i).toFloat()
                    val a = pcm[i % pcm.size]
                    val b = pcm[(i + 1) % pcm.size]
                    pos += step
                    if (pos >= pcm.size) pos -= pcm.size
                    (a * (1 - fr) + b * fr) / 32768f
                }
                synth != null -> synth.next()
                else -> 0f
            }
            return raw * gain
        }

        companion object {
            /** Regler 0..1 -> Verstärkung, gehörrichtig (quadratisch); ganz oben +6 dB über dem Normpegel. */
            fun gainOf(v: Float) = 2f * v.coerceIn(0f, 1f) * v.coerceIn(0f, 1f)
        }
    }

    companion object {
        fun synthFor(sr: Int, id: String): BackgroundSynth? {
            val p = id.split(':')
            if (p.size < 3 || p[0] != "s") return null
            return when (p[1]) {
                "noise" -> runCatching { NoiseType.valueOf(p[2]) }.getOrNull()?.let {
                    BackgroundSynth(sr, FilmLook(bgNoiseOn = true, bgNoiseType = it, bgNoiseLevel = 0.5f))
                }
                "hum" -> runCatching { HumType.valueOf(p[2]) }.getOrNull()?.let {
                    BackgroundSynth(sr, FilmLook(bgHumOn = true, bgHumType = it, bgHumLevel = 0.5f,
                        bgHumFreq = SoundLibrary.synthHumFreq[it] ?: 50f))
                }
                else -> null
            }
        }

        /**
         * Einzelner Ton zum Vorhören (bereits dekodiert bzw. erzeugt): Schleife oder einmal.
         * null, wenn der Ton noch nicht geladen ist.
         */
        fun generator(sr: Int, id: String, loop: Boolean): (() -> Float)? {
            val pcm = SoundLibrary.cached(id)
            if (pcm == null) {
                val s = synthFor(sr, id) ?: return null
                return { s.next() }
            }
            val step = SoundLibrary.SR.toDouble() / sr
            var pos = 0.0
            return {
                val i = pos.toInt()
                if (i >= pcm.size) 0f else {
                    val v = pcm[i] / 32768f
                    pos += step
                    if (loop && pos >= pcm.size) pos -= pcm.size
                    // Ambient/Projektor liegen leiser (−20…−24 dBFS) als Effekte (−1 dBFS)
                    if (loop) v * 2f else v * 0.8f
                }
            }
        }

        /** Alle Töne, die für diesen Look und diese Aufnahme vorab dekodiert werden müssen. */
        fun neededIds(look: FilmLook, cues: SoundCues?) =
            listOfNotNull(look.ambA, look.ambB, look.projectorRecording.takeIf { look.projector }) + cues?.soundIds.orEmpty()
    }
}
