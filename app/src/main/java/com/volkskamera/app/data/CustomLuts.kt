package com.volkskamera.app.data

import android.content.Context
import com.volkskamera.app.render.LutEdit
import com.volkskamera.app.render.LutFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Eine eigene LUT: Basis-LUT eines Films + Bearbeitung, gebacken in eine Datei.
 * Die Bearbeitung bleibt gespeichert, damit man sie später weiter verändern kann.
 */
data class CustomLut(
    val id: String,
    val name: String,
    /** Film, von dem die LUT abstammt (für Korn/Halation und die Anzeige) */
    val baseFilmId: String?,
    /** Asset-Pfad der Basis-LUT */
    val baseLut: String,
    val edit: LutEdit,
    /** absoluter Pfad der gebackenen LUT */
    val file: String,
)

/** Speichert eigene LUTs in files/luts_eigen/ plus eine Liste in den Einstellungen. */
object CustomLuts {
    private const val PREFS = "volkskamera"
    private const val KEY = "eigene_luts"

    private fun dir(context: Context) = File(context.filesDir, "luts_eigen").apply { mkdirs() }

    fun load(context: Context): List<CustomLut> {
        val a = runCatching { JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")) }
            .getOrElse { JSONArray() }
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                CustomLut(o.getString("id"), o.getString("name"), o.optString("baseFilmId").ifEmpty { null },
                    o.getString("baseLut"), editFromJson(o.getJSONObject("edit")), o.getString("file"))
            }.getOrNull()?.takeIf { File(it.file).isFile }
        }
    }

    private fun store(context: Context, list: List<CustomLut>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("baseFilmId", it.baseFilmId ?: "")
                .put("baseLut", it.baseLut).put("edit", editToJson(it.edit)).put("file", it.file))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    /**
     * Backen und speichern. Gleiche [id] überschreibt; jede Speicherung bekommt eine neue Datei,
     * damit zwischengespeicherte Texturen der alten Fassung nicht weiterverwendet werden.
     */
    fun save(context: Context, existing: List<CustomLut>, id: String?, name: String, baseFilmId: String?,
             baseLut: String, edit: LutEdit): Pair<List<CustomLut>, CustomLut> {
        val cube = LutFile.bake(LutFile.load(context, baseLut), edit)
        val newId = id ?: "eigen_${System.currentTimeMillis()}"
        val file = File(dir(context), "${newId}_${System.currentTimeMillis()}.png")
        LutFile.write(cube, file)
        existing.firstOrNull { it.id == newId }?.let { File(it.file).delete() }
        val lut = CustomLut(newId, name, baseFilmId, baseLut, edit, file.absolutePath)
        val list = existing.filterNot { it.id == newId } + lut
        store(context, list)
        return list to lut
    }

    fun delete(context: Context, existing: List<CustomLut>, id: String): List<CustomLut> {
        existing.firstOrNull { it.id == id }?.let { File(it.file).delete() }
        return existing.filterNot { it.id == id }.also { store(context, it) }
    }

    private fun editToJson(e: LutEdit) = JSONObject()
        .put("exposure", e.exposure).put("contrast", e.contrast).put("blacks", e.blacks).put("whites", e.whites)
        .put("temperature", e.temperature).put("tint", e.tint).put("saturation", e.saturation)
        .put("shadowHue", e.shadowHue).put("shadowAmount", e.shadowAmount)
        .put("highlightHue", e.highlightHue).put("highlightAmount", e.highlightAmount)
        .put("red", e.red).put("green", e.green).put("blue", e.blue)

    private fun editFromJson(o: JSONObject): LutEdit {
        fun f(k: String, d: Float = 0f) = o.optDouble(k, d.toDouble()).toFloat()
        return LutEdit(f("exposure"), f("contrast"), f("blacks"), f("whites"), f("temperature"), f("tint"),
            f("saturation"), f("shadowHue", 210f), f("shadowAmount"), f("highlightHue", 40f), f("highlightAmount"),
            f("red"), f("green"), f("blue"))
    }
}
