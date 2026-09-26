package com.volkskamera.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject

/** Eine mitgelieferte Gehäuse-Textur (ambientCG, CC0) – Farbe + Oberfläche für das Licht. */
data class HousingTexture(
    val id: String,
    val name: String,
    /** „Leder“, „Stoff“, „Metall“ */
    val kategorie: String,
    /** Metallanteil 0..1 (Glanz in Materialfarbe) */
    val metall: Float,
    val quelle: String,
    val lizenz: String,
)

/** Katalog aus assets/gehaeuse/texturen.json (erzeugt von tools/texturen.py). */
object HousingTextures {
    @Volatile private var cached: List<HousingTexture>? = null
    private val ORDER = listOf("Leder", "Stoff", "Holz", "Metall", "Stein", "Fliesen")
    /** Kategorien, die es tatsächlich gibt, in fester Reihenfolge */
    fun kategorien(context: Context) = all(context).map { it.kategorie }.distinct().sortedBy { ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } }

    fun all(context: Context): List<HousingTexture> = cached ?: synchronized(this) {
        cached ?: runCatching {
            val o = JSONObject(context.assets.open("gehaeuse/texturen.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().map { id ->
                val t = o.getJSONObject(id)
                HousingTexture(id, t.optString("name", id), t.optString("kategorie", "Material"),
                    t.optDouble("metall", 0.0).toFloat(), t.optString("quelle"), t.optString("lizenz"))
            }.sortedBy { it.name }.toList()
        }.getOrElse { emptyList() }.also { cached = it }
    }

    fun byId(context: Context, id: String): HousingTexture? = all(context).firstOrNull { it.id == id }

    private fun bitmap(context: Context, path: String): Bitmap =
        context.assets.open(path).use { BitmapFactory.decodeStream(it) } ?: error("$path unlesbar")

    fun colorMap(context: Context, id: String) = bitmap(context, "gehaeuse/${id}_farbe.webp")
    fun surfaceMap(context: Context, id: String) = bitmap(context, "gehaeuse/${id}_flaeche.webp")
    fun previewPath(id: String) = "gehaeuse/${id}_vorschau.jpg"
}
