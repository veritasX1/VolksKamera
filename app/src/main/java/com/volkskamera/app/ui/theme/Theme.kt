package com.volkskamera.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Farben aus RetroCam übernommen – das Gelb der Auswahl-Pillen ist ausdrücklich gewünscht
val FilmBlack = Color(0xFF000000)
val FilmSurface = Color(0xFF161616)
val FilmWhite = Color(0xFFF2ECE2)
val FilmAccent = Color(0xFFE8B94A)
val FilmRed = Color(0xFFA1382E)

private val FilmColorScheme = darkColorScheme(
    background = FilmBlack,
    surface = FilmSurface,
    primary = FilmAccent,
    onPrimary = Color.Black,
    onBackground = FilmWhite,
    onSurface = FilmWhite,
    secondary = FilmRed,
)

@Composable
fun VolksKameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FilmColorScheme, content = content)
}
