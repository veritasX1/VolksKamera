package com.volkskamera.app.camera

import android.app.Activity
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

/**
 * Eigenständiger Mess-Modus (roher Camera2-Zugriff, ohne CameraX), um den Grundwert
 * des Kamerachips zu bestimmen. Nimmt dieselbe (statische) Szene mehrfach auf:
 *   1. AUTO        – so wie das Handy von sich aus verarbeitet (Referenz "verschönert")
 *   2. NEUTRAL     – ISO 100, feste Zeit, fester WB, lineare Tonwertkurve, Schärfung/NR aus
 *   3. Belichtungsreihe (neutral) – gleiche Szene, Zeit ×1/4…×4 -> Kennlinie rekonstruierbar
 *   4. RAW (DNG)   – ungeschöntes Referenzbild bei Basiszeit
 * Alle Dateien landen in  Android/data/com.volkskamera.app/files/messung/  (per adb pull holbar),
 * dazu ein manifest.txt mit den echten Sensorwerten je Aufnahme.
 *
 * Start:  adb shell am start -n com.volkskamera.app/.MessungActivity
 */
class MessungActivity : Activity() {

    private val TAG = "VolksKameraMess"
    private lateinit var manager: CameraManager
    private lateinit var chars: CameraCharacteristics
    private var camId = "0"
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private lateinit var startBtn: Button

    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private lateinit var previewSurface: Surface

    private val thread = HandlerThread("mess").also { it.start() }
    private val bg = Handler(thread.looper)            // Mess-Ablauf (wartet)
    private val cbThread = HandlerThread("mess-cb").also { it.start() }
    private val cb = Handler(cbThread.looper)          // Kamera-Rückrufe (dürfen nicht blockiert werden)

    private lateinit var outDir: File
    private val log = StringBuilder()

    // von der AUTO-Aufnahme übernommen
    private var autoExposure = 20_000_000L   // ns, Startwert
    private var autoIso = 400
    private var wbGains: RggbChannelVector? = null
    private var autoFocus: Float? = null     // Fokusdistanz (Dioptrien) aus der Automatik

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        manager = getSystemService(CAMERA_SERVICE) as CameraManager
        outDir = File(getExternalFilesDir(null), "messung").apply { mkdirs() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texture = TextureView(this)
        root.addView(texture, LinearLayout.LayoutParams(MATCH, 0, 3f))
        startBtn = Button(this).apply {
            text = "Messung starten"
            setOnClickListener { it.isEnabled = false; startBtn.text = "läuft…"; bg.post { runSequence() } }
        }
        root.addView(startBtn, LinearLayout.LayoutParams(MATCH, WRAP))
        status = TextView(this).apply { setPadding(24, 24, 24, 24); text = "Kamera wird geöffnet…" }
        root.addView(ScrollView(this).apply { addView(status) }, LinearLayout.LayoutParams(MATCH, 0, 2f))
        setContentView(root, FrameLayout.LayoutParams(MATCH, MATCH))

        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { open() }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun ui(msg: String) { Log.i(TAG, msg); runOnUiThread { status.append(msg + "\n") } }

    private fun open() {
        try {
            // größte Rückkamera mit RAW + manueller Steuerung wählen (i.d.R. "0")
            camId = manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
            } ?: "0"
            chars = manager.getCameraCharacteristics(camId)
            dumpCharacteristics()

            val jpegSize = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(android.graphics.ImageFormat.JPEG).maxByOrNull { it.width.toLong() * it.height }!!
            jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, android.graphics.ImageFormat.JPEG, 2)
            val rawSize = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }
            if (rawSize != null)
                rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, android.graphics.ImageFormat.RAW_SENSOR, 2)

            val st = texture.surfaceTexture!!.apply { setDefaultBufferSize(1920, 1440) }
            previewSurface = Surface(st)

            manager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) { device = cam; createSession() }
                override fun onDisconnected(cam: CameraDevice) { cam.close() }
                override fun onError(cam: CameraDevice, e: Int) { ui("Kamera-Fehler $e"); cam.close() }
            }, bg)
        } catch (t: Throwable) {
            ui("Öffnen fehlgeschlagen: ${t.message}")
        }
    }

    private fun createSession() {
        val surfaces = listOfNotNull(previewSurface, jpegReader?.surface, rawReader?.surface)
        @Suppress("DEPRECATION")
        device!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                val r = device!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }
                s.setRepeatingRequest(r.build(), null, bg)
                ui("Kamera $camId bereit. Szene ruhig halten, dann „Messung starten“.")
            }
            override fun onConfigureFailed(s: CameraCaptureSession) { ui("Session fehlgeschlagen") }
        }, bg)
    }

    /** Eine Einzelaufnahme; blockiert bis Bild UND Aufnahme-Ergebnis da sind (kein Race). */
    private fun capture(req: CaptureRequest, reader: ImageReader, name: String, saveDng: Boolean = false) {
        val sess = session ?: return
        var image: Image? = null
        var result: TotalCaptureResult? = null
        var failed = false
        val imgLatch = java.util.concurrent.CountDownLatch(1)
        val resLatch = java.util.concurrent.CountDownLatch(1)
        reader.setOnImageAvailableListener({ r -> image = r.acquireNextImage(); imgLatch.countDown() }, cb)
        sess.capture(req, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(s: CameraCaptureSession, rq: CaptureRequest, res: TotalCaptureResult) {
                result = res
                if (name.startsWith("1_auto")) {
                    autoExposure = res.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: autoExposure
                    autoIso = res.get(CaptureResult.SENSOR_SENSITIVITY) ?: autoIso
                    wbGains = res.get(CaptureResult.COLOR_CORRECTION_GAINS)
                    autoFocus = res.get(CaptureResult.LENS_FOCUS_DISTANCE)
                }
                resLatch.countDown()
            }
            override fun onCaptureFailed(s: CameraCaptureSession, rq: CaptureRequest,
                                         f: android.hardware.camera2.CaptureFailure) {
                failed = true; ui("Aufnahme $name FEHLGESCHLAGEN (reason ${f.reason})")
                imgLatch.countDown(); resLatch.countDown()
            }
        }, cb)
        val ok = imgLatch.await(8, java.util.concurrent.TimeUnit.SECONDS) &&
                 resLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        val img = image
        if (failed || !ok || img == null || result == null) {
            ui("$name: kein Bild (ok=$ok failed=$failed)"); img?.close(); return
        }
        try {
            if (saveDng) {
                DngCreator(chars, result!!).use { dng ->
                    FileOutputStream(File(outDir, "$name.dng")).use { dng.writeImage(it, img) }
                }
            } else {
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                FileOutputStream(File(outDir, "$name.jpg")).use { it.write(bytes) }
            }
        } catch (t: Throwable) { ui("Speichern $name: ${t.message}") } finally { img.close() }
    }

    private fun neutralBuilder(exposure: Long, targets: List<Surface>): CaptureRequest.Builder =
        device!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            targets.forEach { addTarget(it) }
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
            set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            set(CaptureRequest.SENSOR_SENSITIVITY, 100)
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
            set(CaptureRequest.SENSOR_FRAME_DURATION, 0L)
            // Weißabgleich fest auf die von AUTO gefundenen Gains, Farbmatrix neutral
            wbGains?.let {
                set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                set(CaptureRequest.COLOR_CORRECTION_GAINS, it)
            }
            // lineare Tonwertkurve (keine Handy-Gammakurve)
            val linear = floatArrayOf(0f, 0f, 1f, 1f)
            set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
            set(CaptureRequest.TONEMAP_CURVE, TonemapCurve(linear, linear, linear))
            set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF)
            set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF)
            // Fokus: der Chip meldet keine Fokusdistanz -> statt AF_OFF (Linse auf unendlich)
            // den funktionierenden Dauer-Autofokus behalten; bei statischer Szene bleibt er stehen.
            if (autoFocus != null) {
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, autoFocus!!)
            } else {
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
        }

    private fun runSequence() {
        val jr = jpegReader ?: return
        ui("— AUTO —")
        val auto = device!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(jr.surface)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        }.build()
        capture(auto, jr, "1_auto")
        ui("AUTO: ISO $autoIso, Zeit ${autoExposure / 1000}µs, Fokus ${autoFocus}, Gains ${wbGains}")

        // Basiszeit für ISO 100 auf gleiche Helligkeit umgerechnet
        val maxExp = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: 250_000_000L
        val minExp = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.lower ?: 100_000L
        val base = (autoExposure * (autoIso / 100.0)).toLong().coerceIn(minExp, maxExp)

        val rr = rawReader
        if (rr == null) { ui("Keine RAW-Unterstützung – abgebrochen."); return }

        // Belichtungsreihe als RAW (linear; JPEG liefert unter manueller Einstellung nichts).
        // RAW ist WB-neutral, deshalb kein COLOR_CORRECTION nötig; Fokus fest aus der Automatik.
        ui("— Belichtungsreihe (RAW, ISO100, linear) —")
        val factors = listOf(0.25, 0.5, 1.0, 2.0, 4.0)
        factors.forEachIndexed { i, f ->
            val e = (base * f).toLong().coerceIn(minExp, maxExp)
            capture(neutralBuilder(e, listOf(rr.surface)).build(), rr, "3_reihe_${i}_x${f}", saveDng = true)
            ui("  Reihe ×$f: ${e / 1000}µs")
        }
        ui("— RAW-Referenz bei Basiszeit —")
        capture(neutralBuilder(base, listOf(rr.surface)).build(), rr, "4_raw", saveDng = true)

        File(outDir, "manifest.txt").writeText(log.toString())
        ui("FERTIG. Dateien in: ${outDir.absolutePath}")
        runOnUiThread { startBtn.text = "fertig"; startBtn.isEnabled = true }
    }

    private fun r9(m: android.hardware.camera2.params.ColorSpaceTransform?): String {
        if (m == null) return "?"
        return (0 until 9).joinToString(" ") { i ->
            val n = m.getElement(i % 3, i / 3); "%.3f".format(n.numerator.toDouble() / n.denominator)
        }
    }

    private fun dumpCharacteristics() {
        fun <T> g(k: CameraCharacteristics.Key<T>) = chars.get(k)
        log.appendLine("moto g84 – Kamera $camId  (VolksKamera Messung)")
        log.appendLine("ISO-Bereich: ${g(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)}")
        log.appendLine("Zeit-Bereich (ns): ${g(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)}")
        log.appendLine("WhiteLevel: ${g(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)}  BlackLevel: ${
            g(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)}")
        log.appendLine("Referenzlicht1: ${g(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)}  " +
            "Referenzlicht2: ${g(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)}")
        log.appendLine("colorTransform1: ${r9(g(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1))}")
        log.appendLine("forwardMatrix1:  ${r9(g(CameraCharacteristics.SENSOR_FORWARD_MATRIX1))}")
        log.appendLine("colorTransform2: ${r9(g(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2))}")
        log.appendLine("forwardMatrix2:  ${r9(g(CameraCharacteristics.SENSOR_FORWARD_MATRIX2))}")
        log.appendLine("Tonemap max Punkte: ${g(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS)}")
        log.appendLine("---")
        runOnUiThread { status.text = log.toString() }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close(); device?.close(); jpegReader?.close(); rawReader?.close(); thread.quitSafely(); cbThread.quitSafely()
    }

    companion object { const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT; const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT }
}
