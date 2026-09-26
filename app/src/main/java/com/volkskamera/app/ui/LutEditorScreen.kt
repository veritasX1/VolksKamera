package com.volkskamera.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.data.CustomLut
import com.volkskamera.app.data.FilmCatalog
import com.volkskamera.app.data.FilmStock
import com.volkskamera.app.render.LutEdit
import com.volkskamera.app.render.LutFile
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * LUT-Editor: die LUT eines Films (oder eine eigene LUT) auf dem Referenzbild nachbearbeiten
 * und als eigene LUT speichern. Die Original-LUT des Films bleibt immer unverändert.
 * Gedrückt halten auf dem Bild = Film-Original zum Vergleich.
 */
@UnstableApi
@Composable
fun LutEditorScreen(
    vm: FilmViewModel,
    catalog: FilmCatalog,
    film: FilmStock?,
    custom: CustomLut?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val baseLut = custom?.baseLut ?: film?.look?.lut
    if (baseLut == null) { onBack(); return }
    var edit by remember { mutableStateOf(custom?.edit ?: LutEdit()) }
    var name by remember { mutableStateOf(custom?.name ?: "${film?.variantLabel ?: "Film"} (eigen)") }
    var showOriginal by remember { mutableStateOf(false) }

    // Referenzbild verkleinert (schnelle Vorschau) + Basis-Würfel
    val ref = vm.reference
    val small = remember(ref) {
        ref?.let { val s = 720f / maxOf(it.width, it.height); if (s < 1f) Bitmap.createScaledBitmap(it, (it.width * s).toInt(), (it.height * s).toInt(), true) else it }
    }
    var base by remember { mutableStateOf<FloatArray?>(null) }
    // Vorschau inkl. Farbfilter (bei S/W); gespeichert wird die Bearbeitung ohne Filter, er wird beim Filmen davorgeschaltet
    val baseFilm = film ?: custom?.baseFilmId?.let { catalog.byId(it) }
    LaunchedEffect(baseLut) {
        base = withContext(Dispatchers.IO) {
            runCatching { LutFile.load(context, baseFilm?.let { vm.effectiveLut(it, baseLut) } ?: baseLut) }.getOrNull()
        }
    }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var edited by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(base, small) {
        val b = base ?: return@LaunchedEffect; val s = small ?: return@LaunchedEffect
        original = withContext(Dispatchers.Default) { LutFile.apply(s, b) }
    }
    LaunchedEffect(base, small, edit) {
        val b = base ?: return@LaunchedEffect; val s = small ?: return@LaunchedEffect
        delay(40)   // Regler-Ziehen bündeln
        val e = edit
        edited = withContext(Dispatchers.Default) { LutFile.apply(s, LutFile.bake(b, e)) }
    }

    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val baseName = film?.fullLabel ?: "eigene LUT"

    val preview: @Composable (Modifier) -> Unit = { m ->
        Box(m.clip(RoundedCornerShape(10.dp)).background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onPress = { showOriginal = true; tryAwaitRelease(); showOriginal = false }) },
            contentAlignment = Alignment.Center) {
            val bmp = if (showOriginal) original else edited ?: original
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height)) }
                ?: Text("Referenzbild wird geladen …", color = FilmWhite.copy(alpha = 0.6f))
            Text(if (showOriginal) "Film-Original" else "Bearbeitet  ·  gedrückt halten = Original",
                color = FilmWhite, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }

    val controls: @Composable (Modifier) -> Unit = { m ->
        Column(m.verticalScroll(rememberScrollState())) {
            Section("Tonwerte")
            BipolarSlider("Belichtung", edit.exposure, true) { edit = edit.copy(exposure = it) }
            BipolarSlider("Kontrast", edit.contrast, true) { edit = edit.copy(contrast = it) }
            BipolarSlider("Schwarz", edit.blacks, true, "tiefer", "verblasst") { edit = edit.copy(blacks = it) }
            BipolarSlider("Lichter", edit.whites, true, "gedämpft", "heller") { edit = edit.copy(whites = it) }
            Section("Farbe")
            BipolarSlider("Temperatur", edit.temperature, true, "kühler", "wärmer") { edit = edit.copy(temperature = it) }
            BipolarSlider("Tönung", edit.tint, true, "grün", "magenta") { edit = edit.copy(tint = it) }
            BipolarSlider("Sättigung", edit.saturation, true) { edit = edit.copy(saturation = it) }
            Section("Kanäle (Mitteltöne)")
            BipolarSlider("Rot", edit.red, true, "cyan", "rot") { edit = edit.copy(red = it) }
            BipolarSlider("Grün", edit.green, true, "magenta", "grün") { edit = edit.copy(green = it) }
            BipolarSlider("Blau", edit.blue, true, "gelb", "blau") { edit = edit.copy(blue = it) }
            Section("Teiltonung")
            HueSlider("Schatten", edit.shadowHue) { edit = edit.copy(shadowHue = it) }
            LabeledSlider("Stärke", edit.shadowAmount, true) { edit = edit.copy(shadowAmount = it) }
            HueSlider("Lichter", edit.highlightHue) { edit = edit.copy(highlightHue = it) }
            LabeledSlider("Stärke", edit.highlightAmount, true) { edit = edit.copy(highlightAmount = it) }

            Section("Speichern")
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name der LUT") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = FilmWhite, unfocusedTextColor = FilmWhite,
                    focusedBorderColor = FilmAccent, focusedLabelColor = FilmAccent, cursorColor = FilmAccent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (custom != null) {
                    Pill("Überschreiben", selected = true) {
                        vm.saveCustomLut(custom.id, name, custom.baseFilmId, custom.baseLut, edit, catalog); onBack()
                    }
                    Pill("Als neue speichern", selected = false) {
                        vm.saveCustomLut(null, name, custom.baseFilmId, custom.baseLut, edit, catalog); onBack()
                    }
                } else {
                    Pill("Als eigene LUT speichern", selected = true) {
                        vm.saveCustomLut(null, name, film?.id, baseLut, edit, catalog); onBack()
                    }
                }
                Pill("Regler zurücksetzen", selected = false) { edit = LutEdit() }
            }
            Text("Die Original-LUT des Films bleibt unverändert. Eigene LUTs findest du in der Filmauswahl unter „Eigene LUTs“.",
                color = FilmWhite.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(16.dp))
            Spacer(Modifier.height(24.dp))
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ Zurück", selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("LUT bearbeiten", color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Basis: $baseName", color = FilmAccent, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                preview(Modifier.weight(1.1f).align(Alignment.Top))
                controls(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            preview(Modifier.fillMaxWidth())
            controls(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Farbton-Regler 0…360° mit Farbfleck. */
@Composable
private fun HueSlider(label: String, hue: Float, onChange: (Float) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FilmWhite, modifier = Modifier.width(96.dp))
        Slider(value = hue, onValueChange = onChange, valueRange = 0f..360f, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color(LutEdit.hueColor(hue)),
                activeTrackColor = FilmWhite.copy(alpha = 0.25f), inactiveTrackColor = FilmWhite.copy(alpha = 0.25f)))
        Box(Modifier.padding(start = 8.dp).size(24.dp).clip(CircleShape).background(Color(LutEdit.hueColor(hue))))
        Spacer(Modifier.width(20.dp))
    }
}
