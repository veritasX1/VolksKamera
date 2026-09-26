package com.volkskamera.app.data

import android.content.Context
import com.volkskamera.app.render.FilmLook
import com.volkskamera.app.render.HumType
import com.volkskamera.app.render.MicProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Eine gespeicherte Kombination: Film (oder eigene LUT), Farbfilter und Ton (Mikrofon, Rauschen,
 * Knacken, Brummen). Teilbar als kurzer Text, den jede Volkskamera wieder einlesen kann.
 */
data class Combo(
    val name: String,
    val filmId: String?,
    val customLutName: String?,
    val bwFilter: String,
    val mic: String?,
    val micNoise: Float, val micGate: Float, val micDrive: Float, val micBandwidth: Float, val micAgc: Float,
    val crackleOn: Boolean, val crackleAmount: Float, val crackleDensity: Float,
    val humOn: Boolean, val humType: String, val humFreq: Float, val humLevel: Float,
) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("film", filmId ?: "").put("eigeneLut", customLutName ?: "")
        .put("filter", bwFilter).put("mikro", mic ?: "")
        .put("rauschen", micNoise).put("sperre", micGate).put("verzerrung", micDrive).put("bandbreite", micBandwidth).put("automatik", micAgc)
        .put("knacken", crackleOn).put("knackenStaerke", crackleAmount).put("knackenHaeufigkeit", crackleDensity)
        .put("brummen", humOn).put("brummArt", humType).put("brummHz", humFreq).put("brummPegel", humLevel)

    /** Teilbarer Text: Kopfzeile + eine Zeile JSON */
    fun toShareText(filmLabel: String?): String = buildString {
        appendLine(com.volkskamera.app.tf("Volkskamera-Kombination „%s“", name))
        filmLabel?.let { appendLine(com.volkskamera.app.tf("Film: %s", it)) }
        if (bwFilter != "KEINER") appendLine(com.volkskamera.app.tf("Filter: %s", runCatching { com.volkskamera.app.render.BwFilter.valueOf(bwFilter).label }.getOrDefault(bwFilter)))
        mic?.let { m -> runCatching { MicProfile.valueOf(m) }.getOrNull()?.let { appendLine(com.volkskamera.app.tf("Mikrofon: %s", it.label)) } }
        appendLine()
        appendLine(com.volkskamera.app.t("In der Volkskamera einfügen: Einstellungen → ★ Kombinationen → Einfügen."))
        appendLine("App: https://volkskamera.goip.de")
        append(MARK); append(toJson().toString())
    }

    companion object {
        const val MARK = "VK1:"

        fun fromJson(o: JSONObject) = Combo(
            o.optString("name", "Kombination"), o.optString("film").ifEmpty { null }, o.optString("eigeneLut").ifEmpty { null },
            o.optString("filter", "KEINER"), o.optString("mikro").ifEmpty { null },
            o.optDouble("rauschen", 0.2).toFloat(), o.optDouble("sperre", 0.3).toFloat(), o.optDouble("verzerrung", 0.3).toFloat(),
            o.optDouble("bandbreite", 0.0).toFloat(), o.optDouble("automatik", 0.0).toFloat(),
            o.optBoolean("knacken"), o.optDouble("knackenStaerke", 0.5).toFloat(), o.optDouble("knackenHaeufigkeit", 0.4).toFloat(),
            o.optBoolean("brummen"), o.optString("brummArt", HumType.NETZ.name), o.optDouble("brummHz", 50.0).toFloat(),
            o.optDouble("brummPegel", 0.2).toFloat(),
        )

        /** Aus geteiltem Text lesen (Kennung „VK1:“ irgendwo im Text); null = keine Kombination */
        fun parse(text: String): Combo? {
            val i = text.indexOf(MARK); if (i < 0) return null
            return runCatching { fromJson(JSONObject(text.substring(i + MARK.length).trim().substringBefore('\n'))) }.getOrNull()
        }

        fun from(name: String, look: FilmLook, filmId: String?, customLutName: String?, bwFilter: String) = Combo(
            name, filmId, customLutName, bwFilter, look.mic?.name,
            look.micNoise, look.micGate, look.micDrive, look.micBandwidth, look.micAgc,
            look.crackleOn, look.crackleAmount, look.crackleDensity,
            look.bgHumOn, look.bgHumType.name, look.bgHumFreq, look.bgHumLevel,
        )

        private const val KEY = "kombinationen"
        private fun prefs(c: Context) = c.getSharedPreferences("volkskamera", Context.MODE_PRIVATE)
        fun load(c: Context): List<Combo> = runCatching {
            val a = JSONArray(prefs(c).getString(KEY, "[]"))
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrElse { emptyList() }
        fun store(c: Context, list: List<Combo>) {
            prefs(c).edit().putString(KEY, JSONArray(list.map { it.toJson() }).toString()).apply()
        }
    }
}
