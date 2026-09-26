package com.volkskamera.app.data

import android.content.Context

/** Merkt sich die Kamera-Bedieneinstellungen über Neustarts (Objektiv, Belichtungszeit, Weißabgleich). */
object CameraPrefs {
    private const val P = "camera_prefs"
    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun lensIdx(c: Context) = p(c).getInt("lens", 0)
    fun shutterIdx(c: Context) = p(c).getInt("shutter", 0)
    fun wbIdx(c: Context) = p(c).getInt("wb", 0)

    fun set(c: Context, lens: Int, shutter: Int, wb: Int) {
        p(c).edit().putInt("lens", lens).putInt("shutter", shutter).putInt("wb", wb).apply()
    }
}
