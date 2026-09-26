package com.volkskamera.app.render

import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * Keine Prüfung, sondern Messmaterial: schickt Testsignale durch jedes Mikrofonprofil und das
 * Knacken und schreibt rohe float32-Dateien (48 kHz mono) nach $VOLKSKAMERA_MESSUNG.
 * Auswertung am PC (Frequenzgang, Klirrfaktor, Pumpen). Ohne die Variable passiert nichts.
 */
class MicChainMessung {
    private val sr = 48000

    private fun write(f: File, x: FloatArray) = DataOutputStream(FileOutputStream(f).buffered()).use { o ->
        val b = java.nio.ByteBuffer.allocate(x.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        x.forEach { b.putFloat(it) }
        o.write(b.array())
    }

    @Test
    fun messen() {
        val dir = System.getenv("VOLKSKAMERA_MESSUNG")?.let(::File) ?: return
        dir.mkdirs()
        val rnd = java.util.Random(1)
        val noise = FloatArray(sr * 4) { (rnd.nextGaussian() * 0.1).toFloat() }
        val sine = FloatArray(sr * 2) { (0.1 * sin(2 * PI * 1000.0 * it / sr)).toFloat() }
        // Pumpen: 1 s Ton, 2 s Stille, 1 s Ton
        val pump = FloatArray(sr * 4) { i -> if (i < sr || i >= 3 * sr) (0.1 * sin(2 * PI * 440.0 * i / sr)).toFloat() else 0f }
        for (p in MicProfile.entries) {
            val look = FilmLook().withMic(p)
            for ((name, sig) in listOf("rauschen" to noise, "sinus" to sine, "pumpen" to pump)) {
                val m = MicChain(sr, look)
                write(File(dir, "${p.name}_$name.f32"), FloatArray(sig.size) { m.run(sig[it]) })
            }
        }
        for ((a, d) in listOf(0.5f to 0.2f, 0.5f to 0.8f, 1f to 0.5f)) {
            val c = CrackleSynth(sr, a, d)
            write(File(dir, "knacken_${a}_$d.f32"), FloatArray(sr * 10) { c.next() })
        }
    }
}
