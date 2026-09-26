package com.volkskamera.app.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.data.SequenceAsset
import com.volkskamera.app.render.FilmLook
import com.volkskamera.app.render.FrameMode
import com.volkskamera.app.render.Stage
import com.volkskamera.app.render.Presets
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmRed
import com.volkskamera.app.ui.theme.FilmSurface
import com.volkskamera.app.ui.theme.FilmWhite

/**
 * Film-Look einstellen. Die Vorschau bleibt fixiert (Hochformat oben, Querformat links),
 * nur die Einstellungen scrollen – so sieht man jede Änderung sofort am Ausgangsmaterial.
 */
@UnstableApi
@Composable
fun LookEditorScreen(vm: FilmViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ Kamera", selected = false, onClick = onBack)
            Text("Film-Look", color = FilmAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(start = 16.dp))
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                PreviewPane(vm, Modifier.weight(0.55f).fillMaxHeight().padding(start = 12.dp, bottom = 12.dp))
                Settings(vm, Modifier.weight(0.45f).fillMaxHeight())
            }
        } else {
            PreviewPane(vm, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Settings(vm, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@UnstableApi
@Composable
private fun PreviewPane(vm: FilmViewModel, modifier: Modifier) {
    val still = vm.still
    val l = vm.look
    val aspect = when {
        still == null -> 16f / 9f
        l.aspect == null -> still.width / still.height.toFloat()
        still.height > still.width -> 1f / l.aspect
        else -> l.aspect
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(12.dp)).background(FilmSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (still != null) LookPreview(still, l, Modifier.fillMaxSize())
        }
    }
}

@UnstableApi
@Composable
private fun Settings(vm: FilmViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickSource(uri)
    }
    val c = vm.catalog ?: return
    val l = vm.look
    val on = vm.render !is RenderState.Running

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section("Preset")
        PillRow(Presets.names + vm.userPresets.map { it.name }, { it }, { it == vm.preset }, on) { vm.applyPreset(it) }
        PresetTools(vm, on)

        Section("Format")
        PillRow(listOf(4f / 3f, 16f / 9f, null), { a -> when (a) { null -> "Original"; 4f / 3f -> "4:3"; else -> "16:9" } },
            { it == l.aspect }, on) { a -> vm.edit { copy(aspect = a) } }

        Section("Bildrate des Films")
        PillRow(FPS_CHOICES + listOf(null), { f -> f?.let { "${it.toInt()} fps" } ?: "Original" },
            { it == l.targetFps }, on) { f -> vm.edit { copy(targetFps = f) } }

        // Jede Stufe: Schalter neben der Überschrift; aus = Einstellung bleibt erhalten
        StageHeader("Bild", Stage.BILD, vm, on)
        val bildOn = on && l.isOn(Stage.BILD)
        BipolarSlider("Helligkeit", l.brightness, bildOn) { v -> vm.edit { copy(brightness = v) } }
        BipolarSlider("Kontrast", l.contrast, bildOn) { v -> vm.edit { copy(contrast = v) } }
        BipolarSlider("Schärfe", l.sharpness, bildOn, "unscharf", "scharf") { v -> vm.edit { copy(sharpness = v) } }
        BipolarSlider("Farbtemp.", l.temperature, bildOn, "kühler", "wärmer") { v -> vm.edit { copy(temperature = v) } }
        BipolarSlider("Schatten", l.shadows, bildOn, "tiefer", "heller") { v -> vm.edit { copy(shadows = v) } }
        BipolarSlider("Lichter", l.highlights, bildOn, "gedämpft", "heller") { v -> vm.edit { copy(highlights = v) } }
        BipolarSlider("Sättigung", l.saturation, bildOn, "blasser", "kräftiger") { v -> vm.edit { copy(saturation = v) } }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Pill("Zurücksetzen", selected = false, small = true, enabled = bildOn && l.hasAdjustments) { vm.edit { resetAdjustments() } }
        }
        StageSlider("Vignette", Stage.VIGNETTE, l.vignette, vm, on) { v -> copy(vignette = v) }

        StageHeader("Farblook", Stage.LUT, vm, on)
        val lutOn = l.isOn(Stage.LUT)
        PillRow(c.lutPacks, { it }, { it == vm.pack }, on) { vm.choosePack(it) }
        Spacer(Modifier.height(6.dp))
        val inPack = vm.pack?.let { c.lutsIn(it) }.orEmpty()
        PillRow(inPack, { it.name }, { lutOn && it.file == l.lutFile }, on) { lut ->
            vm.edit { copy(lutFile = lut.file, off = off - Stage.LUT) }
        }
        LabeledSlider("Stärke", l.lutMix, on && lutOn && l.lutFile != null) { v -> vm.edit { copy(lutMix = v) } }

        AssetSection("Korn", Stage.KORN, c.grains, l.grain, l.grainAmount, vm, on,
            { g -> copy(grain = g) }, { v -> copy(grainAmount = v) })
        AssetSection("Staub & Textur", Stage.STAUB, c.dirt.filterNot { it.isScratch }, l.dust, l.dustAmount, vm, on,
            { g -> copy(dust = g) }, { v -> copy(dustAmount = v) })
        AssetSection("Kratzer", Stage.KRATZER, c.dirt.filter { it.isScratch }, l.scratch, l.scratchAmount, vm, on,
            { g -> copy(scratch = g) }, { v -> copy(scratchAmount = v) })
        AssetSection("Light Leaks", Stage.LEAK, c.leaksSorted, l.leak, l.leakAmount, vm, on,
            { g -> copy(leak = g) }, { v -> copy(leakAmount = v) })
        LabeledSlider("Häufigkeit", l.leakFrequency, on && l.isOn(Stage.LEAK) && l.leak != null) { v -> vm.edit { copy(leakFrequency = v) } }

        StageHeader("Rahmen", Stage.RAHMEN, vm, on) { if (frame == null) copy(frame = c.gatedFrames.firstOrNull()) else this }
        val frOn = l.isOn(Stage.RAHMEN) && l.frame != null
        PillRow(c.gatedFrames, { pretty(it.name) }, { l.isOn(Stage.RAHMEN) && it.id == l.frame?.id }, on) { f ->
            vm.edit { copy(frame = f, frameBlack = f.frameBlack, off = off - Stage.RAHMEN) }
        }
        Spacer(Modifier.height(6.dp))
        PillRow(listOf(FrameMode.SCAN, FrameMode.PROJEKTION),
            { if (it == FrameMode.SCAN) "Filmscan (Perforation sichtbar)" else "Projektion (außen schwarz)" },
            { it == l.frameMode }, on && frOn) { m -> vm.edit { copy(frameMode = m) } }
        LabeledSlider("Farbstich", l.frameTint, on && frOn) { v -> vm.edit { copy(frameTint = v) } }
        LabeledSlider("Rand dunkler", l.frameBlack / 0.9f, on && frOn) { v -> vm.edit { copy(frameBlack = v * 0.9f) } }

        Section("Film")
        StageSlider("Halation", Stage.HALATION, l.halation, vm, on) { v -> copy(halation = v) }
        StageSlider("Weich", Stage.WEICH, l.soften, vm, on) { v -> copy(soften = v) }
        StageSlider("Flackern", Stage.FLACKERN, l.flicker, vm, on) { v -> copy(flicker = v) }
        StageSlider("Wackeln", Stage.WACKELN, l.weave, vm, on) { v -> copy(weave = v) }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val byLut = vm.lutInfo?.mono == true && l.lutFile != null && lutOn
            Pill(if (byLut) "Schwarzweiß (durch LUT)" else "Schwarzweiß", selected = vm.isMonochrome, enabled = on && !byLut) {
                vm.edit { copy(mono = !mono) }
            }
            Pill("Look im Sucher andeuten", selected = l.finderLook, enabled = on) { vm.edit { copy(finderLook = !finderLook) } }
        }

        Section("Anfang & Ende")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Pill("Countdown am Anfang (5 … 1)", selected = l.countdown, enabled = on) { vm.edit { copy(countdown = !countdown) } }
        }
        Text("Filmriss am Ende", color = FilmWhite.copy(alpha = 0.8f), modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        PillRow(listOf<SequenceAsset?>(null) + c.burns, { it?.let { b -> "Filmriss " + b.name.filter(Char::isDigit).trimStart('0') } ?: "Aus" },
            { it?.id == l.burnEnd?.id }, on) { b -> vm.edit { copy(burnEnd = b) } }

        Section("Ton")
        PillRow(listOf(com.volkskamera.app.render.AudioMode.STUMM, com.volkskamera.app.render.AudioMode.ORIGINAL,
            com.volkskamera.app.render.AudioMode.ALT),
            { when (it) { com.volkskamera.app.render.AudioMode.STUMM -> "Stumm"; com.volkskamera.app.render.AudioMode.ORIGINAL -> "Original"; else -> "Alte Aufnahme" } },
            { it == l.audioMode }, on) { m -> vm.edit { copy(audioMode = m) } }
        LabeledSlider("Alterung", l.aging, on && l.audioMode == com.volkskamera.app.render.AudioMode.ALT) { v -> vm.edit { copy(aging = v) } }
        CrackleSection(vm, on)
        MicSection(vm, on)
        ProjectorSection(vm, on)
        BackgroundSection(vm, on)
        FxAmbientSection(vm, on)

        Section("Vorhandenes Video entwickeln")
        Column(Modifier.padding(horizontal = 16.dp)) {
            when (val r = vm.render) {
                is RenderState.Running -> {
                    Text("Entwickle … ${r.percent} %", color = FilmWhite)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { r.percent / 100f }, modifier = Modifier.fillMaxWidth(),
                        color = FilmAccent, trackColor = FilmSurface)
                    Spacer(Modifier.height(10.dp))
                    Pill("Abbrechen", selected = false) { vm.cancelRender() }
                }
                else -> {
                    if (r is RenderState.Done) {
                        Text("Gespeichert in Filme/VolksKamera", color = FilmWhite)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (r is RenderState.Failed) {
                        Text("Fehler: ${r.message}", color = FilmRed)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Video aus Galerie wählen", selected = false) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                        if (vm.source != null) Pill("Entwickeln", selected = true) { vm.startRender() }
                        if (r is RenderState.Done) Pill("Ansehen", selected = false) {
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(r.uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    }
                }
            }
        }
    }
}

/** Die Film-Bildraten aus der Skizze. */
val FPS_CHOICES = listOf(18f, 24f, 25f, 30f, 48f, 50f, 60f)

/** Überschrift einer Stufe mit An/Aus-Schalter rechts daneben. */
@UnstableApi
@Composable
private fun StageHeader(
    title: String,
    stage: Stage,
    vm: FilmViewModel,
    enabled: Boolean,
    onEnable: FilmLook.() -> FilmLook = { this },
) {
    val isOn = vm.look.isOn(stage)
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Section(title) }
        Pill(if (isOn) "An" else "Aus", selected = isOn, small = true, enabled = enabled, modifier = Modifier.padding(top = 12.dp)) {
            vm.edit { val t = toggle(stage); if (t.isOn(stage)) t.onEnable() else t }
        }
    }
}

/** Regler mit eigenem An/Aus-Schalter (Halation, Weich, Flackern, Wackeln). */
@UnstableApi
@Composable
private fun StageSlider(label: String, stage: Stage, value: Float, vm: FilmViewModel, enabled: Boolean, set: FilmLook.(Float) -> FilmLook) {
    val isOn = vm.look.isOn(stage)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(if (isOn) "An" else "Aus", selected = isOn, small = true, enabled = enabled) { vm.edit { toggle(stage) } }
        Box(Modifier.weight(1f)) {
            LabeledSlider(label, value, enabled && isOn) { v -> vm.edit { set(v) } }
        }
    }
}

@UnstableApi
@Composable
private fun AssetSection(
    title: String,
    stage: Stage,
    items: List<SequenceAsset>,
    selected: SequenceAsset?,
    amount: Float,
    vm: FilmViewModel,
    enabled: Boolean,
    setAsset: FilmLook.(SequenceAsset) -> FilmLook,
    setAmount: FilmLook.(Float) -> FilmLook,
) {
    // Einschalten ohne gewähltes Asset: das erste nehmen
    StageHeader(title, stage, vm, enabled) { if (selected == null && items.isNotEmpty()) setAsset(items.first()) else this }
    val isOn = vm.look.isOn(stage)
    PillRow(items, { pretty(it.name) }, { isOn && it.id == selected?.id }, enabled) { a ->
        vm.edit { setAsset(a).copy(off = off - stage) }
    }
    LabeledSlider("Stärke", amount, enabled && isOn && selected != null) { v -> vm.edit { setAmount(v) } }
}

/** Eigene Presets: speichern (mit Name), löschen, als .txt exportieren/importieren. */
@UnstableApi
@Composable
private fun PresetTools(vm: FilmViewModel, enabled: Boolean) {
    val context = LocalContext.current
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportText().second.toByteArray()) }
            }.isSuccess
            message = if (ok) "Exportiert." else "Export fehlgeschlagen."
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            message = if (text != null && vm.importText(text)) "Importiert als „${vm.preset}“." else "Das ist keine VolksKamera-Preset-Datei."
        }
    }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Namensfeld sofort mit Tastatur öffnen, nicht erst nach einem weiteren Tipp
    LaunchedEffect(naming) { if (naming) { kotlinx.coroutines.delay(100); focus.requestFocus() } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (naming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text("Name des Presets") },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        if (name.isNotBlank()) { vm.saveUserPreset(name); naming = false; message = "Gespeichert als „${vm.preset}“." }
                    }),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FilmAccent, focusedLabelColor = FilmAccent, cursorColor = FilmAccent),
                )
                Spacer(Modifier.width(8.dp))
                Pill("Speichern", selected = true, enabled = name.isNotBlank()) {
                    vm.saveUserPreset(name); naming = false; message = "Gespeichert als „${vm.preset}“."
                }
                Spacer(Modifier.width(6.dp))
                Pill("✕", selected = false, small = true) { naming = false }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Als Preset speichern", selected = false, small = true, enabled = enabled) {
                    name = if (vm.isUserPreset) vm.preset.orEmpty() else ""; naming = true; message = null
                }
                Pill("Exportieren", selected = false, small = true, enabled = enabled) {
                    export.launch("VolksKamera-" + vm.exportText().first.replace(Regex("[^A-Za-z0-9äöüÄÖÜß _-]"), "") + ".txt")
                }
                Pill("Importieren", selected = false, small = true, enabled = enabled) { import.launch(arrayOf("text/plain", "text/*")) }
                Pill("Teilen", selected = false, small = true, enabled = enabled) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "VolksKamera-Look: ${vm.exportText().first}")
                        putExtra(Intent.EXTRA_TEXT, vm.exportText().second)
                    }
                    context.startActivity(Intent.createChooser(send, "Look teilen"))
                }
                if (vm.selectedFilm != null) Pill("Zurücksetzen", selected = false, small = true, enabled = enabled) {
                    vm.resetSelectedFilm(); message = "Auf Original zurückgesetzt."
                }
                if (vm.isUserPreset) Pill("Löschen", selected = false, small = true, enabled = enabled) {
                    vm.preset?.let { vm.deleteUserPreset(it) }; message = "Gelöscht."
                }
            }
        }
        message?.let { Text(it, color = FilmWhite.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/**
 * Projektor-Rattern: Modell, Lautstärke, Material (Metall … Plastik), Klang (dumpf … klar),
 * Tempo an die Film-fps gebunden oder frei, und eine Live-Vorschau.
 */
@UnstableApi
@Composable
private fun ProjectorSection(vm: FilmViewModel, enabled: Boolean) {
    val l = vm.look
    val context = LocalContext.current
    val preview = remember {
        com.volkskamera.app.render.ProjectorPreview { sr, look ->
            val syn = com.volkskamera.app.render.ProjectorSynth.forLook(sr, look, com.volkskamera.app.render.ClickSamples.load(context))
            return@ProjectorPreview { syn.next() }
        }
    }
    var playing by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { preview.stop() } }
    LaunchedEffect(l) { if (playing) preview.update(l) }
    val on = enabled && l.projector

    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Projektor-Rattern", selected = l.projector, enabled = enabled) {
            vm.edit { copy(projector = !projector) }
            if (playing) { preview.stop(); playing = false }
        }
        Pill(if (playing) "■ Stopp" else "▶ Anhören", selected = playing, enabled = on) {
            if (playing) preview.stop() else preview.start(l)
            playing = !playing
        }
    }
    PillRow(com.volkskamera.app.render.ProjectorModel.entries.toList(), { it.label },
        { it == l.projectorModel && l.projectorRecording == null }, on) { m ->
        vm.edit { copy(projectorModel = m, projectorRecording = null) }
    }
    // echte Projektoraufnahmen statt Klangerzeuger
    var pickRec by remember { mutableStateOf(false) }
    LaunchedEffect(l.projectorRecording) {
        withContext(Dispatchers.IO) {
            com.volkskamera.app.data.SoundLibrary.builtIn(context)
            com.volkskamera.app.data.SoundLibrary.load(context, l.projectorRecording)
        }
        if (playing) preview.update(l)
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aufnahme", color = FilmWhite.copy(alpha = 0.8f))
        Pill(com.volkskamera.app.data.SoundLibrary.nameOf(context, l.projectorRecording)?.let { "$it …" } ?: "Echte Aufnahme wählen …",
            selected = l.projectorRecording != null, enabled = on) { pickRec = true }
    }
    if (pickRec) SoundPickerDialog("Projektor-Aufnahme", com.volkskamera.app.data.SoundKind.PROJEKTOR, l.projectorRecording,
        onPick = { id -> vm.edit { copy(projectorRecording = id) }; pickRec = false }, onDismiss = { pickRec = false })
    Spacer(Modifier.height(4.dp))
    LabeledSlider("Lautstärke", l.projectorVolume, on) { v -> vm.edit { copy(projectorVolume = v) } }
    val hasMaterial = l.projectorModel != com.volkskamera.app.render.ProjectorModel.KLASSISCH && l.projectorRecording == null
    BipolarSlider("Material", l.projectorMaterial * 2 - 1, on && hasMaterial, "Metall", "Plastik") { v -> vm.edit { copy(projectorMaterial = (v + 1) / 2) } }
    BipolarSlider("Klang", l.projectorTone, on, "dumpf", "klar") { v -> vm.edit { copy(projectorTone = v) } }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Tempo = Film-fps", selected = l.projectorSyncFps, small = true, enabled = on) { vm.edit { copy(projectorSyncFps = true) } }
        Pill("Tempo frei", selected = !l.projectorSyncFps, small = true, enabled = on) { vm.edit { copy(projectorSyncFps = false) } }
    }
    LabeledSlider("Tempo", (l.projectorSpeed - 6f) / 30f, on && !l.projectorSyncFps) { v ->
        vm.edit { copy(projectorSpeed = 6f + v * 30f) }
    }
    val rate = com.volkskamera.app.render.ProjectorSynth.rateFor(l)
    val recEntry = com.volkskamera.app.data.SoundLibrary.peek(l.projectorRecording)
    val rateText = when {
        l.projectorRecording == null -> "%.0f Anschläge pro Sekunde%s".format(rate * l.projectorModel.strikes,
            if (l.projectorModel.strikes == 2) " (Doppelschlag)" else "")
        recEntry != null && recEntry.rate > 0f && recEntry.confidence >= 0.15f ->
            "Aufnahme: ~%.0f Klicks/s, wird sanft ans Tempo angepasst".format(recEntry.rate)
        else -> "Aufnahme läuft in Originaltempo"
    }
    Text(rateText, color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(start = 112.dp))
}

/** Knacken wie auf alten Filmkopien – eigene Stufe neben der Alterung, auch bei Stumm. */
@UnstableApi
@Composable
private fun CrackleSection(vm: FilmViewModel, enabled: Boolean) {
    val l = vm.look
    val preview = remember {
        com.volkskamera.app.render.ProjectorPreview { sr, look ->
            val c = com.volkskamera.app.render.CrackleSynth.forLook(sr, look)
            return@ProjectorPreview { c.next() }
        }
    }
    var playing by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { preview.stop() } }
    LaunchedEffect(l) { if (playing) preview.update(l) }
    val on = enabled && l.crackleOn
    if (playing && !on) { preview.stop(); playing = false }

    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Knacken", selected = l.crackleOn, enabled = enabled) { vm.edit { copy(crackleOn = !crackleOn) } }
        Pill(if (playing) "■ Stopp" else "▶ Anhören", selected = playing, enabled = on) {
            if (playing) preview.stop() else preview.start(l)
            playing = !playing
        }
    }
    LabeledSlider("Stärke", l.crackleAmount, on) { v -> vm.edit { copy(crackleAmount = v) } }
    LabeledSlider("Häufigkeit", l.crackleDensity, on) { v -> vm.edit { copy(crackleDensity = v) } }
}

/**
 * Mikrofon der Zeit: Aufnahmeketten der 1920er … 1980er als Profile, dazu Verzerrung, Bandbreite,
 * Rauschen und Automatik. Zum Anhören eine kurze eigene Sprachprobe (bleibt nur im Speicher).
 */
@UnstableApi
@Composable
private fun MicSection(vm: FilmViewModel, enabled: Boolean) {
    val l = vm.look
    val context = LocalContext.current
    val on = enabled && l.audioMode != com.volkskamera.app.render.AudioMode.STUMM
    val micOn = on && l.mic != null
    var decade by remember { mutableStateOf(l.mic?.decade) }
    LaunchedEffect(l.mic) { l.mic?.let { decade = it.decade } }

    val probe = vm.probe
    val pos = remember { IntArray(1) }
    val preview = remember(probe) {
        com.volkskamera.app.render.ProjectorPreview { sr, look ->
            val pr = probe ?: return@ProjectorPreview { 0f }
            val proc = com.volkskamera.app.render.FilmAudioProcessor.always(look,
                com.volkskamera.app.render.ClickSamples.load(context)).apply { preparePreview(sr) }
            return@ProjectorPreview {
                val x = pr[pos[0] % pr.size] / 32768f
                pos[0] = (pos[0] + 1) % pr.size
                proc.previewSample(x)
            }
        }
    }
    var playing by remember(probe) { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(preview) { onDispose { preview.stop() } }
    LaunchedEffect(l) { if (playing) preview.update(l) }
    if (playing && (!on || vm.probeRecording)) { preview.stop(); playing = false }

    Text("Mikrofon der Zeit", color = FilmWhite.copy(alpha = 0.8f), modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp))
    PillRow(listOf<String?>(null) + com.volkskamera.app.render.MicProfile.decades, { it ?: "Keins" }, { it == decade }, on) { d ->
        decade = d
        if (d == null) vm.edit { withMic(null) }
        else if (l.mic?.decade != d) vm.edit { withMic(com.volkskamera.app.render.MicProfile.entries.first { it.decade == d }) }
    }
    decade?.let { d ->
        Spacer(Modifier.height(6.dp))
        PillRow(com.volkskamera.app.render.MicProfile.entries.filter { it.decade == d }, { it.label }, { it == l.mic }, on) { m ->
            vm.edit { withMic(m) }
        }
    }
    l.mic?.let {
        Text(it.hint, color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    LabeledSlider("Verzerrung", l.micDrive, micOn) { v -> vm.edit { copy(micDrive = v) } }
    BipolarSlider("Bandbreite", l.micBandwidth, micOn, "enger", "weiter") { v -> vm.edit { copy(micBandwidth = v) } }
    LabeledSlider("Rauschen", l.micNoise, micOn) { v -> vm.edit { copy(micNoise = v) } }
    LabeledSlider("Automatik", l.micAgc, micOn) { v -> vm.edit { copy(micAgc = v) } }

    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (vm.probeRecording) "● Sprich jetzt …" else "● Sprachprobe (6 s)", selected = vm.probeRecording,
            enabled = on && !vm.probeRecording) { vm.recordProbe() }
        Pill(if (playing) "■ Stopp" else "▶ Probe anhören", selected = playing, enabled = on && probe != null && !vm.probeRecording) {
            if (playing) preview.stop() else { pos[0] = 0; preview.start(l) }
            playing = !playing
        }
    }
    Text("Die Probe klingt wie der fertige Film (Mikrofon, Alterung, Knacken, Rattern, Hintergrund). Sie wird nicht gespeichert.",
        color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * Effekt-Taste und die beiden Ambient-Regler der Kamera: Belegung, Vorhören, Lautstärke und
 * ob sie durch Mikrofon & Alterung laufen ("von den Toneinstellungen beeinflusst").
 */
@UnstableApi
@Composable
private fun FxAmbientSection(vm: FilmViewModel, enabled: Boolean) {
    val l = vm.look
    Section("Effekt-Taste & Ambient-Regler")
    Text("Liegen in der Kamera: die Taste spielt den Effekt an der Stelle im Film, die Regler steuern die Lautstärke beim Filmen.",
        color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
    SoundSlot("Effekt-Taste", com.volkskamera.app.data.SoundKind.EFFEKT, l.fxSound, l.fxVolume, l.fxChain, enabled, "Lautstärke",
        onPick = { vm.edit { copy(fxSound = it) } }, onLevel = { vm.edit { copy(fxVolume = it) } },
        onChain = { vm.edit { copy(fxChain = !fxChain) } })
    SoundSlot("Regler 1", com.volkskamera.app.data.SoundKind.AMBIENT, l.ambA, l.ambALevel, l.ambAChain, enabled, "Pegel",
        onPick = { vm.edit { copy(ambA = it) } }, onLevel = { vm.edit { copy(ambALevel = it) } },
        onChain = { vm.edit { copy(ambAChain = !ambAChain) } })
    SoundSlot("Regler 2", com.volkskamera.app.data.SoundKind.AMBIENT, l.ambB, l.ambBLevel, l.ambBChain, enabled, "Pegel",
        onPick = { vm.edit { copy(ambB = it) } }, onLevel = { vm.edit { copy(ambBLevel = it) } },
        onChain = { vm.edit { copy(ambBChain = !ambBChain) } })
}

@Composable
private fun SoundSlot(
    title: String, kind: com.volkskamera.app.data.SoundKind, id: String?, level: Float, chain: Boolean, enabled: Boolean,
    levelLabel: String, onPick: (String?) -> Unit, onLevel: (Float) -> Unit, onChain: () -> Unit,
) {
    val context = LocalContext.current
    val player = rememberSoundPlayer()
    var picking by remember { mutableStateOf(false) }
    val on = enabled && id != null
    Text(title, color = FilmWhite.copy(alpha = 0.8f), modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(com.volkskamera.app.data.SoundLibrary.nameOf(context, id)?.let { "$it …" } ?: "Belegen …", selected = id != null, enabled = enabled) {
            player.stop(); picking = true
        }
        Pill(if (player.playingId != null) "■ Stopp" else "▶ Anhören", selected = player.playingId != null, enabled = on) {
            if (player.playingId != null) player.stop() else id?.let { player.play(it, kind != com.volkskamera.app.data.SoundKind.EFFEKT) }
        }
    }
    LabeledSlider(levelLabel, level, on, onLevel)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Pill("Durch Mikrofon & Alterung", selected = chain, small = true, enabled = on, onClick = onChain)
    }
    if (picking) SoundPickerDialog(title, kind, id, onPick = { onPick(it); picking = false }, onDismiss = { picking = false })
}

/**
 * Hintergrundgeräusche: Rauschen (Weiß … Violett, Bandrauschen) und Brummen/Ton mit frei
 * wählbarer Frequenz – vom mechanischen Wummern bis zum digitalen Piepen. Eigene Vorschau.
 */
@UnstableApi
@Composable
private fun BackgroundSection(vm: FilmViewModel, enabled: Boolean) {
    val l = vm.look
    val preview = remember {
        com.volkskamera.app.render.ProjectorPreview { sr, look ->
            val syn = com.volkskamera.app.render.BackgroundSynth(sr, look)
            return@ProjectorPreview { syn.next() }
        }
    }
    var playing by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { preview.stop() } }
    LaunchedEffect(l) { if (playing) preview.update(l) }
    if (playing && !l.hasBackground) { preview.stop(); playing = false }

    Section("Hintergrund")
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Rauschen", selected = l.bgNoiseOn, enabled = enabled) { vm.edit { copy(bgNoiseOn = !bgNoiseOn) } }
        Pill("Brummen / Ton", selected = l.bgHumOn, enabled = enabled) { vm.edit { copy(bgHumOn = !bgHumOn) } }
        Pill(if (playing) "■ Stopp" else "▶ Anhören", selected = playing, enabled = enabled && l.hasBackground) {
            if (playing) preview.stop() else preview.start(l)
            playing = !playing
        }
    }
    PillRow(com.volkskamera.app.render.NoiseType.entries.toList(), { it.label }, { it == l.bgNoiseType }, enabled && l.bgNoiseOn) { t ->
        vm.edit { copy(bgNoiseType = t) }
    }
    LabeledSlider("Pegel", l.bgNoiseLevel, enabled && l.bgNoiseOn) { v -> vm.edit { copy(bgNoiseLevel = v) } }
    Spacer(Modifier.height(6.dp))
    PillRow(com.volkskamera.app.render.HumType.entries.toList(), { it.label }, { it == l.bgHumType }, enabled && l.bgHumOn) { t ->
        vm.edit { copy(bgHumType = t) }
    }
    // Frequenz logarithmisch: 20 Hz … 8 kHz
    val pos = (kotlin.math.ln(l.bgHumFreq / 20f) / kotlin.math.ln(400f)).coerceIn(0f, 1f)
    LabeledSlider("Frequenz", pos, enabled && l.bgHumOn) { v ->
        vm.edit { copy(bgHumFreq = (20f * Math.pow(400.0, v.toDouble()).toFloat())) }
    }
    Text(
        "%s Hz  ·  %s".format(if (l.bgHumFreq >= 1000) "%.1f k".format(l.bgHumFreq / 1000) else "%.0f".format(l.bgHumFreq),
            when { l.bgHumFreq < 60 -> "Wummern"; l.bgHumFreq < 250 -> "Brummen"; l.bgHumFreq < 1200 -> "Summen"; else -> "Piepen" }),
        color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(start = 112.dp),
    )
    LabeledSlider("Pegel", l.bgHumLevel, enabled && l.bgHumOn) { v -> vm.edit { copy(bgHumLevel = v) } }
}

