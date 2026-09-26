package com.volkskamera.app.render

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread

/**
 * Vorschau des Projektor-Ratterns in den Einstellungen: spielt den Klangerzeuger live ab.
 * [update] tauscht die Einstellungen im laufenden Betrieb, jede Reglerbewegung ist sofort hörbar.
 */
class ProjectorPreview(
    /** Erzeugt den Klang für einen Look (Projektor oder Hintergrund); liefert je Aufruf ein Sample. */
    private val make: (sr: Int, look: FilmLook) -> (() -> Float),
) {
    @Volatile private var look: FilmLook? = null
    @Volatile private var changed = false
    @Volatile private var running = false
    private var worker: Thread? = null

    val isPlaying get() = running

    fun update(l: FilmLook) {
        look = l
        changed = true
    }

    fun start(l: FilmLook) {
        update(l)
        if (running) return
        running = true
        worker = thread(name = "ProjektorVorschau", isDaemon = true) {
            val sr = 48000
            val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minBuf, sr / 10 * 2))   // ~100 ms: Regler reagieren schnell
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            var synth: (() -> Float)? = null
            val buf = ShortArray(sr / 50)   // 20-ms-Blöcke
            try {
                while (running) {
                    if (changed || synth == null) {
                        changed = false
                        synth = look?.let { make(sr, it) }
                    }
                    val s = synth ?: break
                    for (i in buf.indices) buf[i] = (s() * 32767f).toInt().toShort()
                    track.write(buf, 0, buf.size)
                }
            } finally {
                track.stop()
                track.release()
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(300)
        worker = null
    }
}
