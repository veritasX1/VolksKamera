package com.volkskamera.app.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Knacken wie auf alten Filmkopien – unabhängig vom Regler "Alterung":
 *  - feines Knistern (Staub auf der Tonspur),
 *  - einzelne kräftige Knackser (Schmutz, Kratzer),
 *  - dumpfe "Plopps" an Klebestellen,
 *  - kurze Knister-Schauer, wenn ein Kratzer durch die Tonspur läuft.
 * [amount] = Stärke, [density] = Häufigkeit, beide 0..1.
 */
class CrackleSynth(private val sr: Int, private val amount: Float, private val density: Float, seed: Long = 31) {
    private val rnd = java.util.Random(seed)
    private val a = amount.coerceIn(0f, 1f).toDouble()
    private val d = density.coerceIn(0f, 1f).toDouble()

    private var fine = 0.0
    private var pop = 0.0
    private var thumpLeft = 0
    private var thumpLen = 1
    private var thumpAmp = 0.0
    private var burstLeft = 0
    private val hp = Bq(sr, 250.0, false)
    private val lp = Bq(sr, 6500.0, true)

    fun next(): Float {
        // Schauer: für 80–300 ms deutlich dichteres Knistern
        if (burstLeft <= 0 && rnd.nextDouble() < d * 0.25 / sr) burstLeft = (sr * (0.08 + 0.22 * rnd.nextDouble())).toInt()
        val burst = burstLeft > 0
        if (burst) burstLeft--

        val fineRate = 20 + d * 180 + (if (burst) 900 else 0)
        if (rnd.nextDouble() < fineRate / sr) {
            val r = rnd.nextDouble()
            fine += (0.02 + 0.1 * r * r * r) * sign()
        }
        if (rnd.nextDouble() < (0.15 + d * 2.5) / sr) {
            pop += (0.25 + 0.5 * rnd.nextDouble()) * sign()
            // jeder vierte Knackser mit dumpfem Plopp (Klebestelle)
            if (rnd.nextDouble() < 0.25) {
                thumpLen = (sr / (60 + 50 * rnd.nextDouble())).toInt()
                thumpLeft = thumpLen
                thumpAmp = 0.35 * sign()
            }
        }
        var y = fine + pop
        fine *= 0.35
        pop *= 0.9
        y = lp.run(hp.run(y))
        if (thumpLeft > 0) {
            y += thumpAmp * sin(PI * (thumpLen - thumpLeft) / thumpLen)
            thumpLeft--
        }
        return (y * a).toFloat().coerceIn(-1f, 1f)
    }

    private fun sign() = if (rnd.nextBoolean()) 1.0 else -1.0

    private class Bq(sr: Int, f: Double, low: Boolean) {
        private val b0: Double; private val b1: Double; private val b2: Double; private val a1: Double; private val a2: Double
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        init {
            val w = 2 * PI * f / sr
            val al = sin(w) / (2 * 0.7071)
            val c = cos(w)
            val a0 = 1 + al
            if (low) { b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = (1 - c) / 2 / a0 } else { b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = (1 + c) / 2 / a0 }
            a1 = -2 * c / a0; a2 = (1 - al) / a0
        }
        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    companion object {
        fun forLook(sr: Int, look: FilmLook) = CrackleSynth(sr, look.crackleAmount, look.crackleDensity)
    }
}
