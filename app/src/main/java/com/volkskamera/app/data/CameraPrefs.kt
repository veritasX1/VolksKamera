package com.volkskamera.app.data

import android.content.Context

/** Merkt sich die Kamera-Bedieneinstellungen über Neustarts (Objektiv, Belichtungszeit, Weißabgleich). */
object CameraPrefs {
    private const val P = "camera_prefs"
    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun lensIdx(c: Context) = p(c).getInt("lens", 0)
    /** Belichtungszeit als Anzeigetext („1/50“) – robust gegen Änderungen der Zeitenliste */
    fun shutter(c: Context) = p(c).getString("shutter_label", "1/50") ?: "1/50"
    fun setShutter(c: Context, label: String) { p(c).edit().putString("shutter_label", label).apply() }
    fun resolution(c: Context) = p(c).getInt("resolution", 1080)
    fun setResolution(c: Context, r: Int) { p(c).edit().putInt("resolution", r).apply() }
    fun mirrorFront(c: Context) = p(c).getBoolean("mirror_front", true)
    fun setMirrorFront(c: Context, m: Boolean) { p(c).edit().putBoolean("mirror_front", m).apply() }
    fun wbIdx(c: Context) = p(c).getInt("wb", 0)

    fun setLens(c: Context, lens: Int) { p(c).edit().putInt("lens", lens).apply() }
}
