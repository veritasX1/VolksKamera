package com.volkskamera.app

import android.content.Context
import org.json.JSONObject

/**
 * Übersetzung: Deutsch ist die Quellsprache im Code. Für Englisch, Französisch und Russisch liegen
 * Tabellen „deutscher Text → Übersetzung“ in assets/i18n/<code>.json. Fehlt ein Eintrag, bleibt der
 * deutsche Text stehen. Die Sprache folgt beim ersten Start dem System (bzw. der Download-Variante)
 * und ist in der App umschaltbar (Neustart der Oberfläche).
 */
object I18n {
    enum class Lang(val code: String, val label: String) {
        DE("de", "Deutsch"), EN("en", "English"), FR("fr", "Français"), RU("ru", "Русский");
    }

    @Volatile var lang: Lang = Lang.DE; private set
    @Volatile private var table: Map<String, String> = emptyMap()

    private fun prefs(c: Context) = c.getSharedPreferences("volkskamera", Context.MODE_PRIVATE)

    /** Beim Start: gewählte Sprache, sonst die der Download-Variante, sonst die des Systems. */
    fun init(c: Context) {
        val saved = prefs(c).getString("sprache", null)
        val fallback = BuildConfig.DEFAULT_LANG.ifEmpty { java.util.Locale.getDefault().language }
        val code = saved ?: fallback
        // unbekannte Systemsprache (z. B. Spanisch) -> Englisch
        lang = Lang.entries.firstOrNull { it.code == code } ?: Lang.EN
        table = load(c, lang)
    }

    fun choose(c: Context, l: Lang) {
        prefs(c).edit().putString("sprache", l.code).apply()
        lang = l
        table = load(c, l)
    }

    private fun load(c: Context, l: Lang): Map<String, String> {
        if (l == Lang.DE) return emptyMap()
        return runCatching {
            val o = JSONObject(c.assets.open("i18n/${l.code}.json").bufferedReader().use { it.readText() })
            buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } }
        }.getOrElse { emptyMap() }
    }

    fun t(de: String): String = if (lang == Lang.DE) de else table[de] ?: de
}

/** Übersetzen: deutscher Text als Schlüssel. */
fun t(de: String): String = I18n.t(de)

/** Übersetzen mit Platzhaltern (%1\$s, %2\$s … bzw. %s). */
fun tf(de: String, vararg args: Any?): String = I18n.t(de).format(*args)
