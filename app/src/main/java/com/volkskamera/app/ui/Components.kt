package com.volkskamera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite

/** Die gelben Auswahl-Pillen aus RetroCam: gewählt = Gelb mit schwarzer Schrift. */
@Composable
fun Pill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    small: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            // Achtung: nicht .copy(alpha = …) auf die halbtransparente Farbe – das überschreibt deren
            // eigene Transparenz (weiß auf weiß). Stattdessen die Deckkraft multiplizieren.
            .background(if (selected) FilmAccent.copy(alpha = alpha) else Color.White.copy(alpha = 0.2f * alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = when { big -> 24.dp; small -> 10.dp; else -> 14.dp },
                vertical = when { big -> 12.dp; small -> 4.dp; else -> 6.dp },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = (if (selected) Color.Black else FilmWhite).copy(alpha = alpha),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = when { big -> 16.sp; small -> 12.sp; else -> 14.sp },
        )
    }
}

@Composable
fun Section(title: String) {
    Text(title.uppercase(), color = FilmWhite.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
fun <T> PillRow(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, enabled: Boolean, onClick: (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item -> Pill(label(item), selected(item), enabled = enabled) { onClick(item) } }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FilmWhite.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.width(116.dp))
        Slider(value = value, onValueChange = onChange, enabled = enabled, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = FilmAccent, activeTrackColor = FilmAccent))
        Text("${(value * 100).toInt()} %", color = FilmWhite.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.padding(start = 8.dp).width(44.dp))
    }
}

/**
 * Regler mit Mitte 0 (−100 … +100) für Bildkorrekturen. Doppeltipp auf den Namen = zurück auf 0.
 * [negLabel]/[posLabel] beschriften die Enden (z.B. "unscharf" / "scharf").
 */
@Composable
fun BipolarSlider(label: String, value: Float, enabled: Boolean, negLabel: String? = null, posLabel: String? = null, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = FilmWhite.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.width(96.dp).pointerInput(enabled) {
                    detectTapGestures(onDoubleTap = { if (enabled) onChange(0f) })
                })
            Slider(value = value, onValueChange = { v -> onChange(if (kotlin.math.abs(v) < 0.04f) 0f else v) },
                valueRange = -1f..1f, enabled = enabled, modifier = Modifier.weight(1f),
                // Mitte = 0: keine "gefüllte" Spur von links, nur der Knopf zeigt den Wert
                colors = SliderDefaults.colors(thumbColor = FilmAccent, activeTrackColor = FilmWhite.copy(alpha = 0.25f),
                    inactiveTrackColor = FilmWhite.copy(alpha = 0.25f), activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent))
            val v = (value * 100).toInt()
            Text(if (v > 0) "+$v" else "$v", color = FilmWhite.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.padding(start = 8.dp).width(44.dp))
        }
        if (negLabel != null && posLabel != null) {
            Row(Modifier.padding(start = 96.dp, end = 52.dp)) {
                Text(negLabel, color = FilmWhite.copy(alpha = 0.45f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(posLabel, color = FilmWhite.copy(alpha = 0.45f), fontSize = 11.sp)
            }
        }
    }
}

/** Asset-Namen lesbar machen: "WHITE_SCRATCHES_05" -> "White Scratches 05", "leak_…" -> "★ …". */
fun pretty(name: String): String {
    // schon ein richtiger Anzeigename (z.B. "8 mm kräftig", "Ausgeblichen türkis") -> so lassen
    if ('_' !in name && name != name.uppercase() && !name.all { it.isDigit() }) return name
    var n = name.replace("Filmgrain_4KDCI_", "").replace("_24fps", "").replace("_medium", "")
    val own = n.startsWith("leak_")
    n = n.removePrefix("leak_").replace('_', ' ').replace('-', ' ').trim()
    if (n.firstOrNull()?.isDigit() == true && n.split(' ').all { it.all(Char::isDigit) }) n = "Clip ${n.split(' ').first()}"
    n = n.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    return if (own) "★ $n" else n
}
