package com.volkskamera.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.pow

/**
 * Sensor-Kalibrierung: gleichzeitig RAW (Sensor-Rohdaten) und JPEG (vom Handy bearbeitet) aufnehmen,
 * das RAW selbst neutral entwickeln (Weißabgleich-Gains + Farbmatrix des Chips + sRGB-Kurve, keine
 * Kontrastkurve, keine Sättigungsanhebung) und je Farbkanal die Kurve „Handy → neutral“ bestimmen
 * (Histogramm-Angleichung). Die Kurven werden vor jede Film-LUT gesetzt.
 */
object Calibration {
    private const val TAG = "VolksKameraKal"
    private fun file(c: Context) = File(c.filesDir, "kalibrierung.json")

    /** 3 × 256 Werte (0..1): Korrektur je Kanal; null = nicht kalibriert oder ausgeschaltet */
    class Curves(val r: FloatArray, val g: FloatArray, val b: FloatArray, val date: Long, val camera: String) {
        fun map(v: Float, ch: Int): Float {
            val t = when (ch) { 0 -> r; 1 -> g; else -> b }
            val x = v.coerceIn(0f, 1f) * 255f
            val i = x.toInt().coerceAtMost(254)
            val f = x - i
            return t[i] * (1 - f) + t[i + 1] * f
        }
        /** Kennung für Cache-Dateinamen */
        val key get() = "k$date"
    }

    fun load(c: Context): Curves? = runCatching {
        val o = JSONObject(file(c).readText())
        if (!o.optBoolean("aktiv", true)) return null
        fun arr(k: String) = o.getJSONArray(k).let { a -> FloatArray(256) { a.getDouble(it).toFloat() } }
        Curves(arr("r"), arr("g"), arr("b"), o.getLong("datum"), o.optString("kamera"))
    }.getOrNull()

    fun exists(c: Context) = file(c).isFile
    fun isActive(c: Context) = runCatching { JSONObject(file(c).readText()).optBoolean("aktiv", true) }.getOrDefault(false)
    fun setActive(c: Context, on: Boolean) {
        runCatching { val o = JSONObject(file(c).readText()); o.put("aktiv", on); file(c).writeText(o.toString()) }
    }
    fun delete(c: Context) { file(c).delete() }

    private fun save(c: Context, cur: Array<FloatArray>, camera: String) {
        val o = JSONObject().put("datum", System.currentTimeMillis()).put("kamera", camera).put("aktiv", true)
        listOf("r", "g", "b").forEachIndexed { i, k -> o.put(k, JSONArray(cur[i].map { it.toDouble() })) }
        file(c).writeText(o.toString())
    }

    /** Hat die Hauptkamera RAW? */
    fun rawCameraId(c: Context): String? {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return cm.cameraIdList.firstOrNull { id ->
            val ch = cm.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
        }
    }

    /**
     * Aufnehmen und auswerten. [onDone] mit Fehlermeldung (null = Erfolg) auf dem Hintergrund-Thread;
     * Aufrufer wechselt selbst auf den Haupt-Thread.
     */
    @SuppressLint("MissingPermission")
    fun run(c: Context, onDone: (String?) -> Unit) {
        val id = rawCameraId(c) ?: return onDone(com.volkskamera.app.t("Diese Kamera liefert keine Rohdaten (RAW) – Kalibrierung nicht möglich."))
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ch = cm.getCameraCharacteristics(id)
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val rawSize = map.getOutputSizes(ImageFormat.RAW_SENSOR).maxBy { it.width * it.height }
        // JPEG in vergleichbarer Größe (für die Histogramme genügt ein kleineres Bild)
        val jpgSize = map.getOutputSizes(ImageFormat.JPEG).filter { it.width <= 2048 }.maxByOrNull { it.width * it.height }
            ?: map.getOutputSizes(ImageFormat.JPEG).minBy { it.width * it.height }
        val thread = HandlerThread("kalibrierung").apply { start() }
        val h = Handler(thread.looper)
        val rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
        val jpgReader = ImageReader.newInstance(jpgSize.width, jpgSize.height, ImageFormat.JPEG, 2)
        var raw: ShortArray? = null; var rawStride = 0
        var jpg: ByteArray? = null
        var result: TotalCaptureResult? = null
        var finished = false
        fun finish(err: String?) {
            if (finished) return
            finished = true
            runCatching { rawReader.close(); jpgReader.close() }
            thread.quitSafely()
            onDone(err)
        }
        fun tryEvaluate() {
            val r = raw ?: return; val j = jpg ?: return; val res = result ?: return
            try {
                val cur = evaluate(ch, res, r, rawStride, rawSize.width, rawSize.height, j)
                save(c, cur, "Kamera $id")
                finish(null)
            } catch (t: Throwable) {
                Log.e(TAG, "Auswertung fehlgeschlagen", t); finish(com.volkskamera.app.tf("Auswertung fehlgeschlagen: %s", t.message))
            }
        }
        rawReader.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { img ->
                val p = img.planes[0]
                rawStride = p.rowStride / 2
                val sb = p.buffer.asShortBuffer()
                raw = ShortArray(sb.remaining()).also { sb.get(it) }
            }
            tryEvaluate()
        }, h)
        jpgReader.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { img ->
                val bb = img.planes[0].buffer
                jpg = ByteArray(bb.remaining()).also { bb.get(it) }
            }
            tryEvaluate()
        }, h)
        try {
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(dev: CameraDevice) {
                    @Suppress("DEPRECATION")
                    dev.createCaptureSession(listOf(rawReader.surface, jpgReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            // Vorlauf: Automatik (Belichtung, Weißabgleich, Fokus) einschwingen lassen
                            val pre = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(jpgReader.surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            }.build()
                            // Vorlauf-Bilder verwerfen
                            jpgReader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, h)
                            s.setRepeatingRequest(pre, null, h)
                            h.postDelayed({
                                runCatching {
                                    s.stopRepeating()
                                    jpgReader.setOnImageAvailableListener({ r ->
                                        r.acquireLatestImage()?.use { img ->
                                            val bb = img.planes[0].buffer
                                            jpg = ByteArray(bb.remaining()).also { bb.get(it) }
                                        }
                                        tryEvaluate()
                                    }, h)
                                    val shot = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(rawReader.surface); addTarget(jpgReader.surface)
                                        set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF)
                                    }.build()
                                    s.capture(shot, object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(ss: CameraCaptureSession, rq: CaptureRequest, r: TotalCaptureResult) {
                                            result = r; tryEvaluate()
                                            h.postDelayed({ runCatching { dev.close() } }, 1500)
                                        }
                                    }, h)
                                }.onFailure { dev.close(); finish(com.volkskamera.app.tf("Aufnahme fehlgeschlagen: %s", it.message)) }
                            }, 2500)
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) { dev.close(); finish(com.volkskamera.app.t("Kamera ließ sich nicht einrichten.")) }
                    }, h)
                }
                override fun onDisconnected(dev: CameraDevice) { dev.close(); finish(com.volkskamera.app.t("Kamera getrennt.")) }
                override fun onError(dev: CameraDevice, e: Int) { dev.close(); finish(com.volkskamera.app.tf("Kamerafehler %d.", e)) }
            }, h)
        } catch (t: Throwable) { finish(com.volkskamera.app.tf("Kamera nicht verfügbar: %s", t.message)) }
        h.postDelayed({ finish(com.volkskamera.app.t("Zeitüberschreitung – bitte erneut versuchen.")) }, 15000)
    }

    /** RAW neutral entwickeln (halbe Auflösung), JPEG laden, Kurven je Kanal per Histogramm-Angleichung. */
    private fun evaluate(ch: CameraCharacteristics, res: TotalCaptureResult, raw: ShortArray, stride: Int,
                         w: Int, h: Int, jpg: ByteArray): Array<FloatArray> {
        val black = ch.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        val blk = FloatArray(4) { i -> (black?.getOffsetForIndex(i % 2, i / 2) ?: 64).toFloat() }
        val white = (res.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL) ?: ch.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL) ?: 1023).toFloat()
        val gains = res.get(CaptureResult.COLOR_CORRECTION_GAINS)
        val ccm = res.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
        val m = FloatArray(9) { i -> ccm?.getElement(i % 3, i / 3)?.toFloat() ?: if (i % 4 == 0) 1f else 0f }
        // CFA: Position von R, G, G, B im 2×2-Feld
        val cfa = ch.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) ?: 0
        // Index 0..3 = (x,y) (0,0) (1,0) (0,1) (1,1) -> Farbe 0=R,1=G,2=B
        val layout = when (cfa) {
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> intArrayOf(0, 1, 1, 2)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> intArrayOf(1, 0, 2, 1)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> intArrayOf(1, 2, 0, 1)
            else -> intArrayOf(2, 1, 1, 0)   // BGGR
        }
        val gR = gains?.red ?: 1f; val gG = ((gains?.greenEven ?: 1f) + (gains?.greenOdd ?: 1f)) / 2f; val gB = gains?.blue ?: 1f
        // Histogramme (256 Stufen) – nur jedes 4. Feld reicht statistisch
        val histN = Array(3) { LongArray(256) }
        val step = 4
        val rgb = FloatArray(3)
        var y = 0
        while (y + 1 < h) {
            var x = 0
            while (x + 1 < w) {
                rgb[0] = 0f; rgb[1] = 0f; rgb[2] = 0f
                var gCount = 0
                for (k in 0..3) {
                    val xx = x + (k % 2); val yy = y + (k / 2)
                    val v = ((raw[yy * stride + xx].toInt() and 0xFFFF) - blk[k]) / (white - blk[k])
                    val col = layout[k]
                    if (col == 1) { rgb[1] += v; gCount++ } else rgb[col] += v
                }
                rgb[1] /= gCount.coerceAtLeast(1)
                rgb[0] *= gR; rgb[1] *= gG; rgb[2] *= gB
                val r = m[0] * rgb[0] + m[1] * rgb[1] + m[2] * rgb[2]
                val g = m[3] * rgb[0] + m[4] * rgb[1] + m[5] * rgb[2]
                val b = m[6] * rgb[0] + m[7] * rgb[1] + m[8] * rgb[2]
                histN[0][q(srgb(r))]++; histN[1][q(srgb(g))]++; histN[2][q(srgb(b))]++
                x += 2 * step
            }
            y += 2 * step
        }
        // Handy-Bild (JPEG)
        val bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.size, BitmapFactory.Options().apply { inSampleSize = 2 })
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val histP = Array(3) { LongArray(256) }
        for (i in px.indices step 3) {
            val c = px[i]; histP[0][c shr 16 and 0xFF]++; histP[1][c shr 8 and 0xFF]++; histP[2][c and 0xFF]++
        }
        // Belichtungsausgleich: beide Bilder auf gleiche mittlere Helligkeit (die Kurve soll Kontrast/Farbe
        // korrigieren, nicht die Belichtung – die macht das Zählwerk)
        return Array(3) { chn -> matchCurve(histP[chn], histN[chn]) }.let { cur ->
            val mid = (cur[0][128] + cur[1][128] + cur[2][128]) / 3f
            val scale = 0.5f / mid.coerceAtLeast(0.05f)
            Array(3) { chn -> FloatArray(256) { i -> blend(i / 255f, (cur[chn][i] * scale).coerceIn(0f, 1f)) } }.also { smooth(it) }
        }
    }

    private fun srgb(v: Float): Float { val c = v.coerceIn(0f, 1f); return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1 / 2.4f) - 0.055f }
    private fun q(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    /** Histogramm-Angleichung: Wert x im Handy-Bild -> Wert mit gleichem Rang im neutralen Bild. */
    private fun matchCurve(src: LongArray, dst: LongArray): FloatArray {
        val cs = DoubleArray(256); val cd = DoubleArray(256)
        var a = 0.0; var b = 0.0
        val ts = src.sum().toDouble().coerceAtLeast(1.0); val td = dst.sum().toDouble().coerceAtLeast(1.0)
        for (i in 0..255) { a += src[i] / ts; b += dst[i] / td; cs[i] = a; cd[i] = b }
        var j = 0
        return FloatArray(256) { i ->
            while (j < 255 && cd[j] < cs[i]) j++
            j / 255f
        }
    }

    /** Nur zu 75 % übernehmen und höchstens ±0,12 abweichen (eine Szene ist nie ganz repräsentativ) */
    private fun blend(x: Float, y: Float) = x + ((y - x) * 0.75f).coerceIn(-0.12f, 0.12f)

    /** glätten und monoton machen */
    private fun smooth(c: Array<FloatArray>) {
        for (t in c) {
            val s = FloatArray(256) { i -> (-6..6).map { d -> t[(i + d).coerceIn(0, 255)] }.average().toFloat() }
            var mx = 0f
            for (i in 0..255) { mx = maxOf(mx, s[i]); t[i] = mx }
            t[0] = minOf(t[0], 0.02f)
        }
    }

    /** Kalibrier-Kurven vor eine LUT setzen: neu(rgb) = LUT(kurve(rgb)). */
    fun applyTo(cube: FloatArray, cur: Curves): FloatArray {
        val n = com.volkskamera.app.render.LutFile.N
        val out = FloatArray(cube.size); val tmp = FloatArray(3)
        for (bi in 0 until n) for (gi in 0 until n) for (ri in 0 until n) {
            val s = n - 1f
            com.volkskamera.app.render.LutFile.sample(cube, cur.map(ri / s, 0), cur.map(gi / s, 1), cur.map(bi / s, 2), tmp)
            val i = ((bi * n + gi) * n + ri) * 3
            out[i] = tmp[0]; out[i + 1] = tmp[1]; out[i + 2] = tmp[2]
        }
        return out
    }

    /** LUT-Datei mit Kalibrierung (zwischengespeichert); ohne Kalibrierung unverändert. */
    fun lutWith(c: Context, base: String): String {
        val cur = load(c) ?: return base
        val dir = File(c.filesDir, "luts_kal").apply { mkdirs() }
        val out = File(dir, "${cur.key}_${Integer.toHexString(base.hashCode())}.png")
        if (!out.isFile) runCatching {
            com.volkskamera.app.render.LutFile.write(applyTo(com.volkskamera.app.render.LutFile.load(c, base), cur), out)
        }.onFailure { return base }
        return out.absolutePath
    }
}
