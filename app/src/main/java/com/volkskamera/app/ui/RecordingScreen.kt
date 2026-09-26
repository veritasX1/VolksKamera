package com.volkskamera.app.ui

import androidx.compose.foundation.horizontalScroll
import com.volkskamera.app.t
import com.volkskamera.app.tf
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.camera.Calibration
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite

/** Aufnahme-Einstellungen: Auflösung, Frontkamera spiegeln, Sensor-Kalibrierung mit Anleitung. */
@UnstableApi
@Composable
fun RecordingScreen(vm: FilmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    androidx.activity.compose.BackHandler { onBack() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var calExists by remember { mutableStateOf(Calibration.exists(context)) }
    var calActive by remember { mutableStateOf(Calibration.isActive(context)) }
    val hasRaw = remember { Calibration.rawCameraId(context) != null }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(t("‹ Zurück"), selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t("Aufnahme"), color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Heading(t("Auflösung"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(480, 576, 720, 1080).forEach { r -> Pill("${r}p", selected = vm.recordResolution == r) { vm.chooseResolution(r) } }
        }
        Hint(t("Auflösung des fertigen Films (kurze Seite). 480p und 576p entsprechen alter Video- und Fernsehnorm, 1080p ist volle HD-Auflösung."))

        Heading(t("Analog"))
        LabeledSlider(t("Stärke"), vm.analog, enabled = true) { vm.chooseAnalog(it) }
        Text(t("Verstärkung"), color = FilmWhite.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1f, 2f, 5f, 10f, 20f, 50f, 100f).forEach { f ->
                Pill("${f.toInt()}×", selected = vm.analogFactor == f) { vm.chooseAnalogFactor(f) }
            }
        }
        Hint(t("Nimmt dem Handybild das digital Knackige: Die Nachschärfung der Kamera wird abgeschaltet, die Zeichnung wird weicher, helle Stellen bekommen einen sanften Schimmer und einen Lichthof. Die Verstärkung vervielfacht die Stärke – für sehr weiche, verträumte Bilder. Gilt für jeden Film. Die Weichheit siehst du schon im Sucher, Schimmer und Lichthof erst im fertigen Film."))

        Heading(t("Moiré"))
        LabeledSlider(t("Filter"), vm.moire, enabled = true) { vm.chooseMoire(it) }
        Hint(t("Unterdrückt Moiré – flimmernde Muster bei feinen Linien und Rastern wie Stoffen, Gittern oder Bildschirmen. Ein optischer Tiefpass glättet nur die feinsten Muster, wie der Anti-Moiré-Filter echter Kameras. Bei Bildraten bis 30 fps liest die App den Sensor außerdem vollständig aus statt im schnellen 60-fps-Modus."))

        Heading(t("Projektor und Objektiv"))
        LabeledSlider(t("Wackeln"), vm.weave, enabled = true) { vm.chooseWeave(it) }
        LabeledSlider(t("Flackern"), vm.flicker, enabled = true) { vm.chooseFlicker(it) }
        LabeledSlider(t("Vignette"), vm.vignette, enabled = true) { vm.chooseVignette(it) }
        Hint(t("Wackeln: der Film läuft nicht ganz ruhig durch die Kamera. Flackern: die Helligkeit schwankt von Bild zu Bild. Vignette: das Objektiv dunkelt die Ränder ab."))

        Heading(t("Frontkamera"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(t("Gespiegelt (wie ein Spiegel)"), selected = vm.mirrorFront) { vm.chooseMirrorFront(true) }
            Pill(t("Seitenrichtig"), selected = !vm.mirrorFront) { vm.chooseMirrorFront(false) }
        }
        Hint(t("Umschalten geht auch direkt in der Kamera mit der ⇋-Taste, wenn die Frontkamera aktiv ist."))

        Heading(t("Sensor kalibrieren"))
        Text(
            t("Jedes Handy bearbeitet sein Kamerabild anders – mit mehr Sättigung und mehr Kontrast, als der Sensor wirklich sieht. Die Filme der Volkskamera sind für ein neutrales Bild berechnet. Mit der Kalibrierung misst die App einmalig, wie dein Handy das Bild verändert, und gleicht es vor jedem Film aus."),
            color = FilmWhite.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(10.dp))
        Text(t("So geht's"), color = FilmAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            t("1.  Lege Gegenstände mit vielen kräftigen Farben zusammen – zum Beispiel Buntstifte oder Filzstifte – dazu etwas Weißes und etwas Dunkles.\n2.  Sorge für gleichmäßiges Licht: Tageslicht vom Fenster ist ideal, kein direktes Gegenlicht, keine harten Schatten.\n3.  Halte das Handy ruhig, so dass die Farben den größten Teil des Bildes füllen.\n4.  Tippe auf „Jetzt kalibrieren“. Die App nimmt ein Rohbild und ein normales Bild zugleich auf und vergleicht sie."),
            color = FilmWhite.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Example("kalibrierung/handy.webp", t("So bearbeitet das Handy"), Modifier.weight(1f))
            Example("kalibrierung/neutral.webp", t("So sieht der Sensor neutral"), Modifier.weight(1f))
        }
        Hint(t("Beispiel einer guten Kalibrier-Szene: viele Farben, eine helle und eine dunkle Fläche, gleichmäßiges Licht. Links das Bild des Handys, rechts dieselbe Aufnahme neutral entwickelt – die Kalibrierung gleicht genau diesen Unterschied aus."))
        Spacer(Modifier.height(12.dp))
        if (!hasRaw) {
            Text(t("Dieses Handy liefert keine Sensor-Rohdaten (RAW) – eine Kalibrierung ist hier leider nicht möglich."),
                color = FilmAccent, fontSize = 13.sp)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (busy) t("Kalibriere …") else t("Jetzt kalibrieren"), selected = true, enabled = !busy) {
                busy = true; message = null
                Calibration.run(context) { err ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        busy = false
                        message = err ?: t("Kalibrierung gespeichert – alle Filme sind jetzt auf dein Handy abgestimmt.")
                        calExists = Calibration.exists(context); calActive = Calibration.isActive(context)
                        if (err == null) vm.calibrationChanged()
                    }
                }
            }
            if (busy) CircularProgressIndicator(color = FilmAccent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            if (calExists && !busy) {
                Pill(if (calActive) t("Kalibrierung an") else t("Kalibrierung aus"), selected = calActive) {
                    Calibration.setActive(context, !calActive); calActive = !calActive; vm.calibrationChanged()
                }
                Pill(t("Löschen"), selected = false) {
                    Calibration.delete(context); calExists = false; calActive = false; vm.calibrationChanged()
                }
            }
        }
        message?.let { Text(it, color = FilmAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Hint(t("Während der Kalibrierung ist die Kamera der App kurz belegt. Die Messung dauert etwa drei Sekunden."))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun Heading(t: String) {
    Spacer(Modifier.height(20.dp))
    Text(t, color = FilmAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable private fun Hint(t: String) {
    Text(t, color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable private fun Example(asset: String, caption: String, modifier: Modifier) {
    val context = LocalContext.current
    val bmp = remember(asset) { runCatching { context.assets.open(asset).use { BitmapFactory.decodeStream(it) } }.getOrNull() }
    Column(modifier) {
        bmp?.let {
            Image(it.asImageBitmap(), caption, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp)))
        }
        Text(caption, color = FilmWhite.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
