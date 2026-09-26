package com.volkskamera.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Metall-Oberfläche: heller -> dunkler Verlauf (für Blende, Ring, Icons). */
enum class MetalFinish(val label: String, val light: Color, val dark: Color, val onColor: Color) {
    SILBER("Silber", Color(0xFFEDEFF1), Color(0xFF9A9EA2), Color.Black),
    GOLD("Gold", Color(0xFFF7E7A0), Color(0xFFC9A227), Color.Black),
    ALTGOLD("Altgold", Color(0xFFD8C48A), Color(0xFF8C6E2E), Color.Black),
    BRONZE("Bronze", Color(0xFFD9A566), Color(0xFF7A4A22), Color.Black),
    ANTHRAZIT("Anthrazit", Color(0xFF6A6E72), Color(0xFF26282A), Color.White),
    ROSTIG("Rostig", Color(0xFFC0703A), Color(0xFF5E2A14), Color.White),
    BRUENIERT("Brüniert", Color(0xFF46515C), Color(0xFF12161B), Color.White);

    fun brush() = Brush.linearGradient(listOf(light, dark))
}

/** Auslöser-/Knopf-Stil: entweder Plastik (eine Farbe) oder eine Metall-Oberfläche. */
enum class ButtonStyle(val label: String, val plastic: Color?, val metal: MetalFinish?) {
    PLASTIK_ROT("Rot", Color(0xFFB5342B), null),
    PLASTIK_SCHWARZ("Schwarz", Color(0xFF1C1C1C), null),
    PLASTIK_ELFENBEIN("Elfenbein", Color(0xFFEDE4CF), null),
    PLASTIK_BLAU("Blau", Color(0xFF2B5AA1), null),
    PLASTIK_GRUEN("Grün", Color(0xFF2E7D4F), null),
    PLASTIK_ORANGE("Orange", Color(0xFFD1751F), null),
    METALL_SILBER("Metall Silber", null, MetalFinish.SILBER),
    METALL_GOLD("Metall Gold", null, MetalFinish.GOLD),
    METALL_ANTHRAZIT("Metall Anthrazit", null, MetalFinish.ANTHRAZIT),
    METALL_BRUENIERT("Metall Brüniert", null, MetalFinish.BRUENIERT);
}

/** Gehäuse-Hintergrund: eine mitgelieferte Material-Textur (ambientCG) oder ein eigenes Bild. */
data class Housing(
    val bgAsset: String = DEFAULT_TEXTURE,       // Textur-ID aus gehaeuse/texturen.json; leer wenn eigenes Bild
    val customUri: String? = null,               // content:// eines Galeriebilds
    val metal: MetalFinish = MetalFinish.SILBER,
    val button: ButtonStyle = ButtonStyle.PLASTIK_ROT,
    val titleMetal: MetalFinish = MetalFinish.GOLD,
    /** Licht folgt Neigung und Schwenk des Handys (sonst fest von oben links) */
    val light: Boolean = true,
    /** Lichtstärke 0 … 1,5 */
    val lightStrength: Float = 1f,
    val lightColor: LightColor = LightColor.TAGESLICHT,
    /** Spiegelung: eigenes Frontkamera-Foto statt Studio-Umgebung */
    val ownEnvironment: Boolean = false,
    /** Schriftzug über dem Sucher (frei wählbar) */
    val titleText: String = DEFAULT_TITLE,
    val titleFont: TitleFont = TitleFont.SCHREIBSCHRIFT,
) {
    companion object {
        const val DEFAULT_TEXTURE = "Leather032"
        const val DEFAULT_TITLE = "Volkskamera"
        private const val PREFS = "housing"

        fun load(c: Context): Housing {
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Housing(
                // alte Leder-Fotos (leather_*.jpg, Lizenz unklar) gibt es nicht mehr -> Standard-Textur
                bgAsset = (p.getString("bg", DEFAULT_TEXTURE) ?: DEFAULT_TEXTURE).let { if (it.endsWith(".jpg")) DEFAULT_TEXTURE else it },
                customUri = p.getString("uri", null)?.ifBlank { null },
                metal = runCatching { MetalFinish.valueOf(p.getString("metal", "SILBER")!!) }.getOrDefault(MetalFinish.SILBER),
                button = runCatching { ButtonStyle.valueOf(p.getString("button", "PLASTIK_ROT")!!) }.getOrDefault(ButtonStyle.PLASTIK_ROT),
                titleMetal = runCatching { MetalFinish.valueOf(p.getString("titleMetal", "GOLD")!!) }.getOrDefault(MetalFinish.GOLD),
                light = p.getBoolean("light", true),
                lightStrength = p.getFloat("lightStrength", 1f),
                lightColor = runCatching { LightColor.valueOf(p.getString("lightColor", "TAGESLICHT")!!) }.getOrDefault(LightColor.TAGESLICHT),
                ownEnvironment = p.getBoolean("ownEnv", false),
                titleText = p.getString("titleText", DEFAULT_TITLE) ?: DEFAULT_TITLE,
                titleFont = runCatching { TitleFont.valueOf(p.getString("titleFont", "SCHREIBSCHRIFT")!!) }.getOrDefault(TitleFont.SCHREIBSCHRIFT),
            )
        }

        fun save(c: Context, h: Housing) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("bg", h.bgAsset).putString("uri", h.customUri ?: "")
                .putString("metal", h.metal.name).putString("button", h.button.name)
                .putString("titleMetal", h.titleMetal.name).putBoolean("light", h.light)
                .putFloat("lightStrength", h.lightStrength).putString("lightColor", h.lightColor.name)
                .putBoolean("ownEnv", h.ownEnvironment).putString("titleText", h.titleText)
                .putString("titleFont", h.titleFont.name).apply()
        }
    }
}

/** Schriftarten für den Schriftzug. */
enum class TitleFont(val label: String, val res: Int) {
    SCHREIBSCHRIFT("Schreibschrift", com.volkskamera.app.R.font.great_vibes),
    PINSEL("Pinselschrift", com.volkskamera.app.R.font.kaushan_script),
}
