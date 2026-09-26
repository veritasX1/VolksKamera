package com.volkskamera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.volkskamera.app.ui.theme.lightShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volkskamera.app.ui.theme.FilmWhite
import com.volkskamera.app.ui.theme.MetalFinish

/**
 * Mechanisches Zählwerk im Stil alter Zählwerke: graviertes Label, darunter [drums] einzelne
 * Zahlenrollen (je ein Zeichen, feste Größe) zwischen zwei Schrauben. Die Breite hängt nur von der
 * Anzahl der Rollen ab – sie ändert sich nie mit dem Wert. Der Wert wird rechtsbündig aufgefüllt
 * ([pad], z. B. ' ' oder '0'). [prefix] steht fest aufgedruckt vor den Rollen (z. B. „1/“ bei der Zeit),
 * [prefixShown] = false blendet ihn aus, ohne dass sich die Breite ändert. Antippen schaltet weiter.
 */
@Composable
fun CounterField(
    title: String,
    value: String,
    metal: MetalFinish,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    drums: Int = value.length,
    pad: Char = ' ',
    prefix: String? = null,
    prefixShown: Boolean = true,
    onTap: () -> Unit,
) {
    val text = value.takeLast(drums).padStart(drums, pad)
    val ink = FilmWhite.copy(alpha = if (enabled) 1f else 0.4f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title, color = metal.light.copy(alpha = if (enabled) 0.95f else 0.4f),
            fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(
                color = Color(0xE6000000), offset = androidx.compose.ui.geometry.Offset(0f, 1f), blurRadius = 4f)),
        )
        Spacer(Modifier.height(2.dp))
        // Plastik-Gehäuse (Schatten vom Licht weg), darin Schrauben + eingelassene Rollen
        Box(
            Modifier
                .lightShadow(RoundedCornerShape(7.dp), 4.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3B3B3D), Color(0xFF161617))))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(7.dp))
                .clickable(enabled = enabled, onClick = onTap)
                .padding(horizontal = 5.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Screw(metal)
                Spacer(Modifier.width(4.dp))
                if (prefix != null) {
                    Box(Modifier.width((prefix.length * 8 + 2).dp), contentAlignment = Alignment.CenterEnd) {
                        if (prefixShown) Text(prefix, color = ink.copy(alpha = ink.alpha * 0.85f), fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                // eingelassenes Fenster mit den einzelnen Rollen
                Row(
                    Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF000000))
                        .border(1.dp, Color(0xFF000000), RoundedCornerShape(3.dp)).padding(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                ) {
                    text.forEach { ch -> Drum(ch, ink) }
                }
                Spacer(Modifier.width(4.dp))
                Screw(metal)
            }
        }
    }
}

/** Eine Zahlenrolle: gewölbt (oben/unten dunkler), ein Zeichen, feste Größe. */
@Composable
private fun Drum(ch: Char, ink: Color) {
    Box(
        Modifier.size(width = 12.dp, height = 24.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF2A2A2A), Color(0xFF1A1A1A), Color(0xFF030303)))),
        contentAlignment = Alignment.Center,
    ) {
        if (ch != ' ') Text(ch.toString(), color = ink, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Kleine Schlitzschraube in Metall-Optik. */
@Composable
private fun Screw(metal: MetalFinish) {
    Box(
        Modifier.size(9.dp).clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(metal.light, metal.dark))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 6.dp, height = 1.5.dp).background(Color(0x99000000)))
    }
}
