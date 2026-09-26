package com.volkskamera.app.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.volkskamera.app.render.HumType
import com.volkskamera.app.render.NoiseType
import org.json.JSONArray
import org.json.JSONObject

enum class SoundKind { PROJEKTOR, AMBIENT, EFFEKT }

/**
 * Ein Geräusch: mitgeliefert (ton/…ogg aus sounds_vorbereiten.py), erzeugt (Rauschen/Brummen,
 * IDs "s:…") oder eine eigene Datei des Nutzers (mp3/wav, ID "datei:<uri>").
 */
data class SoundEntry(
    val id: String,
    val name: String,
    val group: String,
    val kind: SoundKind,
    /** Asset-Pfad (mitgeliefert) */
    val file: String? = null,
    /** Klicktakt der Projektoraufnahme pro Sekunde, 0 = unbekannt */
    val rate: Float = 0f,
    /** Verlässlichkeit der Taktmessung 0..1 */
    val confidence: Float = 0f,
) {
    val isSynth get() = id.startsWith("s:")
    val isFile get() = id.startsWith("datei:")
}

/**
 * Katalog aller Geräusche plus Zwischenspeicher der dekodierten Töne (48 kHz mono, 16 Bit).
 * [load] dekodiert (blockierend, im Hintergrund aufrufen), [cached] liefert nur Fertiges –
 * so kann der Tonprozessor während des Exports ohne Wartezeit darauf zugreifen.
 */
object SoundLibrary {
    const val SR = 48000
    private const val PREFS = "volkskamera"
    private const val KEY_CUSTOM = "eigene_sounds"
    private const val CACHE_LIMIT = 40L * 1024 * 1024   // Bytes dekodierter Töne im Speicher

    @Volatile private var builtIn: List<SoundEntry>? = null

    /** Frequenzen der erzeugten Brummtöne in der Auswahl der Regler. */
    val synthHumFreq = mapOf(HumType.MASCHINE to 40f, HumType.NETZ to 50f, HumType.SUMMEN to 120f,
        HumType.PIEZO to 3000f, HumType.SINUS to 1000f)

    fun builtIn(context: Context): List<SoundEntry> = builtIn ?: synchronized(this) {
        builtIn ?: run {
            val list = mutableListOf<SoundEntry>()
            runCatching {
                val o = context.assets.open("ton/sounds.json").bufferedReader().use { JSONObject(it.readText()) }
                fun arr(key: String, kind: SoundKind) {
                    val a = o.optJSONArray(key) ?: return
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        list += SoundEntry(e.getString("id"), e.getString("name"), e.optString("group", "Aufnahmen"), kind,
                            e.getString("file"), e.optDouble("rate", 0.0).toFloat(), e.optDouble("confidence", 0.0).toFloat())
                    }
                }
                arr("projektor", SoundKind.PROJEKTOR); arr("ambient", SoundKind.AMBIENT); arr("effekte", SoundKind.EFFEKT)
            }
            NoiseType.entries.forEach { list += SoundEntry("s:noise:${it.name}", "Rauschen: ${it.label}", "Erzeugt", SoundKind.AMBIENT) }
            HumType.entries.forEach {
                list += SoundEntry("s:hum:${it.name}", "${it.label} (%.0f Hz)".format(synthHumFreq[it]), "Erzeugt", SoundKind.AMBIENT)
            }
            list.also { builtIn = it }
        }
    }

    /** Eintrag ohne Context (nur mitgelieferte, nachdem der Katalog einmal geladen wurde). */
    fun peek(id: String?): SoundEntry? = id?.let { i -> builtIn?.firstOrNull { it.id == i } }

    // ---------- eigene Dateien ----------

    fun custom(context: Context): List<SoundEntry> {
        val a = runCatching { JSONArray(prefs(context).getString(KEY_CUSTOM, "[]")) }.getOrElse { JSONArray() }
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                SoundEntry(o.getString("id"), o.getString("name"), "Eigene Dateien", SoundKind.valueOf(o.getString("kind")))
            }.getOrNull()
        }
    }

    /** Eigene Datei aufnehmen (Zugriff bleibt dauerhaft erhalten); null, wenn sie sich nicht lesen lässt. */
    fun addCustom(context: Context, uri: Uri, kind: SoundKind): SoundEntry? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.substringBeforeLast('.') ?: "Eigene Datei"
        val e = SoundEntry("datei:$uri", name, "Eigene Dateien", kind)
        load(context, e.id) ?: return null     // gleich prüfen, ob es dekodierbar ist
        val rest = custom(context).filter { it.id != e.id || it.kind != kind }
        val a = JSONArray()
        (rest + e).forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind.name)) }
        prefs(context).edit().putString(KEY_CUSTOM, a.toString()).apply()
        return e
    }

    fun removeCustom(context: Context, id: String) {
        val a = JSONArray()
        custom(context).filter { it.id != id }.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind.name)) }
        prefs(context).edit().putString(KEY_CUSTOM, a.toString()).apply()
    }

    fun all(context: Context, kind: SoundKind) = builtIn(context).filter { it.kind == kind } + custom(context).filter { it.kind == kind }

    fun find(context: Context, id: String?): SoundEntry? =
        id?.let { i -> builtIn(context).firstOrNull { it.id == i } ?: custom(context).firstOrNull { it.id == i } }

    /** Anzeigename, auch wenn die Datei inzwischen aus der Liste entfernt wurde. */
    fun nameOf(context: Context, id: String?): String? = id?.let { find(context, it)?.name ?: if (it.startsWith("datei:")) "Eigene Datei" else it }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- dekodierte Töne ----------

    private val cache = object : LinkedHashMap<String, ShortArray>(16, 0.75f, true) {
        private var bytes = 0L
        override fun put(key: String, value: ShortArray): ShortArray? {
            val old = super.put(key, value)
            bytes += value.size * 2L - (old?.size ?: 0) * 2L
            val it = entries.iterator()
            while (bytes > CACHE_LIMIT && size > 1 && it.hasNext()) {
                val e = it.next()
                if (e.key == key) continue
                bytes -= e.value.size * 2L
                it.remove()
            }
            return old
        }
    }

    fun cached(id: String?): ShortArray? = id?.let { synchronized(cache) { cache[it] } }

    /** Dekodiert (einmal) und hält den Ton bereit. Erzeugte Geräusche ("s:…") haben keine Datei -> null. */
    fun load(context: Context, id: String?): ShortArray? {
        if (id == null || id.startsWith("s:")) return null
        cached(id)?.let { return it }
        val maxSec = if (find(context, id)?.kind == SoundKind.EFFEKT) 60 else 180
        val pcm = runCatching {
            val ex = MediaExtractor()
            try {
                if (id.startsWith("datei:")) ex.setDataSource(context, Uri.parse(id.removePrefix("datei:")), null)
                else {
                    val file = find(context, id)?.file ?: return null
                    context.assets.openFd(file).use { fd -> ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                }
                decode(ex, maxSec)
            } finally {
                ex.release()
            }
        }.getOrNull() ?: return null
        if (pcm.isEmpty()) return null
        if (id.startsWith("datei:")) normalize(pcm, custom(context).firstOrNull { it.id == id }?.kind ?: SoundKind.EFFEKT)
        synchronized(cache) { cache[id] = pcm }
        return pcm
    }

    /** Eigene Dateien auf den Pegel der mitgelieferten bringen: Effekte −1 dBFS Spitze, Schleifen −24/−20 dBFS RMS. */
    private fun normalize(pcm: ShortArray, kind: SoundKind) {
        var peak = 1
        var sum = 0.0
        for (v in pcm) { val a = kotlin.math.abs(v.toInt()); if (a > peak) peak = a; sum += v.toDouble() * v }
        val rms = kotlin.math.sqrt(sum / pcm.size).coerceAtLeast(1.0)
        val g = when (kind) {
            SoundKind.EFFEKT -> 0.89 * 32767 / peak
            SoundKind.AMBIENT -> 0.063 * 32767 / rms
            SoundKind.PROJEKTOR -> 0.1 * 32767 / rms
        }
        for (i in pcm.indices) pcm[i] = (pcm[i] * g).coerceIn(-32767.0, 32767.0).toInt().toShort()
    }

    /** Erste Tonspur -> 48 kHz mono. */
    private fun decode(ex: MediaExtractor, maxSec: Int): ShortArray {
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val mono = FloatArrayBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder())
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        if (floatPcm) {
                            val fb = buf.asFloatBuffer()
                            while (fb.remaining() >= ch) { var s = 0f; repeat(ch) { s += fb.get() }; mono.add(s / ch) }
                        } else {
                            val sb = buf.asShortBuffer()
                            while (sb.remaining() >= ch) { var s = 0f; repeat(ch) { s += sb.get() / 32768f }; mono.add(s / ch) }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (mono.size > maxSec.toLong() * sr) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        // auf 48 kHz (linear – für Geräusche genügt das)
        val src = mono.toArray()
        if (sr == SR) return ShortArray(src.size) { (src[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        val n = (src.size.toLong() * SR / sr).toInt()
        val step = sr.toDouble() / SR
        return ShortArray(n) { i ->
            val p = i * step
            val a = p.toInt().coerceAtMost(src.size - 1)
            val b = (a + 1).coerceAtMost(src.size - 1)
            val fr = (p - a).toFloat()
            ((src[a] * (1 - fr) + src[b] * fr).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }

    private class FloatArrayBuilder {
        private var a = FloatArray(1 shl 16)
        var size = 0; private set
        fun add(v: Float) {
            if (size == a.size) a = a.copyOf(a.size * 2)
            a[size++] = v
        }
        fun toArray() = a.copyOf(size)
    }
}
