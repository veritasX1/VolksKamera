package com.volkskamera.app.data

import android.content.Context
import com.volkskamera.app.render.AudioMode
import com.volkskamera.app.render.FilmLook
import com.volkskamera.app.render.FrameMode
import com.volkskamera.app.render.ProjectorModel
import com.volkskamera.app.render.Stage
import org.json.JSONArray
import org.json.JSONObject

/** Ein eigenes, gespeichertes Preset. */
data class UserPreset(val name: String, val look: FilmLook)

/**
 * Speichert Looks: den aktuellen (über Neustarts), eigene Presets und den Text-Export.
 * Assets werden per ID gespeichert und beim Laden wieder im Katalog nachgeschlagen –
 * fehlt ein Asset später (z.B. aussortiert), bleibt die Stufe einfach aus.
 */
object LookStore {
    private const val PREFS = "volkskamera"
    private const val KEY = "look"
    private const val KEY_PRESETS = "eigene_presets"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- aktueller Look ----------

    fun save(context: Context, look: FilmLook, preset: String?) {
        val o = toJson(look).put("preset", preset ?: "")
        prefs(context).edit().putString(KEY, o.toString()).apply()
    }

    /** Gespeicherter Look oder null (erster Start). */
    fun load(context: Context, c: AssetCatalog): Pair<FilmLook, String?>? {
        val s = prefs(context).getString(KEY, null) ?: return null
        return runCatching {
            val o = JSONObject(s)
            fromJson(o, c) to o.optString("preset", "").takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    // ---------- eigene Presets ----------

    fun loadPresets(context: Context, c: AssetCatalog): List<UserPreset> {
        val a = runCatching { JSONArray(prefs(context).getString(KEY_PRESETS, "[]")) }.getOrElse { JSONArray() }
        return (0 until a.length()).mapNotNull { i ->
            runCatching { val o = a.getJSONObject(i); UserPreset(o.getString("name"), fromJson(o.getJSONObject("look"), c)) }.getOrNull()
        }
    }

    fun savePresets(context: Context, presets: List<UserPreset>) {
        val a = JSONArray()
        presets.forEach { a.put(JSONObject().put("name", it.name).put("look", toJson(it.look))) }
        prefs(context).edit().putString(KEY_PRESETS, a.toString()).apply()
    }

    // ---------- Text-Export (eine Einstellung pro Zeile) ----------

    fun toText(name: String, look: FilmLook): String = buildString {
        appendLine("# VolksKamera-Preset")
        appendLine("# Eine Einstellung pro Zeile: schlüssel = wert. Regler 0…1 bzw. −1…+1, Assets per ID.")
        appendLine("name = $name")
        val o = toJson(look)
        o.keys().asSequence().sorted().forEach { k ->
            val v = o.get(k)
            val text = when (v) {
                is JSONArray -> (0 until v.length()).joinToString(", ") { v.getString(it) }
                // Regler lesbar: 0.74085814 -> 0.741
                is Number -> String.format(java.util.Locale.ROOT, "%.3f", v.toDouble()).trimEnd('0').trimEnd('.').ifEmpty { "0" }
                else -> v.toString()
            }
            appendLine("$k = $text")
        }
    }

    /** Liest eine exportierte Textdatei; null, wenn es kein VolksKamera-Preset ist. */
    fun fromText(text: String, c: AssetCatalog): UserPreset? {
        val o = JSONObject()
        var name: String? = null
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }.forEach { line ->
            val k = line.substringBefore('=').trim()
            val v = line.substringAfter('=').trim()
            when (k) {
                "name" -> name = v
                "off" -> o.put("off", JSONArray(v.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                else -> o.put(k, v)   // JSONObject wandelt Text beim Lesen in Zahl/Wahrheitswert um
            }
        }
        if (o.length() == 0) return null
        return UserPreset(name ?: "Import", fromJson(o, c))
    }

    // ---------- gemeinsame Umwandlung ----------

    private fun toJson(look: FilmLook) = JSONObject().apply {
        put("lutFile", look.lutFile ?: "")
        put("lutMix", look.lutMix)
        put("grain", look.grain?.id ?: ""); put("grainAmount", look.grainAmount)
        put("dust", look.dust?.id ?: ""); put("dustAmount", look.dustAmount)
        put("scratch", look.scratch?.id ?: ""); put("scratchAmount", look.scratchAmount)
        put("leak", look.leak?.id ?: ""); put("leakAmount", look.leakAmount); put("leakFrequency", look.leakFrequency)
        put("frame", look.frame?.id ?: ""); put("frameMode", look.frameMode.name)
        put("frameTint", look.frameTint); put("frameBlack", look.frameBlack)
        put("halation", look.halation); put("soften", look.soften)
        put("flicker", look.flicker); put("weave", look.weave)
        put("mono", look.mono); put("finderLook", look.finderLook)
        put("aspect", look.aspect ?: -1f); put("targetFps", look.targetFps ?: -1f)
        put("burnEnd", look.burnEnd?.id ?: ""); put("countdown", look.countdown)
        put("audioMode", look.audioMode.name); put("aging", look.aging)
        put("projector", look.projector); put("projectorVolume", look.projectorVolume)
        put("projectorModel", look.projectorModel.name); put("projectorMaterial", look.projectorMaterial)
        put("projectorTone", look.projectorTone); put("projectorSyncFps", look.projectorSyncFps)
        put("projectorSpeed", look.projectorSpeed)
        put("bgNoiseOn", look.bgNoiseOn); put("bgNoiseType", look.bgNoiseType.name); put("bgNoiseLevel", look.bgNoiseLevel)
        put("bgHumOn", look.bgHumOn); put("bgHumType", look.bgHumType.name); put("bgHumFreq", look.bgHumFreq)
        put("bgHumLevel", look.bgHumLevel)
        put("crackleOn", look.crackleOn); put("crackleAmount", look.crackleAmount); put("crackleDensity", look.crackleDensity)
        put("mic", look.mic?.name ?: ""); put("micDrive", look.micDrive); put("micBandwidth", look.micBandwidth)
        put("micNoise", look.micNoise); put("micAgc", look.micAgc); put("micGate", look.micGate)
        put("projectorRecording", look.projectorRecording ?: "")
        put("fxSound", look.fxSound ?: ""); put("fxVolume", look.fxVolume); put("fxChain", look.fxChain)
        put("ambA", look.ambA ?: ""); put("ambALevel", look.ambALevel); put("ambAChain", look.ambAChain)
        put("ambB", look.ambB ?: ""); put("ambBLevel", look.ambBLevel); put("ambBChain", look.ambBChain)
        put("off", JSONArray(look.off.map { it.name }))
        put("brightness", look.brightness); put("contrast", look.contrast); put("sharpness", look.sharpness)
        put("temperature", look.temperature); put("shadows", look.shadows); put("highlights", look.highlights)
        put("saturation", look.saturation); put("vignette", look.vignette)
    }

    private fun fromJson(o: JSONObject, c: AssetCatalog): FilmLook {
        fun f(k: String, d: Float) = o.optDouble(k, d.toDouble()).toFloat()
        fun asset(k: String) = o.optString(k, "").takeIf { it.isNotEmpty() }?.let { c.find(it) }
        val d = FilmLook()
        return FilmLook(
            brightness = f("brightness", 0f), contrast = f("contrast", 0f), sharpness = f("sharpness", 0f),
            temperature = f("temperature", 0f), shadows = f("shadows", 0f), highlights = f("highlights", 0f),
            saturation = f("saturation", 0f), vignette = f("vignette", 0f),
            lutFile = o.optString("lutFile", "").takeIf {
                it.isNotEmpty() && (c.luts.any { l -> l.file == it } || it.startsWith("luts_film/") ||
                    (it.startsWith("/") && java.io.File(it).isFile))
            },
            lutMix = f("lutMix", d.lutMix),
            grain = asset("grain"), grainAmount = f("grainAmount", d.grainAmount),
            dust = asset("dust"), dustAmount = f("dustAmount", d.dustAmount),
            scratch = asset("scratch"), scratchAmount = f("scratchAmount", d.scratchAmount),
            leak = asset("leak"), leakAmount = f("leakAmount", d.leakAmount), leakFrequency = f("leakFrequency", d.leakFrequency),
            frame = asset("frame"),
            frameMode = runCatching { FrameMode.valueOf(o.optString("frameMode")) }.getOrDefault(d.frameMode),
            frameTint = f("frameTint", d.frameTint), frameBlack = f("frameBlack", d.frameBlack),
            halation = f("halation", d.halation), soften = f("soften", d.soften),
            flicker = f("flicker", d.flicker), weave = f("weave", d.weave),
            mono = o.optBoolean("mono", d.mono), finderLook = o.optBoolean("finderLook", d.finderLook),
            aspect = f("aspect", -1f).takeIf { it > 0f },
            targetFps = f("targetFps", -1f).takeIf { it > 0f },
            burnEnd = asset("burnEnd"), countdown = o.optBoolean("countdown", false),
            audioMode = runCatching { AudioMode.valueOf(o.optString("audioMode")) }.getOrDefault(d.audioMode),
            aging = f("aging", d.aging),
            projector = o.optBoolean("projector", d.projector), projectorVolume = f("projectorVolume", d.projectorVolume),
            projectorModel = runCatching { ProjectorModel.valueOf(o.optString("projectorModel")) }.getOrDefault(d.projectorModel),
            projectorMaterial = f("projectorMaterial", d.projectorMaterial), projectorTone = f("projectorTone", d.projectorTone),
            projectorSyncFps = o.optBoolean("projectorSyncFps", d.projectorSyncFps),
            projectorSpeed = f("projectorSpeed", d.projectorSpeed),
            bgNoiseOn = o.optBoolean("bgNoiseOn", false),
            bgNoiseType = runCatching { com.volkskamera.app.render.NoiseType.valueOf(o.optString("bgNoiseType")) }.getOrDefault(d.bgNoiseType),
            bgNoiseLevel = f("bgNoiseLevel", d.bgNoiseLevel),
            bgHumOn = o.optBoolean("bgHumOn", false),
            bgHumType = runCatching { com.volkskamera.app.render.HumType.valueOf(o.optString("bgHumType")) }.getOrDefault(d.bgHumType),
            bgHumFreq = f("bgHumFreq", d.bgHumFreq), bgHumLevel = f("bgHumLevel", d.bgHumLevel),
            crackleOn = o.optBoolean("crackleOn", false),
            crackleAmount = f("crackleAmount", d.crackleAmount), crackleDensity = f("crackleDensity", d.crackleDensity),
            mic = runCatching { com.volkskamera.app.render.MicProfile.valueOf(o.optString("mic")) }.getOrNull(),
            micDrive = f("micDrive", d.micDrive), micBandwidth = f("micBandwidth", d.micBandwidth),
            micNoise = f("micNoise", d.micNoise), micAgc = f("micAgc", d.micAgc), micGate = f("micGate", d.micGate),
            projectorRecording = o.optString("projectorRecording", "").takeIf { it.isNotEmpty() },
            fxSound = o.optString("fxSound", "").takeIf { it.isNotEmpty() },
            fxVolume = f("fxVolume", d.fxVolume), fxChain = o.optBoolean("fxChain", false),
            ambA = o.optString("ambA", "").takeIf { it.isNotEmpty() }, ambALevel = f("ambALevel", d.ambALevel),
            ambAChain = o.optBoolean("ambAChain", false),
            ambB = o.optString("ambB", "").takeIf { it.isNotEmpty() }, ambBLevel = f("ambBLevel", d.ambBLevel),
            ambBChain = o.optBoolean("ambBChain", false),
            off = o.optJSONArray("off")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { Stage.valueOf(a.getString(it)) }.getOrNull() }.toSet()
            } ?: emptySet(),
        )
    }
}
