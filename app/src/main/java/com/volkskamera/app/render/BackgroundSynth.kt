package com.volkskamera.app.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Rauscharten: von tief (Braun) bis hell (Violett), dazu Bandrauschen wie von altem Tonband. */
enum class NoiseType(val label: String) {
    WEISS("Weiß"), ROSA("Rosa"), BRAUN("Braun"), BLAU("Blau"), VIOLETT("Violett"), BAND("Bandrauschen")
}

/**
 * Brumm- und Tonarten: von mechanisch (Maschine) bis digital (reiner Ton).
 * [cable] = Störungen der Tonleitung (Kabel, Masse, Einstreuung) – im Mikrofon-Bildschirm wählbar.
 */
enum class HumType(val label: String, val cable: Boolean = false, val hint: String = "") {
    MASCHINE("Maschinenbrummen"),
    NETZ("Brummschleife (Masse)", true, "Ruhiges Netzbrummen mit Obertönen: zwei Geräte an verschiedenen Steckdosen, Masse doppelt verbunden."),
    WACKEL("Wackelkontakt", true, "Masse nicht richtig verbunden: lautes Schnarren, das mit Knacksern ein- und aussetzt."),
    HANDY("Handy-Einstreuung (GSM)", true, "Das „dit-dit-dit“ eines Mobiltelefons neben Kabel oder Verstärker – 217 Hz Sendetakt."),
    DIMMER("Dimmer / Trafo-Einstreuung", true, "Unabgeschirmtes Kabel neben Dimmer oder Trafo: scharfes Schnarren mit doppelter Netzfrequenz."),
    SUMMEN("Elektrisches Summen", true, "Leuchtstoffröhre oder Trafo in der Nähe: helles, obertonreiches Summen."),
    PIEZO("Piezo-Piepton"),
    SINUS("Digitaler Ton"),
}

/**
 * Hintergrundgeräusche für den Filmton: Rauschen und Brummen/Ton mit frei wählbarer Frequenz
 * (20 Hz Wummern … 8 kHz Piepen). Wird im Export zum Ton gemischt und in den Einstellungen
 * als Vorschau abgespielt.
 */
class BackgroundSynth(private val sr: Int, look: FilmLook) {
    private val noiseType = look.bgNoiseType.takeIf { look.bgNoiseOn }
    private val noiseLevel = look.bgNoiseLevel
    private val humType = look.bgHumType.takeIf { look.bgHumOn }
    private val humFreq = look.bgHumFreq.toDouble().coerceIn(20.0, 8000.0)
    private val humLevel = look.bgHumLevel
    private val rnd = java.util.Random(11)
    private val crackle = if (look.crackleOn) CrackleSynth.forLook(sr, look) else null

    // Rosa nach Paul Kellet, Braun als "undichtes" Integral
    private var p0 = 0.0; private var p1 = 0.0; private var p2 = 0.0; private var p3 = 0.0
    private var p4 = 0.0; private var p5 = 0.0; private var p6 = 0.0
    private var brown = 0.0
    private var lastWhite = 0.0
    private var lastPink = 0.0
    private val bandHp = Filter().apply { highpass(sr, 3000.0) }
    private val bandLp = Filter().apply { lowpass(sr, 12000.0) }

    private var phase = 0.0
    private var jitter = 0.0
    private var n = 0L
    private val humLp = Filter().apply { lowpass(sr, 2200.0) }
    private val gsmHp = Filter().apply { highpass(sr, 150.0) }
    private val gsmLp = Filter().apply { lowpass(sr, 5000.0) }
    // Wackelkontakt: Kontakt offen/geschlossen, Übergänge prasseln
    private var contactOpen = false
    private var contactLeft = sr
    private var chatter = 0
    private var contactGain = 0.0
    private var pop = 0.0
    // Handy: Sende-Episoden aus kurzen Paketen
    private var gsmOnLeft = 0
    private var gsmOffLeft = sr
    private var gsmEpisodeLeft = 0
    private var gsmPhase = 0.0

    fun next(): Float {
        var y = 0.0
        noiseType?.let { y += noise(it) * noiseLevel * 0.5 }
        humType?.let { y += hum(it) * humLevel * 0.5 }
        crackle?.let { y += it.next() }
        n++
        return y.toFloat().coerceIn(-1f, 1f)
    }

    private fun noise(t: NoiseType): Double {
        val w = rnd.nextDouble() * 2 - 1
        p0 = 0.99886 * p0 + w * 0.0555179; p1 = 0.99332 * p1 + w * 0.0750759
        p2 = 0.96900 * p2 + w * 0.1538520; p3 = 0.86650 * p3 + w * 0.3104856
        p4 = 0.55000 * p4 + w * 0.5329522; p5 = -0.7616 * p5 - w * 0.0168980
        val pink = (p0 + p1 + p2 + p3 + p4 + p5 + p6 + w * 0.5362) * 0.11
        p6 = w * 0.115926
        brown = (brown + 0.02 * w) / 1.02
        val out = when (t) {
            NoiseType.WEISS -> w * 0.5
            NoiseType.ROSA -> pink
            NoiseType.BRAUN -> brown * 3.5
            NoiseType.BLAU -> (pink - lastPink) * 1.6
            NoiseType.VIOLETT -> (w - lastWhite) * 0.35
            NoiseType.BAND -> bandLp.run(bandHp.run(w)) * 0.9
        }
        lastWhite = w
        lastPink = pink
        return out
    }

    private fun hum(t: HumType): Double {
        val time = n.toDouble() / sr
        // Maschine: Drehzahl schwankt leicht (Zufallsweg), dazu langsames Pulsieren
        var f = humFreq
        if (t == HumType.MASCHINE) {
            jitter = (jitter + (rnd.nextDouble() - 0.5) * 0.0004).coerceIn(-0.015, 0.015)
            f *= 1 + jitter
        }
        phase += 2 * PI * f / sr
        if (phase > 2 * PI * 1000) phase -= 2 * PI * 1000
        val nyq = sr * 0.45
        fun harm(k: Int, a: Double) = if (k * f < nyq) a * sin(k * phase) else 0.0
        return when (t) {
            // Sägezahn-artig, obertonreich, gedämpft, pulsierend = Motor/Maschine
            HumType.MASCHINE -> {
                var s = 0.0
                for (k in 1..8) s += harm(k, 1.0 / k)
                humLp.run(s) * (0.75 + 0.25 * sin(2 * PI * 2.7 * time)) * 0.8
            }
            // Netzbrummen: Grundton mit ungeraden Obertönen, ruhig
            HumType.NETZ -> harm(1, 1.0) + harm(3, 0.45) + harm(5, 0.2) + harm(7, 0.08)
            // Summen: viele Obertöne (rechteckähnlich), leicht moduliert – Trafo/Leuchtstoffröhre
            HumType.SUMMEN -> {
                var s = 0.0
                var k = 1
                while (k <= 25) { s += harm(k, 1.0 / k); k += 2 }
                s * 0.9 * (0.9 + 0.1 * sin(2 * phase))
            }
            // Piezo: harte Rechteckwelle, hell
            HumType.PIEZO -> {
                var s = 0.0
                var k = 1
                while (k <= 15) { s += harm(k, 4 / (PI * k)); k += 2 }
                s * 0.6
            }
            HumType.SINUS -> harm(1, 1.0)
            HumType.WACKEL -> {
                // offene Masse: obertonreiches Schnarren (Einweg-gleichgerichtetes Netz)
                var s = 0.0
                for (k in 1..24) s += harm(k, (if (k % 2 == 1) 1.0 else 0.6) / k)
                if (--contactLeft <= 0) {
                    contactOpen = !contactOpen
                    contactLeft = (sr * (if (contactOpen) 0.3 + 1.5 * rnd.nextDouble() else 0.5 + 2.5 * rnd.nextDouble())).toInt()
                    chatter = (sr * (0.03 + 0.08 * rnd.nextDouble())).toInt()
                    pop = (0.4 + 0.5 * rnd.nextDouble()) * (if (rnd.nextBoolean()) 1 else -1)
                }
                // beim Umschalten flattert der Kontakt kurz
                val target = if (chatter > 0) { chatter--; if (rnd.nextDouble() < 0.02) (if (contactGain > 0.5) 0.0 else 1.0) else contactGain }
                    else if (contactOpen) 1.0 else 0.06
                contactGain += (target - contactGain) * 0.02
                if (chatter > 0 && rnd.nextDouble() < 0.004) pop = (rnd.nextDouble() - 0.5) * 0.8
                val p = pop; pop *= 0.93
                s * 0.7 * contactGain + p
            }
            HumType.HANDY -> {
                // GSM: 577-µs-Pakete im 217-Hz-Takt, in Episoden („dit-dit-dit … dididit“)
                if (gsmEpisodeLeft <= 0 && gsmOnLeft <= 0) {
                    if (--gsmOffLeft <= 0) { gsmEpisodeLeft = 2 + rnd.nextInt(6); gsmOffLeft = (sr * (1.5 + 4 * rnd.nextDouble())).toInt() }
                }
                if (gsmOnLeft <= 0 && gsmEpisodeLeft > 0) {
                    // Pause zwischen den „dits“, dann nächstes Paket
                    if (rnd.nextDouble() < 1.0 / (sr * 0.12)) {
                        gsmEpisodeLeft--
                        gsmOnLeft = (sr * (0.06 + 0.25 * rnd.nextDouble())).toInt()
                    }
                }
                val on = gsmOnLeft > 0
                if (on) gsmOnLeft--
                gsmPhase += 217.0 / sr
                if (gsmPhase >= 1) gsmPhase -= 1
                val pulse = if (on && gsmPhase < 0.125) 1.0 else 0.0
                gsmLp.run(gsmHp.run(pulse)) * 2.2
            }
            HumType.DIMMER -> {
                // Phasenanschnitt: harte Spitzen mit doppelter Netzfrequenz, schnell abklingend
                val ph2 = (2 * phase / (2 * PI)) % 1.0
                val spike = if (ph2 < 0.35) 0.0 else kotlin.math.exp(-(ph2 - 0.35) * 40) * (if ((phase / PI).toInt() % 2 == 0) 1 else -1)
                humLp.run(spike) * 1.6 + harm(2, 0.25)
            }
        }
    }

    private class Filter {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun lowpass(sr: Int, f: Double) = set(sr, f, true)
        fun highpass(sr: Int, f: Double) = set(sr, f, false)
        private fun set(sr: Int, f: Double, low: Boolean) {
            val w = 2 * PI * f.coerceIn(20.0, sr * 0.45) / sr
            val alpha = sin(w) / (2 * 0.7071)
            val cw = cos(w)
            val a0 = 1 + alpha
            if (low) { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2 } else { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2 }
            a1 = -2 * cw; a2 = 1 - alpha
            b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
        }
        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }
}
