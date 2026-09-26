package com.volkskamera.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Der (gebackene oder eigene) Look eines Films: LUT-Asset + Beschreibung + Quellen. */
data class FilmLookInfo(
    val lut: String,            // Asset-Pfad der LUT ("luts_film/<id>.png")
    val beschreibung: String,
    val quelle: String,         // Herkunft (Datenblatt-Zahlen vs. hergeleitet)
    val quellen: List<String>,  // Links
    val familie: String,
    val korn: String,           // "fein" | "mittel" | "kräftig" | "grob"
    val halation: Boolean,      // spatial im Renderer (CineStill/Phoenix)
)

/**
 * Ein Filmmaterial aus dem Katalog (film_catalog.json, aus der Materialliste erzeugt).
 * Der Look (LUT/Kurven) wird später ergänzt; hier steckt nur die Beschreibung.
 */
data class FilmStock(
    val hersteller: String,
    val name: String,
    val variante: String,
    val breite: Int,            // 8, 16, 35 (0 = unbekannt)
    val farbeSw: String,        // "Farbe" | "S/W"
    val negativUmkehr: String,  // "Negativ" | "Umkehr"
    val materialtyp: String,
    val iso: Int?,              // Film-Empfindlichkeit (ASA = ISO-Zahl); null = ohne Normangabe
    val isoRoh: String,
    val jahr: Int?,             // Jahr der Ersterscheinung
    val status: String,
    val anmerkung: String,
    val look: FilmLookInfo? = null,
) {
    /** Anzeigename der Variante: Variante/Code, sonst der Name. */
    val variantLabel: String get() = variante.ifBlank { name }
    /** Eindeutige ID für Speicherung/LUT-Zuordnung. */
    val id: String get() = listOf(hersteller, name, variante, breite.toString())
        .joinToString("_").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val breiteLabel: String get() = if (breite > 0) "$breite mm" else "?"
    /** „Hersteller Variante“, ohne den Hersteller zu doppeln („Agfa Agfa Superpan“). */
    val fullLabel: String get() = if (variantLabel.startsWith(hersteller.substringBefore(' '))) variantLabel else "$hersteller $variantLabel"
}

/** Der komplette Filmkatalog, gruppiert nach Hersteller → Breite → Variante. */
class FilmCatalog(val films: List<FilmStock>) {

    /** Hersteller alphabetisch, aber "Kodak"/"Eastman Kodak" etc. so wie in den Daten. */
    val hersteller: List<String> by lazy { films.map { it.hersteller }.distinct().sorted() }

    fun breitenVon(h: String): List<Int> =
        films.filter { it.hersteller == h }.map { it.breite }.distinct().sortedDescending()

    fun variantenVon(h: String, breite: Int): List<FilmStock> =
        films.filter { it.hersteller == h && it.breite == breite }
            .sortedWith(compareBy({ it.jahr ?: 9999 }, { it.variantLabel }))

    fun byId(id: String): FilmStock? = films.firstOrNull { it.id == id }

    companion object {
        @Volatile private var cached: FilmCatalog? = null

        fun load(context: Context): FilmCatalog = cached ?: synchronized(this) {
            cached ?: run {
                // Look-Zuordnung (film_looks.json) laden
                val looks = HashMap<String, FilmLookInfo>()
                runCatching {
                    val lo = JSONObject(context.assets.open("film_looks.json").bufferedReader().use { it.readText() })
                    for (id in lo.keys()) {
                        val o = lo.getJSONObject(id)
                        val q = o.optJSONArray("quellen")
                        looks[id] = FilmLookInfo(
                            lut = o.optString("lut"), beschreibung = o.optString("beschreibung"),
                            quelle = o.optString("quelle"),
                            quellen = (0 until (q?.length() ?: 0)).map { q!!.getString(it) },
                            familie = o.optString("familie"), korn = o.optString("korn", "mittel"),
                            halation = o.optBoolean("halation", false),
                        )
                    }
                }
                val arr = JSONArray(context.assets.open("film_catalog.json").bufferedReader().use { it.readText() })
                val seen = HashSet<String>()
                val list = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val f = FilmStock(
                        hersteller = o.optString("hersteller"),
                        name = o.optString("name"),
                        variante = o.optString("variante"),
                        breite = o.optInt("breite", 0),
                        farbeSw = o.optString("farbe_sw"),
                        negativUmkehr = o.optString("negativ_umkehr"),
                        materialtyp = o.optString("materialtyp"),
                        iso = if (o.isNull("iso")) null else o.optInt("iso"),
                        isoRoh = o.optString("iso_roh"),
                        jahr = if (o.isNull("jahr")) null else o.optInt("jahr"),
                        status = o.optString("status"),
                        anmerkung = o.optString("anmerkung"),
                    )
                    if (!seen.add(f.id)) return@mapNotNull null   // Doppel-Einträge der Liste entdoppeln
                    f.copy(look = looks[f.id])
                }
                FilmCatalog(list).also { cached = it }
            }
        }
    }
}
