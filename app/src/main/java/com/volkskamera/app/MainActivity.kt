package com.volkskamera.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.ui.CameraScreen
import com.volkskamera.app.ui.FilmViewModel
import com.volkskamera.app.ui.Pill
import com.volkskamera.app.ui.RenderState
import com.volkskamera.app.ui.theme.FilmWhite
import com.volkskamera.app.ui.theme.VolksKameraTheme
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {
    private val vm: FilmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        I18n.init(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        debugRenderTest()
        handleShared(intent)
        setContent {
            VolksKameraTheme {
                fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
                var camOk by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
                var micOk by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
                var screen by remember { mutableStateOf("kamera") }
                var playing by remember { mutableStateOf<android.net.Uri?>(null) }
                var editFilm by remember { mutableStateOf<com.volkskamera.app.data.FilmStock?>(null) }
                var filterFilm by remember { mutableStateOf<com.volkskamera.app.data.FilmStock?>(null) }
                var editCustom by remember { mutableStateOf<com.volkskamera.app.data.CustomLut?>(null) }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                    camOk = r[Manifest.permission.CAMERA] ?: camOk
                    micOk = r[Manifest.permission.RECORD_AUDIO] ?: micOk
                }
                LaunchedEffect(Unit) {
                    if (!camOk || !micOk) ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }
                val filmCatalog = remember { com.volkskamera.app.data.FilmCatalog.load(applicationContext) }
                LaunchedEffect(filmCatalog) { vm.restoreSelectedFilm(filmCatalog) }
                // Zurück-Taste: eine Ebene hoch statt App schließen (Bildschirme mit eigener
                // Behandlung – Filmauswahl, Filter, Mikrofon, Player … – haben Vorrang, da später komponiert)
                androidx.activity.compose.BackHandler(enabled = screen != "kamera") {
                    screen = when (screen) {
                        "gehaeuse", "lut_editor", "filter", "mikrofon", "hilfe", "aufnahme", "kombis" -> "filmwahl"
                        "editor" -> "filme"
                        else -> "kamera"
                    }
                }
                com.volkskamera.app.ui.IncomingComboDialog(vm, filmCatalog)
                when {
                    screen == "filmwahl" -> com.volkskamera.app.ui.FilmPickerScreen(
                        vm = vm,
                        catalog = filmCatalog,
                        onSelect = { vm.selectFilm(it); screen = "kamera" },
                        onEdit = { editFilm = it; editCustom = null; screen = "lut_editor" },
                        onSelectCustom = { vm.selectCustomLut(it, filmCatalog); screen = "kamera" },
                        onEditCustom = { editCustom = it; editFilm = null; screen = "lut_editor" },
                        onHousing = { screen = "gehaeuse" },
                        onMic = { screen = "mikrofon" },
                        onHelp = { screen = "hilfe" },
                        onRecording = { screen = "aufnahme" },
                        onCombos = { screen = "kombis" },
                        onFilter = { filterFilm = it; screen = "filter" },
                        onBack = { screen = "kamera" },
                    )
                    screen == "filter" -> com.volkskamera.app.ui.FilterScreen(vm, filterFilm ?: vm.selectedFilm) { screen = "filmwahl" }
                    screen == "kombis" -> com.volkskamera.app.ui.ComboScreen(vm, filmCatalog,
                        onUsed = { screen = "kamera" }, onBack = { screen = "filmwahl" })
                    screen == "aufnahme" -> com.volkskamera.app.ui.RecordingScreen(vm) { screen = "filmwahl" }
                    screen == "hilfe" -> com.volkskamera.app.ui.HelpScreen { screen = "filmwahl" }
                    screen == "mikrofon" -> com.volkskamera.app.ui.MicScreen(vm) { screen = "filmwahl" }
                    screen == "lut_editor" -> com.volkskamera.app.ui.LutEditorScreen(
                        vm, filmCatalog, film = editFilm, custom = editCustom,
                        onBack = { screen = "filmwahl" },
                    )
                    screen == "gehaeuse" -> com.volkskamera.app.ui.HousingScreen(vm) { screen = "filmwahl" }
                    screen == "filme" -> com.volkskamera.app.ui.FilmsScreen(
                        onOpen = { playing = it; screen = "player" },
                        onBack = { screen = "kamera" },
                        onCompose = { screen = "editor" },
                    )
                    screen == "editor" -> com.volkskamera.app.ui.EditorScreen(
                        vm,
                        onBack = { screen = "filme" },
                        onOpenResult = { playing = it; vm.editClear(); screen = "player" },
                    )
                    screen == "player" && playing != null -> com.volkskamera.app.ui.PlayerScreen(
                        uri = playing!!,
                        onBack = { screen = "kamera" },
                        onOverview = { screen = "filme" },
                        onDeleted = { vm.refreshLastFilm(); screen = "filme" },
                    )
                    camOk -> CameraScreen(
                        vm, audioAllowed = micOk,
                        onOpenSettings = {
                            // immer auf der Startseite der Einstellungen (Hersteller-Liste) beginnen
                            vm.pickerNav = com.volkskamera.app.ui.PickerNav(null, null, vm.selectedFilmId, eigene = false)
                            screen = "filmwahl"
                        },
                        onOpenFilm = { playing = it; screen = "player" },
                        onOpenFilms = { screen = "filme" },
                    )
                    else -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ohne Kamera-Berechtigung kann VolksKamera nicht filmen.", color = FilmWhite,
                                modifier = Modifier.padding(24.dp))
                            Pill("Berechtigung erteilen", selected = true) {
                                ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            }
                            Spacer(Modifier.height(12.dp))
                            Pill("Filmauswahl", selected = false) { screen = "filmwahl" }
                        }
                    }
                }
            }
        }
    }

    /**
     * Test ohne Oberfläche (z.B. bei gesperrtem Handy), nur im Debug-Build:
     *   adb push test.mp4 /data/local/tmp/ && adb shell run-as com.volkskamera.app cp /data/local/tmp/test.mp4 files/
     *   adb shell am start -n com.volkskamera.app/.MainActivity --es render_test test.mp4
     *        [--es preset Kodachrome|Expired|"Silent Film"] [--es format 4:3|16:9] [--ef fps 18]
     *        [--es nur lut,korn,staub,kratzer,leak,rahmen,halation,weich,flackern,wackeln] [--ez projektor false]
     *        [--ez countdown true] [--es burn film_burns_hd_009]
     * Ergebnis im Log unter "VolksKameraTest".
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleShared(intent)
    }

    /** Über „Teilen“ geschickte Kombination (Text mit „VK1:“) zur Bestätigung vormerken. */
    private fun handleShared(i: android.content.Intent?) {
        if (i?.action != android.content.Intent.ACTION_SEND) return
        val text = i.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: return
        com.volkskamera.app.data.Combo.parse(text)?.let { vm.pendingCombo = it }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun debugRenderTest() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        // Montage-Test: --es montage <MediaStore-IDs,kommagetrennt> --es intro KEIN|SCHWARZ|WEISS|COUNTDOWN
        //               --es outro KEIN|SCHWARZ|WEISS|FILMRISS [--ef blende 1.5]
        // Import-Test: --es import_test <Datei in files/> -> wie "Importieren" in den Einstellungen
        intent.getStringExtra("import_test")?.let { name ->
            vm.persistenceEnabled = false   // Test darf echte Einstellungen/Presets nicht verändern
            lifecycleScope.launch {
                while (vm.catalog == null) kotlinx.coroutines.delay(50)
                val ok = vm.importText(java.io.File(filesDir, name).readText())
                android.util.Log.i("VolksKameraTest", "Import ok=$ok Preset=${vm.preset} LUT=${vm.look.lutFile} Projektor=${vm.look.projectorModel}")
            }
            return
        }
        intent.getStringExtra("montage")?.let { ids ->
            vm.persistenceEnabled = false
            lifecycleScope.launch {
                while (vm.catalog == null) kotlinx.coroutines.delay(50)
                val clips = com.volkskamera.app.data.Films.list(this@MainActivity)
                ids.split(',').mapNotNull { id -> clips.firstOrNull { it.uri.lastPathSegment == id.trim() } }.forEach { vm.editAdd(it) }
                intent.getStringExtra("intro")?.let { vm.editIntro = com.volkskamera.app.render.RenderJob.Intro.valueOf(it) }
                intent.getStringExtra("outro")?.let { vm.editOutro = com.volkskamera.app.render.RenderJob.Outro.valueOf(it) }
                if (intent.hasExtra("blende")) vm.editFadeSec = intent.getFloatExtra("blende", 1.5f)
                val t0 = System.currentTimeMillis()
                vm.startMontage()
                while (vm.montage is RenderState.Running) kotlinx.coroutines.delay(200)
                android.util.Log.i("VolksKameraTest", "Ergebnis ${vm.montage} nach ${System.currentTimeMillis() - t0} ms")
            }
            return
        }
        val name = intent.getStringExtra("render_test") ?: return
        val file = java.io.File(filesDir, name)
        vm.persistenceEnabled = false   // Testeinstellungen nicht als echte Einstellungen merken
        lifecycleScope.launch {
            while (vm.catalog == null) kotlinx.coroutines.delay(50)
            intent.getStringExtra("preset")?.let { vm.applyPreset(it) }
            when (intent.getStringExtra("format")) {
                "4:3" -> vm.edit { copy(aspect = 4f / 3f) }
                "16:9" -> vm.edit { copy(aspect = 16f / 9f) }
            }
            if (intent.hasExtra("fps")) vm.edit { copy(targetFps = intent.getFloatExtra("fps", 18f)) }
            intent.getStringExtra("nur")?.split(',')?.map { it.trim() }?.toSet()?.let { k ->
                vm.edit {
                    copy(
                        lutFile = lutFile.takeIf { "lut" in k },
                        grain = grain.takeIf { "korn" in k }, dust = dust.takeIf { "staub" in k },
                        scratch = scratch.takeIf { "kratzer" in k }, leak = leak.takeIf { "leak" in k },
                        frame = frame.takeIf { "rahmen" in k },
                        halation = if ("halation" in k) halation else 0f, soften = if ("weich" in k) soften else 0f,
                        flicker = if ("flackern" in k) flicker else 0f, weave = if ("wackeln" in k) weave else 0f,
                    )
                }
            }
            // --es bild "hell,kontrast,schärfe,temp,schatten,lichter,sättigung,vignette" (je -1..1)
            intent.getStringExtra("bild")?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.let { v ->
                if (v.size == 8) vm.edit {
                    copy(brightness = v[0], contrast = v[1], sharpness = v[2], temperature = v[3], shadows = v[4],
                        highlights = v[5], saturation = v[6], vignette = v[7], off = off - com.volkskamera.app.render.Stage.BILD - com.volkskamera.app.render.Stage.VIGNETTE)
                }
            }
            // --es hintergrund "RAUSCHART,pegel,BRUMMART,frequenz,pegel" (Art "-" = aus)
            intent.getStringExtra("hintergrund")?.split(',')?.let { h ->
                vm.edit {
                    copy(bgNoiseOn = h[0] != "-", bgNoiseType = if (h[0] != "-") com.volkskamera.app.render.NoiseType.valueOf(h[0]) else bgNoiseType,
                        bgNoiseLevel = h[1].toFloat(), bgHumOn = h[2] != "-",
                        bgHumType = if (h[2] != "-") com.volkskamera.app.render.HumType.valueOf(h[2]) else bgHumType,
                        bgHumFreq = h[3].toFloat(), bgHumLevel = h[4].toFloat())
                }
            }
            // --es mikro "PROFIL,verzerrung,bandbreite,rauschen,automatik" (Werte leer = Vorgabe des Profils)
            intent.getStringExtra("mikro")?.split(',')?.let { m ->
                vm.edit {
                    withMic(com.volkskamera.app.render.MicProfile.valueOf(m[0])).let { b ->
                        b.copy(micDrive = m.getOrNull(1)?.toFloatOrNull() ?: b.micDrive,
                            micBandwidth = m.getOrNull(2)?.toFloatOrNull() ?: b.micBandwidth,
                            micNoise = m.getOrNull(3)?.toFloatOrNull() ?: b.micNoise,
                            micAgc = m.getOrNull(4)?.toFloatOrNull() ?: b.micAgc)
                    }
                }
            }
            // --es ton "STUMM|ORIGINAL|ALT"
            intent.getStringExtra("ton")?.let { t -> vm.edit { copy(audioMode = com.volkskamera.app.render.AudioMode.valueOf(t)) } }
            // --es knacken "stärke,häufigkeit"
            intent.getStringExtra("knacken")?.split(',')?.let { k ->
                vm.edit { copy(crackleOn = true, crackleAmount = k[0].toFloat(), crackleDensity = k[1].toFloat()) }
            }
            // --es projektor_test "MODELL,material,klang,tempo" (tempo 0 = an fps gebunden)
            intent.getStringExtra("projektor_test")?.split(',')?.let { p ->
                vm.edit {
                    copy(projector = true, projectorVolume = 0.8f,
                        projectorModel = com.volkskamera.app.render.ProjectorModel.valueOf(p[0]),
                        projectorMaterial = p[1].toFloat(), projectorTone = p[2].toFloat(),
                        projectorSyncFps = p[3].toFloat() <= 0f, projectorSpeed = p[3].toFloat().coerceAtLeast(6f))
                }
            }
            intent.getStringExtra("korn")?.let { id -> vm.edit { copy(grain = vm.catalog?.find(id)) } }
            if (intent.hasExtra("countdown")) vm.edit { copy(countdown = intent.getBooleanExtra("countdown", false)) }
            intent.getStringExtra("burn")?.let { id -> vm.edit { copy(burnEnd = vm.catalog?.find(id)) } }
            if (intent.hasExtra("projektor")) vm.edit { copy(projector = intent.getBooleanExtra("projektor", true)) }
            // --es amb "IDA,pegelA,IDB,pegelB[,kette]" ("-" = leer), --es proj_rec "p:…"
            intent.getStringExtra("amb")?.split(',')?.let { a ->
                vm.edit {
                    copy(ambA = a[0].takeIf { it != "-" }, ambALevel = a[1].toFloat(),
                        ambB = a[2].takeIf { it != "-" }, ambBLevel = a[3].toFloat(),
                        ambAChain = a.getOrNull(4) == "kette", ambBChain = a.getOrNull(4) == "kette")
                }
            }
            intent.getStringExtra("fx_taste")?.let { id -> vm.edit { copy(fxSound = id) } }
            intent.getStringExtra("proj_rec")?.let { id -> vm.edit { copy(projector = true, projectorRecording = id, projectorVolume = 0.6f) } }
            vm.pickSource(android.net.Uri.fromFile(file))
            // --es fx_cues "ID@ms;ID@ms"  --es lvA "ms:wert;ms:wert"  (wie beim Filmen mitgeschrieben)
            val fx = intent.getStringExtra("fx_cues")?.split(';')?.map {
                com.volkskamera.app.render.SoundCues.Cue(it.substringAfter('@').toLong(), it.substringBefore('@'))
            }.orEmpty()
            val lvA = intent.getStringExtra("lvA")?.split(';')?.map {
                com.volkskamera.app.render.SoundCues.Level(it.substringBefore(':').toLong(), it.substringAfter(':').toFloat())
            }.orEmpty()
            // --es datei_test "name.mp3" (liegt in files/): wie "Eigene Datei" importieren und bei 1 s als Effekt auslösen
            val own = intent.getStringExtra("datei_test")?.let { n ->
                val e = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.volkskamera.app.data.SoundLibrary.addCustom(this@MainActivity,
                        android.net.Uri.fromFile(java.io.File(filesDir, n)), com.volkskamera.app.data.SoundKind.EFFEKT)
                }
                android.util.Log.i("VolksKameraTest", "Eigene Datei $n -> ${e?.id} ${com.volkskamera.app.data.SoundLibrary.cached(e?.id)?.size} Samples")
                e?.let { com.volkskamera.app.render.SoundCues.Cue(1000, it.id) }
            }
            if (own != null) vm.takeCues = com.volkskamera.app.render.SoundCues(listOf(own))
            if (fx.isNotEmpty() || lvA.isNotEmpty()) vm.takeCues = com.volkskamera.app.render.SoundCues(fx, lvA)
            val t0 = System.currentTimeMillis()
            vm.startRender()
            while (vm.render is RenderState.Running) kotlinx.coroutines.delay(200)
            android.util.Log.i("VolksKameraTest", "Ergebnis ${vm.render} nach ${System.currentTimeMillis() - t0} ms")
            // Testdatei nicht in der Liste "Eigene Dateien" des Nutzers stehen lassen
            own?.let { com.volkskamera.app.data.SoundLibrary.removeCustom(this@MainActivity, it.id) }
        }
    }
}
