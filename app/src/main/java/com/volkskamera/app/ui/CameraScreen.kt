package com.volkskamera.app.ui

import com.volkskamera.app.t
import com.volkskamera.app.tf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.Surface
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import com.volkskamera.app.ui.theme.lightShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.camera.CameraController
import com.volkskamera.app.camera.LensOption
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmRed
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Aufnahme-Bildschirm nach der Skizze vom 24.09.:
 * Querformat – Sucher + Bedienspalte (rechts bei Drehung nach links = Rechtshänder,
 * links bei Drehung nach rechts = Linkshänder); Hochformat – Sucher oben, Bedienung unten.
 * Der Sucher zeigt das reine Kamerabild (wie eine Optik), der Look kommt beim Entwickeln.
 */
@UnstableApi
@Composable
fun CameraScreen(
    vm: FilmViewModel,
    audioAllowed: Boolean,
    onOpenSettings: () -> Unit,
    onOpenFilm: (android.net.Uri) -> Unit,
    onOpenFilms: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val controller = remember { CameraController(context.applicationContext) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var lenses by remember { mutableStateOf<List<LensOption>>(emptyList()) }
    var lensIdx by remember { mutableStateOf(com.volkskamera.app.data.CameraPrefs.lensIdx(context)) }
    var shutterIdx by remember {
        mutableStateOf(SHUTTER_SPEEDS.indexOfFirst { it.first == com.volkskamera.app.data.CameraPrefs.shutter(context) }
            .let { if (it < 0) SHUTTER_SPEEDS.indexOfFirst { s -> s.first == "1/50" } else it })
    }
    LaunchedEffect(lensIdx, shutterIdx) {
        com.volkskamera.app.data.CameraPrefs.setLens(context, lensIdx)
        com.volkskamera.app.data.CameraPrefs.setShutter(context, SHUTTER_SPEEDS[shutterIdx].first)
    }
    // Aufnahme-Einstellungen (Auflösung, Frontkamera spiegeln) vor jedem Binden übernehmen
    val resolution = vm.recordResolution
    val mirrorFront = vm.mirrorFront
    // Belichtung (Zeit + Film-ISO) VOR dem Binden setzen: neue Kamera startet direkt manuell
    val filmIso = vm.selectedFilm?.iso ?: 100
    controller.setExposure(SHUTTER_SPEEDS[shutterIdx].second, filmIso)
    controller.resolution = resolution
    controller.mirrorFront = mirrorFront
    // Sucher: PreviewView zeigt die Frontkamera immer gespiegelt – ohne Spiegeln zurückdrehen
    val isFront = lenses.getOrNull(lensIdx)?.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT
    LaunchedEffect(isFront, mirrorFront) { previewView.scaleX = if (isFront && !mirrorFront) -1f else 1f }
    var torch by remember { mutableStateOf(false) }
    var aeLocked by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var sensorFps by remember { mutableStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    // Eine Aufnahme ("Take") besteht aus Stücken: jede Pause beendet ein Stück, "C" beginnt das
    // nächste. Erst Stopp über den Auslöser entwickelt alle Stücke zusammen als einen Film.
    var takeParts by remember { mutableStateOf(listOf<java.io.File>()) }
    var paused by remember { mutableStateOf(false) }
    var doneMs by remember { mutableLongStateOf(0L) }
    var stopRequested by remember { mutableStateOf(false) }
    val takeActive = recording || paused
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var manualFocus by remember { mutableStateOf(false) }
    // Mitschrift für den Ton: Effekt-Taste und Ambient-Regler, Zeit = Aufnahmezeit ohne Pausen
    var statusAt by remember { mutableLongStateOf(0L) }
    val fxCues = remember { mutableListOf<com.volkskamera.app.render.SoundCues.Cue>() }
    val levelsA = remember { mutableListOf<com.volkskamera.app.render.SoundCues.Level>() }
    val levelsB = remember { mutableListOf<com.volkskamera.app.render.SoundCues.Level>() }
    val fxPlayer = rememberSoundPlayer()
    fun takeTimeMs(): Long = doneMs + elapsedMs +
        if (recording) (android.os.SystemClock.elapsedRealtime() - statusAt).coerceIn(0L, 500L) else 0L
    // Effekt vorab laden, damit die Taste sofort reagiert
    LaunchedEffect(vm.look.fxSound) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.volkskamera.app.data.SoundLibrary.builtIn(context)
            com.volkskamera.app.data.SoundLibrary.load(context, vm.look.fxSound)
        }
    }

    val aspect43 = vm.look.aspect == 4f / 3f
    val rotation = displayRotation(context, LocalConfiguration.current)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val developing = vm.render is RenderState.Running

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        controller.loadLenses { lenses = it }
        onDispose {
            view.keepScreenOn = false
            controller.unbind()
        }
    }
    // gemerkter Objektiv-Index könnte für dieses Gerät zu groß sein -> begrenzen
    LaunchedEffect(lenses) { if (lenses.isNotEmpty() && lensIdx > lenses.lastIndex) lensIdx = 0 }
    // (Neu) binden bei Objektiv- oder Formatwechsel – nie während einer Aufnahme
    LaunchedEffect(lenses, lensIdx, aspect43, resolution, mirrorFront) {
        val lens = lenses.getOrNull(lensIdx) ?: return@LaunchedEffect
        torch = false
        manualFocus = false
        controller.bind(owner, previewView, lens, aspect43, rotation) { _, fps ->
            sensorFps = fps
            aeLocked = false
            hasFlash = controller.hasFlash()
        }
    }
    LaunchedEffect(rotation) { controller.setRotation(rotation) }
    // Farblook im Sucher: Farbmatrix als Layer-Filter der View – kostet die Kamera keine fps
    val finderMatrix = vm.viewfinderMatrix
    LaunchedEffect(finderMatrix?.toList()) {
        if (finderMatrix == null) {
            previewView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
        } else {
            val paint = android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(finderMatrix))
            }
            previewView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
        }
    }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) { delay(2500); if (!recording) focusPoint = null }
    }

    fun finishTake(parts: List<java.io.File>) {
        takeParts = emptyList()
        paused = false
        doneMs = 0
        stopRequested = false
        val cues = com.volkskamera.app.render.SoundCues(fxCues.toList(), levelsA.toList(), levelsB.toList())
        fxCues.clear(); levelsA.clear(); levelsB.clear()
        vm.onRecorded(parts, cues)
    }

    fun startPart() {
        val start = {
            controller.startRecording(vm.newRawFile(), audioAllowed,
                onStatus = { elapsedMs = it; statusAt = android.os.SystemClock.elapsedRealtime() },
                onFinished = { file ->
                    recording = false
                    doneMs += elapsedMs
                    elapsedMs = 0
                    val parts = takeParts + listOfNotNull(file)
                    takeParts = parts
                    if (stopRequested) finishTake(parts) else paused = true
                })
            recording = true
            paused = false
            statusAt = android.os.SystemClock.elapsedRealtime()
            // Reglerstellung zu Beginn jedes Stücks festhalten
            vm.look.ambA?.let { levelsA += com.volkskamera.app.render.SoundCues.Level(doneMs, vm.look.ambALevel) }
            vm.look.ambB?.let { levelsB += com.volkskamera.app.render.SoundCues.Level(doneMs, vm.look.ambBLevel) }
        }
        // Aufnahme sofort starten – scharfgestellt wird nur durch Antippen (nie automatisch)
        start()
    }

    /** Auslöser: Aufnahme starten bzw. komplett beenden (auch aus der Pause heraus). */
    fun toggleRecord() {
        when {
            recording -> { stopRequested = true; controller.stopRecording() }
            paused -> finishTake(takeParts)
            !developing -> {
                takeParts = emptyList(); doneMs = 0; stopRequested = false
                fxCues.clear(); levelsA.clear(); levelsB.clear()
                startPart()
            }
        }
    }

    /** P/C: Stück beenden (Pause) bzw. nächstes Stück beginnen (Continue). */
    fun togglePause() {
        when {
            recording -> { stopRequested = false; controller.stopRecording() }
            paused -> startPart()
        }
    }

    // ---------- Bausteine ----------

    val housing = vm.housing
    val finder: @Composable (Modifier) -> Unit = { mod ->
        val a = if (aspect43) 4f / 3f else 16f / 9f
        Box(mod, contentAlignment = Alignment.Center) {
            Box(
                Modifier.aspectRatio(if (landscape) a else 1f / a, matchHeightConstraintsFirst = landscape)
                    .lightShadow(RoundedCornerShape(10.dp), 7.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(3.dp, housing.metal.light, RoundedCornerShape(10.dp))
            ) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                // leichter Innenschatten für Tiefe (dunklere Ränder/Ecken)
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, Color(0x55000000)),
                    radius = 1400f)))
                ThirdsGrid(Modifier.fillMaxSize())
                // Antippen = gezielt scharfstellen (auch während der Aufnahme)
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { p ->
                        controller.focusAt(previewView.meteringPointFactory.createPoint(p.x, p.y))
                        focusPoint = p
                        manualFocus = true
                    }
                })
                focusPoint?.let { FocusBox(it) }
                if (takeActive) {
                    RecordingBadge(doneMs + elapsedMs, paused, Modifier.align(Alignment.TopStart).padding(10.dp))
                }
                // Ambient-Regler links/rechts, Effekt-Taste unten links
                val faderH = if (landscape) 120.dp else 170.dp
                val l = vm.look
                l.ambA?.let { id ->
                    AmbientFader(l.ambALevel, soundShortName(context, id), faderH, Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) { v ->
                        vm.editQuiet { copy(ambALevel = v) }
                        if (recording) levelsA += com.volkskamera.app.render.SoundCues.Level(takeTimeMs(), v)
                    }
                }
                l.ambB?.let { id ->
                    AmbientFader(l.ambBLevel, soundShortName(context, id), faderH, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) { v ->
                        vm.editQuiet { copy(ambBLevel = v) }
                        if (recording) levelsB += com.volkskamera.app.render.SoundCues.Level(takeTimeMs(), v)
                    }
                }
                l.fxSound?.let { id ->
                    FxButton(soundShortName(context, id), Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        if (recording) {
                            fxCues += com.volkskamera.app.render.SoundCues.Cue(takeTimeMs(), id)
                            // hörbar nur über Kopfhörer – sonst nähme das Mikrofon den Effekt doppelt auf
                            if (headphonesConnected(context)) fxPlayer.play(id, false)
                        } else fxPlayer.play(id, false)   // außerhalb der Aufnahme: vorhören
                    }
                }
            }
        }
    }
    // Belichtungszeit anwenden (analog: feste Film-ISO, Helligkeit über die Zeit) – sofort, ohne Verzögerung
    LaunchedEffect(shutterIdx, filmIso) { controller.setExposure(SHUTTER_SPEEDS[shutterIdx].second, filmIso) }
    // Weißabgleich bleibt automatisch: die Farbe kommt allein aus der LUT des Films
    LaunchedEffect(lenses, lensIdx) {
        kotlinx.coroutines.delay(350)
        controller.setWhiteBalance(android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO)
    }
    // ----- Zählwerk-Wähler (mechanischer Counter-Stil) -----
    val fpsCounter: @Composable () -> Unit = {
        val opts = FPS_CHOICES + listOf(null)
        val cur = vm.look.targetFps
        val label = cur?.toInt()?.toString() ?: "--"
        CounterField(t("BILD/S"), label, housing.metal, enabled = !takeActive, drums = 2) {
            val i = opts.indexOfFirst { it == cur }.let { if (it < 0) 0 else it }
            vm.edit { copy(targetFps = opts[(i + 1) % opts.size]) }
        }
    }
    val formatCounter: @Composable () -> Unit = {
        CounterField(t("FORMAT"), if (aspect43) "4:3" else "16:9", housing.metal, enabled = !takeActive, drums = 4) {
            vm.edit { copy(aspect = if (aspect43) 16f / 9f else 4f / 3f) }
        }
    }
    val lensCounter: @Composable () -> Unit = {
        val label = lenses.getOrNull(lensIdx)?.label?.let { if (it == "Front") "FR" else it } ?: "-"
        CounterField(t("OBJEKTIV"), label, housing.metal, enabled = !recording && lenses.size > 1, drums = 3) {
            if (lenses.isNotEmpty()) lensIdx = (lensIdx + 1) % lenses.size
        }
    }
    val zeitCounter: @Composable () -> Unit = {
        // „1/“ fest aufgedruckt, die Rollen zeigen den Nenner (Auto: „AUTO“, Aufdruck ausgeblendet)
        val zeit = SHUTTER_SPEEDS[shutterIdx].first
        CounterField(t("ZEIT"), zeit.removePrefix("1/"), housing.metal,
            enabled = !takeActive, drums = 4, prefix = "1/") {
            shutterIdx = (shutterIdx + 1) % SHUTTER_SPEEDS.size
        }
    }
    // Anzeige (nicht verstellbar): Empfindlichkeit des gewählten Films – wie die ASA-Scheibe der Kamera
    val isoCounter: @Composable () -> Unit = {
        // vierstellig mit führenden Nullen wie ein Rollenzählwerk (0008, 0100, 1200); ohne Normangabe: ----
        CounterField(t("ISO / ASA"), vm.selectedFilm?.iso?.toString() ?: "----", housing.metal, enabled = true,
            drums = 4, pad = '0') { }
    }
    val counterGrid: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { fpsCounter(); formatCounter() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { lensCounter(); zeitCounter() }
            isoCounter()
        }
    }
    val shutter: @Composable () -> Unit = {
        Shutter(takeActive, enabled = takeActive || (!developing && lenses.isNotEmpty()), style = housing.button) { toggleRecord() }
    }
    // Pause-Knopf: nur während einer Aufnahme sichtbar, Platz bleibt reserviert (nichts verrutscht)
    val pauseBtn: @Composable () -> Unit = {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (takeActive) PauseButton(paused) { togglePause() }
        }
    }
    val led: @Composable () -> Unit = {
        if (isFront) {
            // Frontkamera: statt Blitz-LED die Spiegel-Taste (Aufnahme wie im Spiegel oder seitenrichtig)
            // immer im Gehäuse-Metall; der Zustand steckt im Symbol (gespiegelt / seitenrichtig)
            RoundIcon(if (mirrorFront) Icons.Filled.Flip else Icons.Filled.CameraFront,
                if (mirrorFront) t("Gespiegelt (wie ein Spiegel)") else t("Seitenrichtig"),
                active = false, enabled = !takeActive, metal = housing.metal) {
                vm.chooseMirrorFront(!mirrorFront)
            }
        } else RoundIcon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "LED", active = torch, enabled = hasFlash, metal = housing.metal) {
            torch = !torch
            controller.setTorch(torch)
        }
    }
    // AE-/AWB-Sperre: Helligkeit und Weißabgleich festsetzen bzw. lösen (auch während der Aufnahme)
    val aeLock: @Composable () -> Unit = {
        RoundText(if (aeLocked) t("AE\uD83D\uDD12") else "AE", active = aeLocked, enabled = lenses.isNotEmpty(), metal = housing.metal) {
            aeLocked = !aeLocked
            controller.setExposureLock(aeLocked)
        }
    }
    val thumb: @Composable () -> Unit = {
        // Vorschaubild: letzter Film im eigenen Player, ohne Film die Übersicht
        Thumbnail(vm) {
            if (!takeActive) vm.lastFilm?.let(onOpenFilm) ?: onOpenFilms()
        }
    }
    val gear: @Composable () -> Unit = {
        RoundIcon(Icons.Filled.Settings, t("Einstellungen"), active = false, enabled = !takeActive, metal = housing.metal, onClick = onOpenSettings)
    }

    // ---------- Anordnung ----------

    val titleFont = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(housing.titleFont.res))
    // eine Lichtquelle für alles: Gehäuse, Schatten, Schriftzug (folgt Neigen/Schwenken oder fest)
    val lightHolder = com.volkskamera.app.ui.theme.rememberSceneLight(housing.light)
    androidx.compose.runtime.CompositionLocalProvider(com.volkskamera.app.ui.theme.LocalLight provides lightHolder) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // Gehäuse: Material-Textur mit Licht (folgt optional der Neigung) oder eigenes Bild
        HousingBackground(housing)
      Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Metall-Schriftzug oben mittig: Glanz je Buchstabe zur Lichtseite, Schatten vom Licht weg
        MetalTitle(housing, lightHolder, titleFont, Modifier.align(Alignment.TopCenter))
        // Hersteller + Film/Variante oben links über dem Sucher
        val labelTop = vm.activeCustom?.let { t("Eigene LUT") } ?: vm.selectedFilm?.hersteller
        val filterSuffix = if (vm.selectedFilm?.farbeSw == "S/W" && vm.bwFilter != com.volkskamera.app.render.BwFilter.KEINER)
            tf(" · %s-Filter", vm.bwFilter.label) else ""
        val labelMain = (vm.activeCustom?.name ?: vm.selectedFilm?.variantLabel?.let { t(it) })?.let { it + filterSuffix }
        if (labelTop != null && labelMain != null && !landscape) {
            // hochkant: einzeilig unter dem Schriftzug (sonst überlappt er den Titel)
            Text("$labelTop · $labelMain", color = FilmWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xCC000000), offset = Offset(0f, 1f), blurRadius = 3f)),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, end = 12.dp, top = 54.dp))
        }
        if (labelTop != null && labelMain != null && landscape) {
            Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 6.dp)) {
                Text(labelTop, color = housing.metal.light, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(labelMain, color = FilmWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xCC000000), offset = Offset(0f, 1f), blurRadius = 3f)))
            }
        }
        if (landscape) {
            val controlsLeft = rotation == Surface.ROTATION_270   // Linkshänder-Haltung
            val controls: @Composable () -> Unit = {
                Column(
                    Modifier.fillMaxHeight().width(236.dp).padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
                        gear()
                    }
                    counterGrid()
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        shutter()
                        Box(Modifier.align(Alignment.CenterEnd)) { pauseBtn() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        led(); thumb()
                    }
                }
            }
            Row(Modifier.fillMaxSize()) {
                if (controlsLeft) controls()
                finder(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 48.dp))
                if (!controlsLeft) controls()
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                finder(Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 78.dp))
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { counterGrid() }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) { gear(); led() }
                    Box(Modifier.align(Alignment.Center)) { shutter() }
                    Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) { pauseBtn(); thumb() }
                }
            }
        }
        if (sensorFps in 1..59 && !takeActive) {
            Text(tf("Sucher: %d fps", sensorFps), color = FilmWhite.copy(alpha = 0.5f), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp))
        }
      }
    }
    } // CompositionLocalProvider (Licht)
}

private fun displayRotation(context: Context, @Suppress("UNUSED_PARAMETER") config: Configuration): Int =
    @Suppress("DEPRECATION")
    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

@Composable
private fun ThirdsGrid(modifier: Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.5f)
            drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.5f)
        }
    }
}

/** Gelbe Box mit runden Ecken an der Fokusstelle – im Stil der Auswahl-Pillen. */
@Composable
private fun FocusBox(p: Offset) {
    val half = with(LocalDensity.current) { 36.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }
            .size(72.dp)
            .border(2.dp, FilmAccent, RoundedCornerShape(14.dp)),
    )
}

@Composable
private fun RecordingBadge(ms: Long, paused: Boolean, modifier: Modifier) {
    val s = ms / 1000
    val blink = blinkAlpha()
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (paused) FilmAccent.copy(alpha = blink) else FilmRed))
        Spacer(Modifier.width(6.dp))
        Text("%d:%02d".format(s / 60, s % 60), color = FilmWhite, fontWeight = FontWeight.Bold)
        if (paused) Text(t("  PAUSE"), color = FilmAccent.copy(alpha = blink), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun blinkAlpha(): Float {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "blink")
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(550), androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "blinkAlpha",
    )
    return a
}

/** "P" während der Aufnahme, blinkendes "C" in der Pause. */
@Composable
private fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    val alpha = if (paused) blinkAlpha() else 1f
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(if (paused) FilmAccent.copy(alpha = alpha) else Color(0x33FFFFFF))
            .border(2.dp, FilmWhite.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (paused) "C" else "P", color = if (paused) Color.Black else FilmWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}

@Composable
private fun Shutter(recording: Boolean, enabled: Boolean,
                    style: com.volkskamera.app.ui.theme.ButtonStyle, onClick: () -> Unit) {
    val holder = com.volkskamera.app.ui.theme.LocalLight.current
    val a = if (enabled) 1f else 0.3f
    val base = style.plastic ?: FilmRed
    val metal = style.metal
    fun mix(c: Color, w: Float, t: Color = Color.White) = Color(c.red + (t.red - c.red) * w, c.green + (t.green - c.green) * w, c.blue + (t.blue - c.blue) * w)
    fun dark(c: Color, w: Float) = Color(c.red * w, c.green * w, c.blue * w)
    Box(
        Modifier
            .size(76.dp)
            .lightShadow(CircleShape, 7.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                // Licht in Bildschirm-Koordinaten: Glanz liegt auf der Lichtseite, Verlauf folgt der Richtung
                val l = holder.light
                val r = size.minDimension / 2
                val c = center
                val dir = androidx.compose.ui.geometry.Offset(l.x, l.y).let { v ->
                    val n = kotlin.math.hypot(v.x, v.y).coerceAtLeast(0.15f); androidx.compose.ui.geometry.Offset(v.x / n, v.y / n) }
                val lvl = l.level
                // Metallring: heller auf der Lichtseite
                drawCircle(Brush.linearGradient(listOf(Color(0xFFF7F8FA).copy(alpha = a), Color(0xFFB9BDC1).copy(alpha = a), Color(0xFF6E7276).copy(alpha = a)),
                    start = c + dir * r, end = c - dir * r), r)
                drawCircle(Color(0xFF141414), r - 4.dp.toPx())
                // Knopf: Plastik (Radialverlauf) oder Metall (linear), jeweils zur Lichtseite hin heller
                // Knopfgröße für den Verlauf: rund (30 dp) bzw. beim Aufnehmen das Quadrat (15 dp) – nie 0
                val kr = if (recording) 15.dp.toPx() else 30.dp.toPx()
                val fill = if (metal != null)
                    Brush.linearGradient(listOf(mix(metal.light, 0.35f * lvl).copy(alpha = a), metal.light.copy(alpha = a), metal.dark.copy(alpha = a)),
                        start = c + dir * kr, end = c - dir * kr)
                else Brush.radialGradient(listOf(mix(base, 0.5f * lvl).copy(alpha = a), base.copy(alpha = a), dark(base, 0.45f).copy(alpha = a)),
                    center = c + dir * kr * 0.45f, radius = kr * 1.3f)
                if (recording) {
                    val q = 15.dp.toPx()
                    drawRoundRect(fill, topLeft = c - androidx.compose.ui.geometry.Offset(q, q), size = androidx.compose.ui.geometry.Size(2 * q, 2 * q),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                } else {
                    drawCircle(fill, kr)
                    // Glanzpunkt: wandert mit dem Licht, Größe/Stärke nach Lichthöhe
                    val hp = c + dir * kr * (0.55f - 0.25f * l.z)
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f * lvl * a), Color.Transparent),
                        center = hp, radius = kr * 0.38f), kr * 0.38f, hp)
                    // Gegenseite leicht abgeschattet (Wölbung)
                    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f * a)),
                        center = c + dir * kr * 0.3f, radius = kr * 1.1f), kr)
                }
            },
    )
}

/** Runde Taste mit kurzem Text (z.B. AE-Sperre), gleiche Größe wie die Symbol-Tasten. */
@Composable
private fun RoundText(label: String, active: Boolean, enabled: Boolean,
                      metal: com.volkskamera.app.ui.theme.MetalFinish, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Brush.linearGradient(listOf(FilmAccent, FilmAccent)) else metal.brush())
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = (if (active) Color.Black else metal.onColor).copy(alpha = if (enabled) 1f else 0.35f),
            fontWeight = FontWeight.Bold, fontSize = if (active) 11.sp else 13.sp)
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    active: Boolean,
    enabled: Boolean,
    metal: com.volkskamera.app.ui.theme.MetalFinish,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .lightShadow(CircleShape, 4.dp)
            .clip(CircleShape)
            .background(if (active) Brush.linearGradient(listOf(FilmAccent, FilmAccent)) else metal.brush())
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = (if (active) Color.Black else metal.onColor).copy(alpha = if (enabled) 1f else 0.35f))
    }
}

/** Vorschaubild der letzten Aufnahme; beim Entwickeln mit Fortschritt. */
@UnstableApi
@Composable
private fun Thumbnail(vm: FilmViewModel, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .lightShadow(RoundedCornerShape(12.dp), 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, FilmWhite.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        vm.lastFilmThumb?.let {
            Image(it.asImageBitmap(), t("Letzter Film"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        val r = vm.render
        if (r is RenderState.Running) {
            Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { r.percent / 100f }, color = FilmAccent, strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp))
                Text("${r.percent}", color = FilmWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}



/** Kurzname eines Geräuschs für die Kamera-Bedienelemente. */
private fun soundShortName(context: Context, id: String): String {
    val n = com.volkskamera.app.data.SoundLibrary.nameOf(context, id) ?: "?"
    return if (n.length > 12) n.take(11) + "…" else n
}

private fun headphonesConnected(context: Context): Boolean {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val types = setOf(
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
    )
    return am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
}

/** Senkrechter Lautstärkeregler im Sucher (Ambient), gelbe Füllung wie die Pillen. */
@Composable
private fun AmbientFader(value: Float, label: String, height: androidx.compose.ui.unit.Dp, modifier: Modifier, onChange: (Float) -> Unit) {
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(34.dp).height(height).clip(RoundedCornerShape(17.dp)).background(Color(0x66000000))
                .border(1.dp, FilmWhite.copy(alpha = 0.35f), RoundedCornerShape(17.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { c, _ ->
                        change((1f - c.position.y / size.height).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) { detectTapGestures { p -> change((1f - p.y / size.height).coerceIn(0f, 1f)) } },
        ) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                .background(FilmAccent.copy(alpha = 0.85f)))
        }
        Text(label, color = FilmWhite, fontSize = 10.sp, maxLines = 1,
            modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x88000000))
                .padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

/** Effekt-Taste: leuchtet kurz auf, wenn gedrückt. */
@Composable
private fun FxButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    var lit by remember { mutableStateOf(0) }
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(lit) { if (lit > 0) { on = true; delay(300); on = false } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (on) FilmAccent else Color(0x88000000))
                .border(2.dp, FilmAccent, CircleShape)
                .pointerInput(Unit) { detectTapGestures { lit++; onClick() } },
            contentAlignment = Alignment.Center,
        ) {
            Text("FX", color = if (on) Color.Black else FilmAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(label, color = FilmWhite, fontSize = 10.sp, maxLines = 1,
            modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x88000000))
                .padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

/** Belichtungszeiten (Anzeige, Zeit in Nanosekunden); null = Automatik. Analog-typische Werte. */
val SHUTTER_SPEEDS: List<Pair<String, Long>> = listOf(
    "1/5", "1/8", "1/10", "1/15", "1/24", "1/30", "1/48", "1/50", "1/60", "1/100", "1/125",
    "1/250", "1/500", "1/1000", "1/2000", "1/4000", "1/5000",
).map { it to 1_000_000_000L / it.removePrefix("1/").toLong() }

/**
 * Schriftzug in Metall: ruhiger Metallverlauf als Grundton, darüber für JEDEN Buchstaben ein dezenter
 * Glanz auf der Lichtseite und eine leichte Abschattung auf der Gegenseite (per Glyphen-Rahmen).
 * Der Glanz wird nur beim Zeichnen aus dem Licht gelesen – kein Neuaufbau bei jeder Bewegung.
 */
@Composable
private fun MetalTitle(
    housing: com.volkskamera.app.ui.theme.Housing,
    holder: com.volkskamera.app.ui.theme.LightHolder,
    font: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier,
) {
    val tm = housing.titleMetal
    val lc = housing.lightColor
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val l = holder.light
    val shadowLen = 2.5f / maxOf(l.z, 0.35f)
    Text(
        housing.titleText.ifBlank { com.volkskamera.app.ui.theme.Housing.DEFAULT_TITLE },
        fontFamily = font,
        fontSize = 44.sp,
        maxLines = 1,
        onTextLayout = { layout = it },
        style = androidx.compose.ui.text.TextStyle(
            brush = Brush.verticalGradient(listOf(tm.light, tm.light, tm.dark)),
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.7f * l.level),
                offset = Offset((-l.x * shadowLen).coerceIn(-6f, 6f), (-l.y * shadowLen).coerceIn(-6f, 6f)), blurRadius = 4f)),
        modifier = modifier
            .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val lay = layout ?: return@drawWithContent
                val li = holder.light
                val a = (0.55f * housing.lightStrength * li.level).coerceIn(0f, 0.7f)
                val glint = Color(lc.r, lc.g, lc.b).copy(alpha = a)
                val shade = Color.Black.copy(alpha = 0.28f * li.level)
                for (i in 0 until lay.layoutInput.text.length) {
                    if (lay.layoutInput.text[i].isWhitespace()) continue
                    val b = lay.getBoundingBox(i)
                    val c = b.center
                    val r = maxOf(b.width, b.height) * 0.55f
                    val toLight = Offset(li.x, li.y).let { v -> val n = kotlin.math.hypot(v.x, v.y).coerceAtLeast(0.2f); Offset(v.x / n, v.y / n) }
                    // Glanz nur auf der Lichtseite des Buchstabens, zur Mitte hin auslaufend
                    drawRect(Brush.linearGradient(listOf(glint, Color.Transparent, Color.Transparent, shade),
                        start = c + toLight * r, end = c - toLight * r),
                        topLeft = Offset(b.left - 4f, b.top - 4f),
                        size = androidx.compose.ui.geometry.Size(b.width + 8f, b.height + 8f),
                        blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                }
            },
    )
}
