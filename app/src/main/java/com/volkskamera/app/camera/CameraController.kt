package com.volkskamera.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sucher + Videoaufnahme mit CameraX – bewusst OHNE Live-Effekt (RetroCam-Lehre: ein
 * CameraEffect/SurfaceProcessor deckelte die Auflösung und verursachte den Absturz beim
 * Kamerawechsel). Der Film-Look wird erst nach der Aufnahme berechnet.
 *
 * Fokus: Camera2 CONTROL_AF_MODE_AUTO statt CONTINUOUS_VIDEO – das Objektiv bewegt sich nur,
 * wenn der Nutzer in den Sucher tippt. Nie von selbst (auch nicht beim Start oder vor der Aufnahme).
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraController(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var generation = 0

    /**
     * Manuelle Belichtung wie an der Filmkamera: Belichtungszeit am Zählwerk + Empfindlichkeit des Films.
     * Wird bei JEDEM Binden sofort mitgegeben (kein Automatik-Moment beim Kamerawechsel) und
     * beim Fokussieren nie angetastet.
     */
    data class Exposure(val filmTimeNs: Long, val filmIso: Int)
    var exposure: Exposure? = null; private set
    private var appliedTo: Camera? = null

    /**
     * ALLE eigenen Aufnahme-Einstellungen (Belichtung, Sperre, Weißabgleich, LED) in einem Satz:
     * Camera2CameraControl.setCaptureRequestOptions ERSETZT jedes Mal alles – einzeln gesetzt würde z. B. der
     * Weißabgleich die manuelle Belichtung löschen. Die LED läuft bewusst nicht über enableTorch(): CameraX
     * schaltet dafür die Belichtungsautomatik ein. FLASH_MODE_TORCH funktioniert auch bei AE_MODE_OFF.
     */
    private val requestOptions = linkedMapOf<CaptureRequest.Key<*>, Any>()

    private fun <T : Any> option(key: CaptureRequest.Key<T>, value: T) { requestOptions[key] = value }

    /** Regler „Analog“ aktiv: die kamerainterne Nachschärfung (EDGE_MODE) abschalten – sie macht das Bild „digital“. */
    private var sharpeningOff = false
    /** vor bind() setzen: Sensor lieber mit 30 fps auslesen (Moiré-Filter) */
    var preferSensor30 = false
    fun setCameraSharpening(off: Boolean) {
        sharpeningOff = off
        putSharpening()
        applyOptions()
    }
    private fun putSharpening() {
        val cam = camera ?: return
        val modes = Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
        if (sharpeningOff && modes?.contains(CaptureRequest.EDGE_MODE_OFF) == true) option(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        else requestOptions.remove(CaptureRequest.EDGE_MODE)
    }

    private fun applyOptions() {
        val cam = camera ?: return
        val b = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
        @Suppress("UNCHECKED_CAST")
        requestOptions.forEach { (k, v) -> b.setCaptureRequestOption(k as CaptureRequest.Key<Any>, v) }
        androidx.camera.camera2.interop.Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
    }

    private fun putExposure(cam: Camera, e: Exposure) {
        val s = sensorExposure(cam.cameraInfo, e, boundFps)
        option(CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF)
        option(CaptureRequest.SENSOR_EXPOSURE_TIME, s.timeNs)
        option(CaptureRequest.SENSOR_SENSITIVITY, s.iso)
        option(CaptureRequest.SENSOR_FRAME_DURATION, s.frameNs)
        appliedTo = cam
    }
    /** Aufnahme-Auflösung (kurze Seite): 480, 576, 720 oder 1080 */
    var resolution: Int = 1080
    /** Frontkamera-Aufnahme spiegeln (wie im Spiegel) */
    var mirrorFront: Boolean = true

    /** Tatsächlich gebundene Sensor-Bildrate (60 oder Rückfall 30/automatisch). */
    var boundFps: Int = 0; private set
    val isRecording get() = recording != null

    /** Verfügbare Objektive (1,0 / 0,7 / Front ...) ermitteln. */
    fun loadLenses(onReady: (List<LensOption>) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get()
            provider = p
            onReady(buildLensOptions(p.availableCameraInfos))
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Kamera binden. [aspect43] = Aufnahme in 4:3 statt 16:9 (Sucher zeigt dasselbe Format).
     * Versucht 60 fps, fällt auf 30 und dann auf automatisch zurück, falls die Kombination
     * nicht unterstützt wird.
     */
    fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        lens: LensOption,
        aspect43: Boolean,
        rotation: Int,
        onBound: (Camera, Int) -> Unit,
    ) {
        if (recording != null) return
        val gen = ++generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (gen != generation) return@addListener
            val p = future.get()
            provider = p
            p.unbindAll()
            // Moiré-Filter bei Bildraten bis 30: Sensor mit 30 fps auslesen (bei 60 fps fassen viele Handys
            // Bildpunkte zusammen oder überspringen Zeilen – die klassische Moiré-Quelle)
            val wanted = (if (preferSensor30) listOf(30, 60, 0) else listOf(60, 30, 0)).filter { it == 0 || supportsFps(lens.cameraInfo, it) }
            for (fps in wanted) {
                val bound = tryBind(p, owner, previewView, lens, aspect43, rotation, fps)
                if (bound != null) {
                    camera = bound
                    boundFps = fps
                    // neue Kamera: LED aus, Sperre gelöst – die feste Belichtung sofort wieder in den Satz
                    requestOptions.clear()
                    exposureLocked = false
                    exposure?.let { putExposure(bound, it) }
                    putSharpening()
                    applyOptions()
                    onBound(bound, fps)
                    // kein automatisches Scharfstellen: fokussiert wird nur, wenn der Nutzer in den Sucher tippt
                    return@addListener
                }
            }
            Log.e(TAG, "Kamera ${lens.label} ließ sich nicht binden")
        }, ContextCompat.getMainExecutor(context))
    }

    private fun tryBind(
        p: ProcessCameraProvider,
        owner: LifecycleOwner,
        previewView: PreviewView,
        lens: LensOption,
        aspect43: Boolean,
        rotation: Int,
        fps: Int,
    ): Camera? {
        val strategy = if (aspect43) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val pv = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(strategy).build())
            .setTargetRotation(rotation)
            .apply { cameraOptions(this, fps, lens.cameraInfo) }
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        // aufnehmen in der nächsthöheren CameraX-Stufe; auf die gewählte Auflösung skaliert wird beim Entwickeln
        val quality = when {
            resolution <= 480 -> Quality.SD
            resolution <= 720 -> Quality.HD
            else -> Quality.FHD
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.higherQualityOrLowerThan(quality)))
            .setAspectRatio(if (aspect43) AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9)
            .build()
        val vc = VideoCapture.Builder(recorder)
            .setTargetRotation(rotation)
            .setMirrorMode(if (mirrorFront) androidx.camera.core.MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else androidx.camera.core.MirrorMode.MIRROR_MODE_OFF)
            .apply { cameraOptions(this, fps, lens.cameraInfo) }
            .build()
        return try {
            val group = UseCaseGroup.Builder().addUseCase(pv).addUseCase(vc).build()
            p.bindToLifecycle(owner, lens.toSelector(), group).also {
                preview = pv
                videoCapture = vc
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Binden mit $fps fps fehlgeschlagen", t)
            p.unbindAll()
            null
        }
    }

    /** Feste Bildrate (0 = Kamera entscheidet), Autofokus nur auf Auslösung, manuelle Belichtung ab dem ersten Bild. */
    private fun <T> cameraOptions(b: ExtendableBuilder<T>, fps: Int, info: CameraInfo) {
        val ext = Camera2Interop.Extender(b)
        if (debugLog && b is Preview.Builder) ext.setSessionCaptureCallback(DebugCaptureLog())
        if (fps > 0) ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        exposure?.let { e ->
            val s = sensorExposure(info, e, fps)
            ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF)
            ext.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, s.timeNs)
            ext.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, s.iso)
            ext.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, s.frameNs)
        }
    }

    private class SensorExposure(val timeNs: Long, val iso: Int, val frameNs: Long)

    /**
     * Film-Belichtung auf den Sensor umrechnen, bei GLEICHER Lichtmenge: Zeit × Empfindlichkeit bleibt
     * erhalten. Ein ISO-8-Film bei 1/50 s wird auf dem Sensor (Mindest-ISO z. B. 100) zu 1/625 s –
     * das Bild ist so hell, wie es der echte Film gewesen wäre. Grenzen des Sensors werden eingehalten;
     * Belichtungszeiten länger als ein Bild verlängern die Bilddauer (weniger Bilder pro Sekunde, wie in echt).
     */
    private fun sensorExposure(info: CameraInfo, e: Exposure, fps: Int): SensorExposure {
        val ch = Camera2CameraInfo.from(info)
        val isoRange = ch.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 3200)
        val tRange = ch.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(20_000L, 250_000_000L)
        val maxFrame = ch.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 250_000_000L
        val iso = e.filmIso.coerceIn(isoRange.lower, isoRange.upper)
        val t = (e.filmTimeNs.toDouble() * e.filmIso / iso).toLong().coerceIn(tRange.lower, minOf(tRange.upper, maxFrame))
        val frame = maxOf(if (fps > 0) 1_000_000_000L / fps else 33_333_333L, t).coerceAtMost(maxFrame)
        return SensorExposure(t, iso, frame)
    }

    private fun supportsFps(info: CameraInfo, fps: Int): Boolean {
        val ranges = Camera2CameraInfo.from(info)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return false
        return ranges.any { it.lower == fps && it.upper == fps }
    }

    /** AE-/AWB-Sperre aktiv: Helligkeit und Weißabgleich bleiben stehen. */
    var exposureLocked = false
        private set

    /**
     * AE- und AWB-Sperre setzen oder lösen. Berührt den Fokus nicht (kein AF-Auslöser) –
     * es bleibt bei "einmal scharfstellen, danach nur durch Antippen".
     */
    fun setExposureLock(on: Boolean) {
        val cam = camera ?: return
        exposureLocked = on
        option(CaptureRequest.CONTROL_AE_LOCK, on)
        option(CaptureRequest.CONTROL_AWB_LOCK, on)
        applyOptions()
    }

    /**
     * Belichtung wie an einer Analogkamera: Belichtungszeit + Empfindlichkeit des Films. Gilt sofort für
     * die laufende Kamera und für jede künftig gebundene (feste Bindung, keine Automatik dazwischen).
     */
    fun setExposure(filmTimeNs: Long, filmIso: Int) {
        val e = Exposure(filmTimeNs, filmIso.coerceAtLeast(1))
        val cam0 = camera
        if (e == exposure && cam0 === appliedTo) return   // unverändert und schon angewandt
        exposure = e
        val cam = cam0 ?: return
        putExposure(cam, e)
        applyOptions()
    }

    /** Weißabgleich-Voreinstellung setzen (CONTROL_AWB_MODE, z.B. Tageslicht/Bewölkt/Kunstlicht). */
    fun setWhiteBalance(awbMode: Int) {
        if (camera == null) return
        option(CaptureRequest.CONTROL_AWB_LOCK, false)
        option(CaptureRequest.CONTROL_AWB_MODE, awbMode)
        applyOptions()
    }

    /**
     * Scharfstellen auf einen Punkt; bleibt stehen (kein automatisches Zurückfallen auf Dauer-AF).
     * Bei gesperrter Belichtung nur Fokus – Helligkeit und Weißabgleich werden nicht neu gemessen.
     */
    fun focusAt(point: MeteringPoint, onDone: (Boolean) -> Unit = {}) {
        val cam = camera ?: return onDone(false)
        // Nur scharfstellen – die Belichtung gehört allein dem Zählwerk ZEIT und dem Film (nie nachmessen)
        val flags = FocusMeteringAction.FLAG_AF
        val action = FocusMeteringAction.Builder(point, flags)
            .disableAutoCancel()
            .build()
        val f = cam.cameraControl.startFocusAndMetering(action)
        f.addListener({
            onDone(runCatching { f.get().isFocusSuccessful }.getOrDefault(false))
        }, ContextCompat.getMainExecutor(context))
    }

    fun focusCenter(previewView: PreviewView, onDone: (Boolean) -> Unit = {}) {
        val w = previewView.width.toFloat().coerceAtLeast(1f)
        val h = previewView.height.toFloat().coerceAtLeast(1f)
        focusAt(previewView.meteringPointFactory.createPoint(w / 2, h / 2), onDone)
    }

    fun setTorch(on: Boolean) {
        if (camera == null) return
        option(CaptureRequest.FLASH_MODE, if (on) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
        applyOptions()
    }

    fun hasFlash() = camera?.cameraInfo?.hasFlashUnit() == true

    /** Drehung ohne Neu-Binden: gilt für den Sucher sofort, für die Aufnahme ab der nächsten. */
    fun setRotation(rotation: Int) {
        preview?.targetRotation = rotation
        if (recording == null) videoCapture?.targetRotation = rotation
    }

    @SuppressLint("MissingPermission") // Mikrofon-Berechtigung wird vor dem Start geprüft
    fun startRecording(file: File, withAudio: Boolean, onStatus: (Long) -> Unit, onFinished: (File?) -> Unit) {
        val vc = videoCapture ?: return
        if (recording != null) return
        var pending = vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(context)) { e ->
            when (e) {
                is VideoRecordEvent.Status -> onStatus(TimeUnit.NANOSECONDS.toMillis(e.recordingStats.recordedDurationNanos))
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    if (e.hasError() && e.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                        Log.e(TAG, "Aufnahme-Fehler ${e.error}", e.cause)
                        onFinished(if (file.exists() && file.length() > 0) file else null)
                    } else {
                        onFinished(file)
                    }
                }
                else -> {}
            }
        }
    }

    fun stopRecording() {
        recording?.stop()
    }

    fun unbind() {
        generation++
        recording?.stop()
        provider?.unbindAll()
        camera = null
    }

    private val debugLog = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Nur Debug-Build: alle 2 s echte Sensorwerte ins Log (Tag VolksKameraCam) – Bilddauer
     * (= tatsächliche fps), AF-Modus/-Zustand und Linsenposition. So lässt sich prüfen, ob
     * wirklich 60 fps laufen und ob der Fokus während der Aufnahme stillhält.
     */
    private class DebugCaptureLog : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
        private var n = 0L
        private var lastTs = 0L
        override fun onCaptureCompleted(
            session: android.hardware.camera2.CameraCaptureSession,
            request: android.hardware.camera2.CaptureRequest,
            result: android.hardware.camera2.TotalCaptureResult,
        ) {
            val ts = result.get(android.hardware.camera2.CaptureResult.SENSOR_TIMESTAMP) ?: return
            if (n++ % 120 == 0L && lastTs != 0L) {
                val dur = result.get(android.hardware.camera2.CaptureResult.SENSOR_FRAME_DURATION) ?: 0L
                Log.i(TAG, ("fps=%.1f (Abstand %.1f ms) afAngefordert=%s afMode=%s afState=%s fokus=%.3f " +
                    "aeLock=%s aeState=%s awbLock=%s awbState=%s belichtung=%.2f ms iso=%s").format(
                    if (dur > 0) 1e9 / dur else 0.0, (ts - lastTs) / 1e6,
                    request.get(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE),
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AF_MODE),
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AF_STATE),
                    result.get(android.hardware.camera2.CaptureResult.LENS_FOCUS_DISTANCE) ?: -1f,
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AE_LOCK),
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AE_STATE),
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AWB_LOCK),
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AWB_STATE),
                    (result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L) / 1e6,
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)))
            }
            lastTs = ts
        }
    }

    companion object {
        private const val TAG = "VolksKameraCam"
    }
}
