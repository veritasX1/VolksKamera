package com.volkskamera.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Eine Farb-LUT als 33er-Streifen (1089x33 PNG, Kachel = Blau, x = Rot, y = Grün). */
data class LutAsset(val id: String, val name: String, val pack: String, val file: String)

/** Ein Effekt als WebP-Bildfolge (Korn, Staub, Kratzer, Leaks, Rahmen). */
data class SequenceAsset(
    val id: String,
    val name: String,
    val category: String,
    val folder: String,
    val fps: Int,
    /** overlay | multiply | screen | key | scan */
    val blend: String,
    val strength: Float,
    val frames: List<String>,
    val gray: Boolean = false,
    /** Greenscreen-Farbe bei blend = key, als 0xRRGGBB */
    val keyColor: Int? = null,
    /** Rahmen: Bildfenster x0,y0,x1,y1,Radius in 1920x1080-Koordinaten */
    val gate: IntArray? = null,
    val frameBlack: Float = 0.4f,
) {
    val isScratch get() = category == "dirt" && blend == "screen"
}

/** Liest assets.json, den Katalog aus prepare_assets.py. */
class AssetCatalog private constructor(
    val luts: List<LutAsset>,
    val grains: List<SequenceAsset>,
    val dirt: List<SequenceAsset>,
    val leaks: List<SequenceAsset>,
    val frames: List<SequenceAsset>,
    val burns: List<SequenceAsset>,
) {
    /** LUT-Pakete in Katalog-Reihenfolge, für die zweistufige Auswahl Paket -> Look. */
    val lutPacks: List<String> = luts.map { it.pack }.distinct()

    fun lutsIn(pack: String) = luts.filter { it.pack == pack }
    fun lut(id: String?) = luts.firstOrNull { it.id == id }
    fun find(id: String?) = (grains + dirt + leaks + frames + burns).firstOrNull { it.id == id }

    /** Selbst erzeugte Leaks zuerst, sie sind auf Schwarz und damit am saubersten. */
    val leaksSorted get() = leaks.sortedBy { if (it.id.startsWith("leak_")) 0 else 1 }

    /** Nur Rahmen mit Bildfenster – die dunklen Clips (Perforation auf Schwarz) sind keine Rahmen. */
    val gatedFrames get() = frames.filter { it.gate != null }

    companion object {
        fun load(context: Context): AssetCatalog {
            val json = context.assets.open("assets.json").bufferedReader().use { JSONObject(it.readText()) }
            val luts = json.optJSONArray("luts").objects().map {
                LutAsset(it.getString("id"), it.getString("name"), it.getString("paket"), it.getString("datei"))
            }
            fun seq(key: String) = json.optJSONArray(key).objects().map { sequence(context, it) }
            return AssetCatalog(luts, seq("grain"), seq("dirt"), seq("leaks"), seq("frames"), seq("burns"))
        }

        private fun sequence(context: Context, o: JSONObject): SequenceAsset {
            val folder = o.getString("ordner")
            val frames = context.assets.list(folder).orEmpty().filter { it.endsWith(".webp") }.sorted()
                .map { "$folder/$it" }
            val gate = o.optJSONArray("gate")?.let { a -> IntArray(a.length()) { a.getInt(it) } }
            val key = o.optString("key_farbe", "").removePrefix("#").takeIf { it.length == 6 }?.toInt(16)
            return SequenceAsset(
                id = o.getString("id"),
                name = o.getString("name"),
                category = o.getString("kategorie"),
                folder = folder,
                fps = o.optInt("fps", 12),
                blend = o.optString("blend", "overlay"),
                strength = o.optDouble("staerke", 0.7).toFloat(),
                frames = frames,
                gray = o.optBoolean("graustufen", false),
                keyColor = key,
                gate = gate,
                frameBlack = o.optDouble("frame_black", 0.4).toFloat(),
            )
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
