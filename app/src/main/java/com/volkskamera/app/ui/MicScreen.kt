package com.volkskamera.app.ui

import com.volkskamera.app.t
import com.volkskamera.app.tf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.render.HumType
import com.volkskamera.app.render.MicProfile
import com.volkskamera.app.render.MicType
import com.volkskamera.app.render.info
import com.volkskamera.app.render.rangeText
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite

/**
 * Mikrofon der Zeit: Auswahl nach Jahrzehnt (1900er … 2000er) mit Icons, dazu Rauschen,
 * Rauschsperre, Verzerrung, Bandbreite und Automatik. Zum Anhören eine eigene Sprachprobe.
 */
@UnstableApi
@Composable
fun MicScreen(vm: FilmViewModel, onBack: () -> Unit) {
    val l = vm.look
    val context = LocalContext.current
    var decade by remember { mutableStateOf(l.mic?.decade ?: MicProfile.decades.first()) }
    var byType by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(l.mic?.info?.type ?: MicType.KONDENSATOR) }
    androidx.activity.compose.BackHandler { onBack() }

    // Sprachprobe durch die eingestellte Kette (wie im fertigen Film)
    val probe = vm.probe
    val pos = remember { IntArray(1) }
    val preview = remember(probe) {
        com.volkskamera.app.render.ProjectorPreview { sr, look ->
            val pr = probe ?: return@ProjectorPreview { 0f }
            val proc = com.volkskamera.app.render.FilmAudioProcessor.always(look,
                com.volkskamera.app.render.ClickSamples.load(context)).apply { preparePreview(sr) }
            return@ProjectorPreview {
                // einmal abspielen, danach Stille (die Oberfläche stoppt die Wiedergabe)
                if (pos[0] >= pr.size) 0f
                else { val x = pr[pos[0]] / 32768f; pos[0]++; proc.previewSample(x) }
            }
        }
    }
    var playing by remember(probe) { mutableStateOf(false) }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    LaunchedEffect(l) { if (playing) preview.update(l) }
    // Ende der Probe erkennen und Wiedergabe beenden
    LaunchedEffect(playing, probe) {
        val pr = probe ?: return@LaunchedEffect
        while (playing) {
            if (pos[0] >= pr.size) { kotlinx.coroutines.delay(150); preview.stop(); playing = false }
            kotlinx.coroutines.delay(100)
        }
    }
    if (playing && vm.probeRecording) { preview.stop(); playing = false }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(top = 12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(t("‹ Zurück"), selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Mikrofon"), color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(l.mic?.let { "${MicProfile.decadeLabel(it.decade)} · ${it.label}" } ?: t("Originalton des Handys"),
                    color = FilmAccent, fontSize = 12.sp)
            }
            Pill(t("Originalton"), selected = l.mic == null) { vm.editQuiet { withMic(null) } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(t("nach Jahrzehnt"), selected = !byType, small = true) { byType = false }
            Pill(t("nach Bauart"), selected = byType, small = true) { byType = true }
        }
        Spacer(Modifier.height(6.dp))
        if (byType) PillRow(MicType.entries.filter { t -> MicProfile.entries.any { it.info.type == t } }, { it.label },
            { it == type }, true) { type = it }
        else PillRow(MicProfile.decades, { MicProfile.decadeLabel(it) }, { it == decade }, true) { decade = it }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.weight(1f)) {
            LazyVerticalGrid(
                GridCells.Adaptive(128.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                val shown = if (byType) MicProfile.entries.filter { it.info.type == type }
                    else MicProfile.entries.filter { it.decade == decade }.sortedBy { it.info.type.ordinal }
                items(shown, key = { it.name }) { m ->
                    MicCard(m, m == l.mic) { vm.editQuiet { withMic(m) } }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    MicControls(vm, playing, onPlay = {
                        if (playing) preview.stop() else { pos[0] = 0; preview.start(vm.look) }
                        playing = !playing
                    })
                }
            }
        }
    }
}

@Composable
private fun MicCard(m: MicProfile, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) FilmAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(if (selected) 2.dp else 0.dp, if (selected) FilmAccent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MicIcon(m.shape, if (selected) FilmAccent else FilmWhite.copy(alpha = 0.85f), Modifier.size(52.dp),
            cut = if (selected) Color(0xFF3A3320) else Color(0xFF1A1A1A))
        Spacer(Modifier.height(6.dp))
        Text(m.label, color = FilmWhite, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, minLines = 2, maxLines = 3)
        Text("${MicProfile.decadeLabel(m.decade)} · ${m.info.type.label.substringBefore(" (")}", color = FilmAccent.copy(alpha = 0.8f),
            fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@UnstableApi
@Composable
private fun MicControls(vm: FilmViewModel, playing: Boolean, onPlay: () -> Unit) {
    val l = vm.look
    val on = l.mic != null
    Column(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
        l.mic?.let { m ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp)) {
                Text(m.label, color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(m.hint, color = FilmWhite.copy(alpha = 0.75f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                InfoLine(t("Bauart"), m.info.type.label)
                InfoLine(t("Richtcharakteristik"), m.info.pattern)
                InfoLine(t("Frequenzbereich"), tf("ca. %s", m.rangeText))
                InfoLine(t("Einsatz"), m.info.use)
                InfoLine(t("Zeit"), MicProfile.decadeLabel(m.decade))
            }
        }
        Section(t("Klang"))
        LabeledSlider(t("Rauschen"), l.micNoise, on) { v -> vm.editQuiet { copy(micNoise = v) } }
        LabeledSlider(t("Rauschsperre"), l.micGate, on) { v -> vm.editQuiet { copy(micGate = v) } }
        Text(t("Rauschen = Eigenrauschen des alten Geräts (0 = ganz still). Rauschsperre dämpft das Rauschen des Handymikrofons in leisen Stellen."),
            color = FilmWhite.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        LabeledSlider(t("Verzerrung"), l.micDrive, on) { v -> vm.editQuiet { copy(micDrive = v) } }
        BipolarSlider(t("Bandbreite"), l.micBandwidth, on, "enger", "weiter") { v -> vm.editQuiet { copy(micBandwidth = v) } }
        LabeledSlider(t("Automatik"), l.micAgc, on) { v -> vm.editQuiet { copy(micAgc = v) } }
        InterferenceSection(vm)
        Section(t("Sprachprobe"))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(t("Länge"), color = FilmWhite, modifier = Modifier.width(108.dp))
            listOf(3, 5, 8).forEach { s -> Pill("$s s", selected = vm.probeSeconds == s, small = true) { vm.chooseProbeSeconds(s) } }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (vm.probeRecording) t("● Sprich jetzt …") else tf("● Sprachprobe (%d s)", vm.probeSeconds), selected = vm.probeRecording,
                enabled = !vm.probeRecording) { vm.recordProbe(vm.probeSeconds) }
            Pill(if (playing) t("■ Stopp") else t("▶ Probe anhören"), selected = playing,
                enabled = vm.probe != null && !vm.probeRecording) { onPlay() }
        }
        Text(t("Die Probe klingt wie der fertige Film und wird einmal abgespielt. Sie wird nicht gespeichert."),
            color = FilmWhite.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(k, color = FilmWhite.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.width(120.dp))
        Text(v, color = FilmWhite.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}

/**
 * Störgeräusche der Tonleitung: Knacken (Staub, Kratzer, Wackler) und elektrisches Brummen
 * (Brummschleife, Wackelkontakt, Handy-Einstreuung, Dimmer, Summen). Mit eigener Hörprobe.
 */
@UnstableApi
@Composable
private fun InterferenceSection(vm: FilmViewModel) {
    val l = vm.look
    val preview = remember {
        com.volkskamera.app.render.ProjectorPreview { sr, look ->
            // nur Knacken und Brummen vorhören (kein Rauschen/Projektor)
            val syn = com.volkskamera.app.render.BackgroundSynth(sr, look.copy(bgNoiseOn = false))
            return@ProjectorPreview { syn.next() }
        }
    }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    LaunchedEffect(l) { if (playing) preview.update(l) }
    // Hörprobe nur so lang wie eingestellt (3/5/8 s), kein Endlos-Loop
    LaunchedEffect(playing) {
        if (playing) { kotlinx.coroutines.delay(vm.probeSeconds * 1000L); preview.stop(); playing = false }
    }
    val any = l.crackleOn || l.bgHumOn
    if (playing && !any) { preview.stop(); playing = false }

    Section(t("Knacken & Brummen"))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(t("Knacken"), selected = l.crackleOn) { vm.editQuiet { copy(crackleOn = !crackleOn) } }
        Pill(t("Brummen"), selected = l.bgHumOn) {
            vm.editQuiet { copy(bgHumOn = !bgHumOn, bgHumType = if (bgHumType.cable) bgHumType else HumType.NETZ,
                bgHumFreq = if (bgHumFreq == 60f) 60f else 50f) }
        }
        Pill(if (playing) t("■ Stopp") else t("▶ Anhören"), selected = playing, enabled = any) {
            if (playing) preview.stop() else preview.start(l)
            playing = !playing
        }
    }
    LabeledSlider(t("Stärke"), l.crackleAmount, l.crackleOn) { v -> vm.editQuiet { copy(crackleAmount = v) } }
    LabeledSlider(t("Häufigkeit"), l.crackleDensity, l.crackleOn) { v -> vm.editQuiet { copy(crackleDensity = v) } }
    Spacer(Modifier.height(8.dp))
    PillRow(HumType.entries.filter { it.cable }, { it.label }, { l.bgHumOn && it == l.bgHumType }, l.bgHumOn) { t ->
        vm.editQuiet { copy(bgHumType = t) }
    }
    if (l.bgHumOn) Text(l.bgHumType.hint, color = FilmWhite.copy(alpha = 0.55f), fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(t("Stromnetz"), color = FilmWhite.copy(alpha = if (l.bgHumOn) 1f else 0.4f), modifier = Modifier.width(108.dp))
        Pill(t("50 Hz (Europa)"), selected = l.bgHumFreq != 60f, small = true, enabled = l.bgHumOn) { vm.editQuiet { copy(bgHumFreq = 50f) } }
        Pill(t("60 Hz (USA)"), selected = l.bgHumFreq == 60f, small = true, enabled = l.bgHumOn) { vm.editQuiet { copy(bgHumFreq = 60f) } }
    }
    LabeledSlider(t("Pegel"), l.bgHumLevel, l.bgHumOn) { v -> vm.editQuiet { copy(bgHumLevel = v) } }
}
