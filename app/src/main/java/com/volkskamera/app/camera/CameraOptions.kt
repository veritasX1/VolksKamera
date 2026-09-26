package com.volkskamera.app.camera

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector

/**
 * Ein Objektiv des Geräts (Hauptkamera, Weitwinkel, Front), beschriftet nach
 * intrinsischem Zoom: 1,0 = Hauptkamera, darunter Weitwinkel (g84: 0,7), darüber Tele.
 * Übernommen aus RetroCam (CameraOption.kt), dort auf dem g84 erprobt.
 */
data class LensOption(
    val cameraInfo: CameraInfo,
    val lensFacing: Int,
    val zoomRatio: Float,
    val label: String,
) {
    fun toSelector(): CameraSelector = CameraSelector.Builder()
        .addCameraFilter { infos -> infos.filter { it == cameraInfo } }
        .build()
}

@OptIn(ExperimentalCamera2Interop::class)
private fun isNormalCamera(info: CameraInfo): Boolean {
    // Tiefen-/Hilfssensoren, die manche Handys als eigene Kamera melden, ausfiltern
    val caps = Camera2CameraInfo.from(info)
        .getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return true
    return caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
}

@OptIn(ExperimentalCamera2Interop::class)
private fun pixelCount(info: CameraInfo): Long {
    val s = Camera2CameraInfo.from(info)
        .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return 0L
    return s.width.toLong() * s.height.toLong()
}

fun buildLensOptions(infos: List<CameraInfo>): List<LensOption> {
    val usable = infos.filter { isNormalCamera(it) }
    // gleicher Zoom mehrfach (Makro/Tiefe neben der Hauptkamera): nur die höchstauflösende behalten
    val back = usable.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        .groupBy { "%.2f".format(java.util.Locale.ROOT, it.intrinsicZoomRatio) }
        .values.map { g -> g.maxBy { pixelCount(it) } }
        .sortedByDescending { it.intrinsicZoomRatio in 0.95f..1.05f }
        .map { LensOption(it, CameraSelector.LENS_FACING_BACK, it.intrinsicZoomRatio, zoomLabel(it.intrinsicZoomRatio)) }
    val front = usable.filter { it.lensFacing == CameraSelector.LENS_FACING_FRONT }.take(1)
        .map { LensOption(it, CameraSelector.LENS_FACING_FRONT, it.intrinsicZoomRatio, "Front") }
    return back + front
}

/** "1,0" / "0,7" – deutsche Schreibweise wie in der Skizze */
private fun zoomLabel(z: Float) = if (z in 0.95f..1.05f) "1,0" else "%.1f".format(java.util.Locale.GERMANY, z)
