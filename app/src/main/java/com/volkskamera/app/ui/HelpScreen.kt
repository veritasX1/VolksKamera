package com.volkskamera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite

private class HelpTopic(val title: String, val text: String)

private val TOPICS = listOf(
    HelpTopic("Die Idee",
        "Die Volkskamera filmt wie eine analoge Filmkamera. Du wählst ein echtes Filmmaterial – vom Kinofilm der 1890er " +
        "bis zum heutigen Diafilm – und die App gibt dem Bild dessen Farben, Kontrast und Korn. Wie bei einer echten Kamera " +
        "hat jeder Film eine feste Empfindlichkeit; die Helligkeit regelst du über die Belichtungszeit."),
    HelpTopic("Filmen",
        "Der große Knopf startet und beendet die Aufnahme, während der Aufnahme erscheint daneben die Pause-Taste. " +
        "Tippe in den Sucher, um dort scharfzustellen. Nach dem Filmen wird der Film entwickelt (Fortschritt im " +
        "Vorschaubild unten rechts) und landet in der Galerie im Ordner „Movies/VolksKamera“."),
    HelpTopic("Die Zählwerke",
        "BILD/S: Bildrate des fertigen Films (z. B. 18 für Stummfilm-Ruckeln, 24 für Kino).\n" +
        "FORMAT: Seitenverhältnis 4:3 oder 16:9.\n" +
        "OBJEKTIV: wechselt zwischen den Kameras des Handys (Weitwinkel, Normal, Tele).\n" +
        "ZEIT: Belichtungszeit. „Auto“ belichtet selbst; kürzere Zeiten machen das Bild dunkler und schärfer bei Bewegung.\n" +
        "ISO / ASA: zeigt die feste Empfindlichkeit des gewählten Films an.\n" +
        "Tippe auf ein Zählwerk, um weiterzuschalten."),
    HelpTopic("Einen Film auswählen",
        "Zahnrad → Hersteller → Breite (8, 16 oder 35 mm) → Film. Ein Tipp auf einen Film zeigt seinen Steckbrief: " +
        "Beispielbild, Charakteristik und Beschreibung. Mit dem ◐-Knopf neben dem Beispielbild vergleichst du mit dem Original. " +
        "Übernommen wird der Film erst mit „Verwenden“."),
    HelpTopic("Beispielbild",
        "Oben in der Filmauswahl wählst du, woran die Filme gezeigt werden: Terrasse, Fröruper Berge oder ein eigenes Foto " +
        "aus deiner Galerie – am besten eines mit Himmel, Grün, Rot und Hauttönen."),
    HelpTopic("Farbfilter (Schwarzweiß)",
        "Bei S/W-Filmen kannst du wie früher einen Farbfilter vors Objektiv setzen. Er hellt seine eigene Farbe auf und " +
        "dunkelt die Gegenfarbe ab: Gelb für natürliche Wolken, Orange und Rot für dramatischen Himmel, Grün für helles Laub. " +
        "Zu jedem Filter stehen die Kodak-Wratten-Nummer, die Bezeichnung bei B+W und Hoya sowie der Einsatzzweck. " +
        "Der Filter gilt für alle S/W-Filme, bis du ihn wieder abnimmst."),
    HelpTopic("Filme bearbeiten und eigene LUTs",
        "„Bearbeiten“ öffnet den LUT-Editor: Belichtung, Kontrast, Schwarz- und Weißpunkt, Farbtemperatur, Sättigung, " +
        "Farbkanäle und Teiltonung. Gedrückt halten zeigt das Film-Original. Deine Fassung speicherst du als eigene LUT – " +
        "die Original-LUT des Films bleibt immer erhalten. Eigene LUTs findest du in der Filmauswahl unter „★ Eigene LUTs“."),
    HelpTopic("Mikrofon",
        "Unter „Mikrofon“ wählst du die Aufnahmekette einer Epoche – vom Edison-Phonographen über Kohlemikrofon, " +
        "Bändchen und Röhrenmikrofon bis zum Camcorder –, sortiert nach Jahrzehnt oder Bauart, jeweils mit Steckbrief. " +
        "Regler: Rauschen (0 = still), Rauschsperre gegen das Rauschen des Handymikrofons, Verzerrung, Bandbreite, Automatik. " +
        "„Originalton“ lässt den Ton unverändert."),
    HelpTopic("Knacken & Brummen",
        "Knacken wie von einer alten Filmkopie. Brummen wie bei einem Kabelproblem: Brummschleife, Wackelkontakt, " +
        "Handy-Einstreuung (das „dit-dit-dit“), Dimmer oder Summen – mit 50 Hz (Europa) oder 60 Hz (USA)."),
    HelpTopic("Hörprobe",
        "Nimm eine kurze Sprachprobe auf (3, 5 oder 8 Sekunden) und höre sie so, wie sie im fertigen Film klingen wird. " +
        "Die Probe wird einmal abgespielt und nicht gespeichert."),
    HelpTopic("Gehäuse",
        "Unter „Gehäuse“ gestaltest du deine Kamera: Material (Leder, Stoff, Holz, Metall, Stein oder ein eigenes Foto), " +
        "Metall der Beschläge, Auslöser und den Schriftzug – Text, Schriftart und Metall frei wählbar."),
    HelpTopic("Licht",
        "Die Materialien werden mit Licht gerechnet: Relief, Glanz und Spiegelung. Mit „Licht folgt der Bewegung“ steht die " +
        "Lampe fest im Raum – neigst oder schwenkst du das Handy, wandern Glanzlichter und Schatten wie bei einer echten Kamera. " +
        "Stärke und Lichtfarbe sind einstellbar. Mit einem Frontkamera-Foto spiegelt sich dein Raum im Gehäuse. " +
        "Wen es stört: einfach ausschalten, dann kommt das Licht fest von oben links."),
    HelpTopic("App-Symbol",
        "Unter „Gehäuse“ → „App-Symbol“ wählst du eines von vier Art-déco-Symbolen. Der Startbildschirm übernimmt es nach " +
        "einigen Sekunden; bei manchen Startbildschirmen muss die App danach neu abgelegt werden."),
    HelpTopic("Filme ansehen",
        "Das Vorschaubild unten rechts öffnet den zuletzt entwickelten Film. Von dort gelangst du zur Übersicht aller Filme " +
        "und zum Editor, in dem du mehrere Filme mit Blenden, Countdown oder Filmriss aneinanderreihst."),
)

/** Hilfe: alle Bereiche der App, aufklappbar. Erreichbar über das „?“ in den Einstellungen. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    var open by remember { mutableStateOf<String?>(TOPICS.first().title) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ Zurück", selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text("Hilfe", color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(TOPICS, key = { it.title }) { t ->
                val expanded = open == t.title
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (expanded) FilmAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                        .clickable { open = if (expanded) null else t.title }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, color = if (expanded) FilmAccent else FilmWhite, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (expanded) "–" else "+", color = FilmWhite.copy(alpha = 0.6f), fontSize = 18.sp)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        Text(t.text, color = FilmWhite.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
