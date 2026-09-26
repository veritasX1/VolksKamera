package com.volkskamera.app.ui

import com.volkskamera.app.t
import com.volkskamera.app.tf
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

private fun topics() = listOf(
    HelpTopic(t("Die Idee"),
        t("Die Volkskamera filmt wie eine analoge Filmkamera. Du wählst ein echtes Filmmaterial – vom Kinofilm der 1890er bis zum heutigen Diafilm – und die App gibt dem Bild dessen Farben, Kontrast und Korn. Wie bei einer echten Kamera hat jeder Film eine feste Empfindlichkeit; die Helligkeit regelst du über die Belichtungszeit.")),
    HelpTopic(t("Filmen"),
        t("Der große Knopf startet und beendet die Aufnahme, während der Aufnahme erscheint daneben die Pause-Taste. Tippe in den Sucher, um dort scharfzustellen. Nach dem Filmen wird der Film entwickelt (Fortschritt im Vorschaubild unten rechts) und landet in der Galerie im Ordner „Movies/VolksKamera“.")),
    HelpTopic(t("Die Zählwerke"),
        t("BILD/S: Bildrate des fertigen Films (z. B. 18 für Stummfilm-Ruckeln, 24 für Kino).\nFORMAT: Seitenverhältnis 4:3 oder 16:9.\nOBJEKTIV: wechselt zwischen den Kameras des Handys (Weitwinkel, Normal, Tele).\nZEIT: Belichtungszeit von 1/5 bis 1/5000 Sekunde. Kürzere Zeiten machen das Bild dunkler und Bewegungen schärfer. Es gibt bewusst keine Automatik: Die Helligkeit folgt allein Zeit und Film – auch beim Scharfstellen und beim Objektivwechsel.\nISO / ASA: zeigt die feste Empfindlichkeit des gewählten Films an. Die App belichtet den Sensor so, wie der echte Film bei dieser Zeit belichtet worden wäre – ein ISO-8-Film braucht also viel Licht oder lange Zeiten.\nTippe auf ein Zählwerk, um weiterzuschalten.")),
    HelpTopic(t("Aufnahme & Kalibrierung"),
        t("Unter „Aufnahme“ wählst du die Auflösung des fertigen Films (480p, 576p, 720p, 1080p) und ob die Frontkamera gespiegelt aufnimmt (auch direkt in der Kamera mit der ⇋-Taste). Mit „Sensor kalibrieren“ misst die App einmalig, wie dein Handy sein Kamerabild bearbeitet, und gleicht das vor jedem Film aus – die Anleitung dort zeigt, wie eine gute Kalibrier-Szene aussieht.")),
    HelpTopic(t("Kombinationen"),
        t("Unter „★ Kombinationen“ speicherst du Film, Farbfilter und Ton (Mikrofon, Rauschen, Knacken, Brummen) unter einem Namen. Mit „Teilen“ schickst du sie als kurzen Text an andere; wer ihn mit der Volkskamera teilt oder einfügt, kann sie übernehmen.")),
    HelpTopic(t("Einen Film auswählen"),
        t("Zahnrad → Hersteller → Breite (8, 16 oder 35 mm) → Film. Ein Tipp auf einen Film zeigt seinen Steckbrief: Beispielbild, Charakteristik und Beschreibung. Mit dem ◐-Knopf neben dem Beispielbild vergleichst du mit dem Original. Übernommen wird der Film erst mit „Verwenden“.")),
    HelpTopic(t("Beispielbild"),
        t("Oben in der Filmauswahl wählst du, woran die Filme gezeigt werden: Terrasse, Fröruper Berge oder ein eigenes Foto aus deiner Galerie – am besten eines mit Himmel, Grün, Rot und Hauttönen.")),
    HelpTopic(t("Farbfilter (Schwarzweiß)"),
        t("Bei S/W-Filmen kannst du wie früher einen Farbfilter vors Objektiv setzen. Er hellt seine eigene Farbe auf und dunkelt die Gegenfarbe ab: Gelb für natürliche Wolken, Orange und Rot für dramatischen Himmel, Grün für helles Laub. Zu jedem Filter stehen die Kodak-Wratten-Nummer, die Bezeichnung bei B+W und Hoya sowie der Einsatzzweck. Der Filter gilt für alle S/W-Filme, bis du ihn wieder abnimmst.")),
    HelpTopic(t("Filme bearbeiten und eigene LUTs"),
        t("„Bearbeiten“ öffnet den LUT-Editor: Belichtung, Kontrast, Schwarz- und Weißpunkt, Farbtemperatur, Sättigung, Farbkanäle und Teiltonung. Gedrückt halten zeigt das Film-Original. Deine Fassung speicherst du als eigene LUT – die Original-LUT des Films bleibt immer erhalten. Eigene LUTs findest du in der Filmauswahl unter „★ Eigene LUTs“.")),
    HelpTopic(t("Mikrofon"),
        t("Unter „Mikrofon“ wählst du die Aufnahmekette einer Epoche – vom Edison-Phonographen über Kohlemikrofon, Bändchen und Röhrenmikrofon bis zum Camcorder –, sortiert nach Jahrzehnt oder Bauart, jeweils mit Steckbrief. Regler: Rauschen (0 = still), Rauschsperre gegen das Rauschen des Handymikrofons, Verzerrung, Bandbreite, Automatik. „Originalton“ lässt den Ton unverändert.")),
    HelpTopic(t("Knacken & Brummen"),
        t("Knacken wie von einer alten Filmkopie. Brummen wie bei einem Kabelproblem: Brummschleife, Wackelkontakt, Handy-Einstreuung (das „dit-dit-dit“), Dimmer oder Summen – mit 50 Hz (Europa) oder 60 Hz (USA).")),
    HelpTopic(t("Hörprobe"),
        t("Nimm eine kurze Sprachprobe auf (3, 5 oder 8 Sekunden) und höre sie so, wie sie im fertigen Film klingen wird. Die Probe wird einmal abgespielt und nicht gespeichert.")),
    HelpTopic(t("Gehäuse"),
        t("Unter „Gehäuse“ gestaltest du deine Kamera: Material (Leder, Stoff, Holz, Metall, Stein oder ein eigenes Foto), Metall der Beschläge, Auslöser und den Schriftzug – Text, Schriftart und Metall frei wählbar.")),
    HelpTopic(t("Licht"),
        t("Die Materialien werden mit Licht gerechnet: Relief, Glanz und Spiegelung. Mit „Licht folgt der Bewegung“ steht die Lampe fest im Raum – neigst oder schwenkst du das Handy, wandern Glanzlichter und Schatten wie bei einer echten Kamera. Stärke und Lichtfarbe sind einstellbar. Mit einem Frontkamera-Foto spiegelt sich dein Raum im Gehäuse. Wen es stört: einfach ausschalten, dann kommt das Licht fest von oben links.")),
    HelpTopic(t("App-Symbol"),
        t("Unter „Gehäuse“ → „App-Symbol“ wählst du eines von vier Art-déco-Symbolen. Der Startbildschirm übernimmt es nach einigen Sekunden; bei manchen Startbildschirmen muss die App danach neu abgelegt werden.")),
    HelpTopic(t("Filme ansehen"),
        t("Das Vorschaubild unten rechts öffnet den zuletzt entwickelten Film. Von dort gelangst du zur Übersicht aller Filme und zum Editor, in dem du mehrere Filme mit Blenden, Countdown oder Filmriss aneinanderreihst.")),
)

/** Hilfe: alle Bereiche der App, aufklappbar. Erreichbar über das „?“ in den Einstellungen. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    var open by remember { mutableStateOf<String?>(topics().first().title) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(t("‹ Zurück"), selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t("Hilfe"), color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(topics(), key = { it.title }) { t ->
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
            item(key = "_fuss") {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Volkskamera " + com.volkskamera.app.BuildConfig.VERSION_NAME, color = FilmAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(tf("© seit %d Olaf Winkler", 2026), color = FilmWhite.copy(alpha = 0.75f), fontSize = 13.sp)
                    Text(t("Entwickelt in Schleswig-Holstein"), color = FilmWhite.copy(alpha = 0.75f), fontSize = 13.sp)
                    Text("volkskamera.goip.de", color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}
