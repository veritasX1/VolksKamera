package com.volkskamera.app.ui

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.data.AssetCatalog
import com.volkskamera.app.render.FilmLook
import com.volkskamera.app.render.Presets
import com.volkskamera.app.render.RenderJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

sealed interface RenderState {
    data object Idle : RenderState
    data class Running(val percent: Int) : RenderState
    data class Done(val uri: Uri) : RenderState
    data class Failed(val message: String) : RenderState
}

/** Gemeinsamer Zustand für Kamera und Look-Einstellungen: ein Look, ein Entwickel-Vorgang. */
@UnstableApi
class FilmViewModel(app: Application) : AndroidViewModel(app) {
    var catalog by mutableStateOf<AssetCatalog?>(null); private set
    /** Video, das entwickelt wird (Aufnahme oder aus der Galerie). */
    var source by mutableStateOf<Uri?>(null); private set
    /** Standbild für die Look-Vorschau: letzte Aufnahme/gewähltes Video, sonst das Beispielbild. */
    var still by mutableStateOf<Bitmap?>(null); private set

    var look by mutableStateOf(FilmLook()); private set
    var preset by mutableStateOf<String?>(null); private set
    var pack by mutableStateOf<String?>(null); private set
    var render by mutableStateOf<RenderState>(RenderState.Idle); private set

    /** Zuletzt entwickelter Film (für das Vorschaubild unten in der Kamera). */
    var lastFilm by mutableStateOf<Uri?>(null); private set
    var lastFilmThumb by mutableStateOf<Bitmap?>(null); private set

    private var renderJob: Job? = null

    /** LUT-Infos (Farbmatrix für den Sucher, Schwarzweiß-Erkennung) zur aktuellen LUT. */
    var lutInfo by mutableStateOf<com.volkskamera.app.render.LutMatrix.Info?>(null); private set
    val isMonochrome get() = look.mono || (lutInfo?.mono == true && look.isOn(com.volkskamera.app.render.Stage.LUT))
    /** Farbfilter für den Sucher, null = reines Kamerabild. */
    val viewfinderMatrix: FloatArray?
        get() = if (!look.finderLook) null
        else com.volkskamera.app.render.LutMatrix.viewfinderMatrix(
            lutInfo.takeIf { look.lutFile != null && look.isOn(com.volkskamera.app.render.Stage.LUT) }, look.lutMix, look.mono,
            look.effective())

    private fun refreshLutInfo() {
        val f = look.lutFile
        viewModelScope.launch {
            lutInfo = f?.let { withContext(Dispatchers.Default) { com.volkskamera.app.render.LutMatrix.info(getApplication(), it) } }
        }
    }

    init {
        viewModelScope.launch {
            val c = withContext(Dispatchers.IO) { AssetCatalog.load(getApplication()) }
            // gemerkten Look laden (beim allerersten Start Kodachrome) – ERST danach den Katalog
            // freigeben, sonst gilt kurz der Standard-Look (sichtbar z.B. als falsches Format)
            val stored = withContext(Dispatchers.IO) { com.volkskamera.app.data.LookStore.load(getApplication(), c) }
            userPresets = withContext(Dispatchers.IO) { com.volkskamera.app.data.LookStore.loadPresets(getApplication(), c) }
            if (stored != null) {
                look = stored.first
                preset = stored.second
                pack = c.luts.firstOrNull { it.file == look.lutFile }?.pack ?: c.lutPacks.firstOrNull()
                refreshLutInfo()
            } else {
                applyPreset("Kodachrome", c)
            }
            catalog = c
            if (still == null) still = withContext(Dispatchers.IO) { sampleStill() }
            withContext(Dispatchers.IO) { latestFilm() }?.let { setLastFilm(it) }
        }
    }

    // ---------- Look ----------

    // ---------- eigene Presets ----------

    var userPresets by mutableStateOf(listOf<com.volkskamera.app.data.UserPreset>()); private set
    val isUserPreset get() = preset != null && userPresets.any { it.name == preset }

    // ---------- Gehäuse-Design (Leder/Metall/Auslöser) ----------
    var housing by mutableStateOf(com.volkskamera.app.ui.theme.Housing.load(getApplication())); private set
    fun updateHousing(h: com.volkskamera.app.ui.theme.Housing) {
        housing = h
        com.volkskamera.app.ui.theme.Housing.save(getApplication(), h)
    }

    // ---------- Film-Auswahl (Katalog) ----------
    /** Gewähltes Filmmaterial aus dem Katalog (id). Der Look/die LUT dazu kommt später. */
    var selectedFilmId by mutableStateOf<String?>(null); private set
    var selectedFilmLabel by mutableStateOf<String?>(null); private set

    /** Zuletzt gewählter Film (für „Zurücksetzen auf Original"). */
    var selectedFilm by mutableStateOf<com.volkskamera.app.data.FilmStock?>(null); private set

    /** Ein Filmmaterial wählen: gebackene LUT + Korn (nach Familie) anwenden. */
    fun selectFilm(f: com.volkskamera.app.data.FilmStock) {
        selectedFilm = f
        selectedFilmId = f.id
        selectedFilmLabel = "${f.hersteller} ${f.variantLabel} (${f.jahr ?: "?"})"
        activeCustom = null
        preset = f.variantLabel
        applyFilmLook(f)
        cameraPrefs().edit().putString("film", f.id).remove("eigene_lut").apply()
        persist()
    }

    private fun cameraPrefs() =
        getApplication<Application>().getSharedPreferences("camera_prefs", android.content.Context.MODE_PRIVATE)

    /** Navigationszustand der Filmauswahl (null = beim Film in Verwendung beginnen). */
    var pickerNav by mutableStateOf<PickerNav?>(null)

    // ---------- eigene LUTs ----------
    var customLuts by mutableStateOf(com.volkskamera.app.data.CustomLuts.load(getApplication())); private set
    /** Gewählte eigene LUT (statt der Original-LUT des Films); null = Film-Original. */
    var activeCustom by mutableStateOf<com.volkskamera.app.data.CustomLut?>(null); private set

    fun selectCustomLut(l: com.volkskamera.app.data.CustomLut, catalog: com.volkskamera.app.data.FilmCatalog) {
        val base = l.baseFilmId?.let { catalog.byId(it) }
        selectedFilm = base
        selectedFilmId = base?.id
        selectedFilmLabel = l.name
        activeCustom = l
        preset = l.name
        if (base != null) applyFilmLook(base, l.file)
        else { look = FilmLook(lutFile = l.file, finderLook = look.finderLook).withAudioFrom(look); refreshLutInfo() }
        cameraPrefs().edit().putString("eigene_lut", l.id).putString("film", base?.id).apply()
        persist()
    }

    /** Bearbeitung backen und als eigene LUT speichern ([id] = überschreiben) und gleich verwenden. */
    fun saveCustomLut(id: String?, name: String, baseFilmId: String?, baseLut: String,
                      edit: com.volkskamera.app.render.LutEdit, catalog: com.volkskamera.app.data.FilmCatalog) {
        viewModelScope.launch {
            val (list, lut) = withContext(Dispatchers.IO) {
                com.volkskamera.app.data.CustomLuts.save(getApplication(), customLuts, id, name.trim().ifEmpty { "Meine LUT" },
                    baseFilmId, baseLut, edit)
            }
            customLuts = list
            selectCustomLut(lut, catalog)
        }
    }

    fun deleteCustomLut(id: String) {
        customLuts = com.volkskamera.app.data.CustomLuts.delete(getApplication(), customLuts, id)
        if (activeCustom?.id == id) {
            activeCustom = null
            cameraPrefs().edit().remove("eigene_lut").apply()
            selectedFilm?.let { applyFilmLook(it); persist() }
        }
    }

    // ---------- Referenzbild (Beispielbild der Filme, Vorschau im LUT-Editor) ----------
    private val referenceFile get() = File(getApplication<Application>().filesDir, "referenz.jpg")
    var reference by mutableStateOf<Bitmap?>(null); private set
    /** zählt hoch, wenn sich das Referenzbild ändert (Beispielbilder neu rechnen) */
    var referenceVersion by mutableStateOf(0); private set
    val hasOwnReference get() = referenceFile.isFile

    /** Mitgelieferte Beispielbilder (Asset, Anzeigename); "eigen" = aus der Galerie. */
    val builtinReferences = listOf(
        "terrasse" to "Terrasse",
        "wiese" to "Fröruper Berge",
        "rinder" to "Fröruper Berge, Rinder",
    )
    var referenceChoice by mutableStateOf(cameraPrefs().getString("referenz", "terrasse") ?: "terrasse"); private set

    fun chooseReference(id: String) {
        referenceChoice = id
        cameraPrefs().edit().putString("referenz", id).apply()
        loadReference()
    }

    private fun loadReference() {
        val choice = referenceChoice
        viewModelScope.launch {
            reference = withContext(Dispatchers.IO) {
                runCatching {
                    if (choice == "eigen" && referenceFile.isFile) BitmapFactory.decodeFile(referenceFile.path)
                    else getApplication<Application>().assets.open("referenz_${choice.takeIf { c -> builtinReferences.any { it.first == c } } ?: "terrasse"}.jpg")
                        .use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            referenceVersion++
        }
    }

    /** Eigenes Referenzbild aus der Galerie übernehmen (verkleinert kopiert). */
    fun setReference(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val cr = getApplication<Application>().contentResolver
                    val src = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(cr, uri)) { d, info, _ ->
                        val s = 1280f / maxOf(info.size.width, info.size.height)
                        if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    referenceFile.outputStream().use { src.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                }
            }
            chooseReference("eigen")
        }
    }

    fun resetReference() {
        referenceFile.delete()
        chooseReference("terrasse")
    }

    init { loadReference() }

    /** Nach Neustart den zuletzt gewählten Film aus dem Katalog wiederherstellen (nur Label/Referenz). */
    fun restoreSelectedFilm(catalog: com.volkskamera.app.data.FilmCatalog) {
        if (selectedFilm != null) return
        val id = getApplication<Application>().getSharedPreferences("camera_prefs", android.content.Context.MODE_PRIVATE)
            .getString("film", null) ?: return
        catalog.byId(id)?.let { f ->
            selectedFilm = f; selectedFilmId = f.id
            selectedFilmLabel = "${f.hersteller} ${f.variantLabel} (${f.jahr ?: "?"})"
        }
        cameraPrefs().getString("eigene_lut", null)?.let { cid ->
            customLuts.firstOrNull { it.id == cid }?.let { activeCustom = it; selectedFilmLabel = it.name }
        }
    }

    /** Den (unveränderten) Original-Look des gewählten Films wiederherstellen. */
    fun resetSelectedFilm() {
        activeCustom = null
        cameraPrefs().edit().remove("eigene_lut").apply()
        selectedFilm?.let { applyFilmLook(it); persist() }
    }

    // ---------- Farbfilter für Schwarzweißfilm ----------
    var bwFilter by mutableStateOf(com.volkskamera.app.render.BwFilter.byName(cameraPrefs().getString("bw_filter", null))); private set

    fun chooseBwFilter(f: com.volkskamera.app.render.BwFilter) {
        bwFilter = f
        cameraPrefs().edit().putString("bw_filter", f.name).apply()
        val film = selectedFilm ?: return
        applyFilmLook(film, activeCustom?.file)
        persist()
    }

    /** LUT eines Films, wie sie gerechnet wird (bei S/W mit dem gewählten Farbfilter). */
    fun effectiveLut(f: com.volkskamera.app.data.FilmStock, base: String? = null,
                     filter: com.volkskamera.app.render.BwFilter = bwFilter): String? {
        val b = base ?: f.look?.lut ?: return null
        return if (f.farbeSw == "S/W") com.volkskamera.app.render.BwFilter.lutWith(getApplication(), b, filter) else b
    }

    private fun applyFilmLook(f: com.volkskamera.app.data.FilmStock, lutOverride: String? = null) {
        val c = catalog ?: return
        val lk = f.look ?: return
        val (grainId, grainAmt) = when (lk.korn) {
            "fein" -> "filmgrain_4kdci_35mm_24fps" to 0.35f
            "kräftig" -> "filmgrain_4kdci_8mm_24fps" to 0.55f
            "grob" -> "filmgrain_4kdci_8mm_24fps_heavy" to 0.7f
            else -> "filmgrain_4kdci_16mm_24fps" to 0.45f
        }
        // Ton, Mikrofon, Bildrate und Format bleiben beim Filmwechsel erhalten
        look = FilmLook(
            lutFile = effectiveLut(f, lutOverride ?: lk.lut), lutMix = 1f,
            grain = c.find(grainId), grainAmount = grainAmt,
            halation = if (lk.halation) 0.5f else 0f,
            finderLook = look.finderLook,
        ).withAudioFrom(look)
        refreshLutInfo()
    }

    /** Aktuelle Einstellungen unter [name] speichern (gleicher Name = überschreiben). */
    fun saveUserPreset(name: String) {
        var n = name.trim().ifEmpty { "Mein Look" }
        if (n in Presets.names) n = "$n (eigen)"
        userPresets = userPresets.filterNot { it.name == n } + com.volkskamera.app.data.UserPreset(n, look)
        preset = n
        storePresets()
        persist()
    }

    fun deleteUserPreset(name: String) {
        userPresets = userPresets.filterNot { it.name == name }
        if (preset == name) preset = null
        storePresets()
        persist()
    }

    /** Text für den Export (.txt): das gewählte Preset bzw. die aktuellen Einstellungen. */
    fun exportText(): Pair<String, String> {
        val name = preset ?: "Mein Look"
        return name to com.volkskamera.app.data.LookStore.toText(name, look)
    }

    /** Importierte Textdatei als eigenes Preset übernehmen und anwenden. false = kein gültiges Preset. */
    fun importText(text: String): Boolean {
        val c = catalog ?: return false
        val p = com.volkskamera.app.data.LookStore.fromText(text, c) ?: return false
        look = p.look
        refreshLutInfo()
        saveUserPreset(p.name)
        return true
    }

    private fun storePresets() {
        if (!persistenceEnabled) return
        val list = userPresets
        viewModelScope.launch(Dispatchers.IO) { com.volkskamera.app.data.LookStore.savePresets(getApplication(), list) }
    }

    fun applyPreset(name: String, c: AssetCatalog? = catalog) {
        c ?: return
        userPresets.firstOrNull { it.name == name }?.let { up ->
            look = up.look
            preset = name
            pack = c.luts.firstOrNull { it.file == look.lutFile }?.pack ?: c.lutPacks.firstOrNull()
            refreshLutInfo()
            persist()
            return
        }
        look = Presets.build(name, c, look)
        preset = name
        refreshLutInfo()
        persist()
        pack = c.luts.firstOrNull { it.file == look.lutFile }?.pack ?: c.lutPacks.firstOrNull()
    }

    // ---------- Tonprobe für die Mikrofon-Vorschau ----------

    /** Kurze eigene Sprachaufnahme (48 kHz mono), nur im Speicher, nie gespeichert. */
    var probe by mutableStateOf<ShortArray?>(null); private set
    /** Länge der Hörproben in Sekunden (3, 5 oder 8) */
    var probeSeconds by mutableStateOf(
        getApplication<Application>().getSharedPreferences("camera_prefs", android.content.Context.MODE_PRIVATE).getInt("probe_s", 3)); private set
    fun chooseProbeSeconds(s: Int) {
        probeSeconds = s
        getApplication<Application>().getSharedPreferences("camera_prefs", android.content.Context.MODE_PRIVATE).edit().putInt("probe_s", s).apply()
    }
    var probeRecording by mutableStateOf(false); private set

    @android.annotation.SuppressLint("MissingPermission")
    fun recordProbe(seconds: Int = 6) {
        if (probeRecording) return
        val ok = androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!ok) return
        probeRecording = true
        viewModelScope.launch(Dispatchers.IO) {
            val sr = 48000
            val buf = ShortArray(sr * seconds)
            runCatching {
                val min = android.media.AudioRecord.getMinBufferSize(sr, android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT)
                val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, sr,
                    android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, sr))
                rec.startRecording()
                var got = 0
                while (got < buf.size) {
                    val r = rec.read(buf, got, minOf(4800, buf.size - got))
                    if (r <= 0) break
                    got += r
                }
                rec.stop(); rec.release()
                if (got > sr) withContext(Dispatchers.Main) { probe = buf.copyOf(got) }
            }
            withContext(Dispatchers.Main) { probeRecording = false }
        }
    }

    fun edit(change: FilmLook.() -> FilmLook) {
        val before = look.lutFile
        look = look.change()
        preset = null
        if (look.lutFile != before) refreshLutInfo()
        persist()
    }

    /** Ändern ohne das gewählte Preset zu verlassen (Kamera-Regler für Ambient). */
    fun editQuiet(change: FilmLook.() -> FilmLook) {
        look = look.change()
        persist()
    }

    private var saveJob: Job? = null
    /** Debug-Testläufe (MainActivity.debugRenderTest) sollen die echten Einstellungen nicht überschreiben. */
    var persistenceEnabled = true

    /** Einstellungen merken – kurz gebündelt, damit Schieberegler nicht bei jedem Pixel schreiben. */
    private fun persist() {
        if (!persistenceEnabled) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            val l = look
            val p = preset
            withContext(Dispatchers.IO) { com.volkskamera.app.data.LookStore.save(getApplication(), l, p) }
        }
    }

    fun choosePack(p: String) { pack = p }

    // ---------- Quelle & Entwickeln ----------

    fun pickSource(uri: Uri) {
        source = uri
        takeParts = emptyList()
        takeCues = null
        render = RenderState.Idle
        viewModelScope.launch { frameOf(uri)?.let { still = it } }
    }

    /** Stücke der letzten Aufnahme (mit Pausen); leer = einzelne Quelle [source]. */
    private var takeParts: List<Uri> = emptyList()

    /** Mitschrift der letzten Aufnahme (Effekt-Taste, Ambient-Regler); bleibt beim Neu-Entwickeln erhalten. */
    var takeCues: com.volkskamera.app.render.SoundCues? = null

    fun startRender() {
        val parts = takeParts.ifEmpty { listOfNotNull(source) }
        if (parts.isEmpty()) return
        val l = look
        render = RenderState.Running(0)
        renderJob = viewModelScope.launch {
            render = try {
                val uri = RenderJob(getApplication()).render(parts, l, takeCues) { render = RenderState.Running(it) }
                setLastFilm(uri)
                RenderState.Done(uri)
            } catch (e: Exception) {
                RenderState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancelRender() {
        renderJob?.cancel()
        render = RenderState.Idle
    }

    // ---------- Mini-Editor (Filme aneinanderreihen) ----------

    var editClips by mutableStateOf(listOf<com.volkskamera.app.data.FilmClip>()); private set
    var editIntro by mutableStateOf(RenderJob.Intro.KEIN)
    var editOutro by mutableStateOf(RenderJob.Outro.KEIN)
    var editFadeSec by mutableStateOf(1.5f)
    var editBurn by mutableStateOf<com.volkskamera.app.data.SequenceAsset?>(null)
    var montage by mutableStateOf<RenderState>(RenderState.Idle); private set

    fun editAdd(c: com.volkskamera.app.data.FilmClip) { editClips = editClips + c }
    fun editRemove(i: Int) { editClips = editClips.filterIndexed { j, _ -> j != i } }
    fun editMove(i: Int, d: Int) {
        val j = i + d
        if (j !in editClips.indices) return
        editClips = editClips.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }
    fun editClear() { editClips = emptyList(); montage = RenderState.Idle }

    fun startMontage() {
        if (editClips.isEmpty() || montage is RenderState.Running || render is RenderState.Running) return
        val clips = editClips.map { it.uri }
        val burn = editBurn ?: catalog?.burns?.firstOrNull()
        montage = RenderState.Running(0)
        viewModelScope.launch {
            montage = try {
                val uri = RenderJob(getApplication()).renderMontage(clips, editIntro, editOutro, editFadeSec, burn,
                    // Countdown im Look, aber im Format der Clips (nicht dem Kameraformat)
                    look.copy(leak = null, burnEnd = null, aspect = null)) { montage = RenderState.Running(it) }
                setLastFilm(uri)
                RenderState.Done(uri)
            } catch (e: Exception) {
                RenderState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // ---------- Aufnahme ----------

    /** Rohaufnahmen liegen app-privat (nicht in der Galerie); in die Galerie kommt nur der fertige Film. */
    fun newRawFile(): File {
        val dir = File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES), "roh").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(java.util.Date())
        return File(dir, "roh_$ts.mp4")
    }

    /** Nach der Aufnahme: alle Stücke (Pausen) zusammen mit dem eingestellten Look entwickeln. */
    fun onRecorded(parts: List<File>, cues: com.volkskamera.app.render.SoundCues? = null) {
        if (parts.isEmpty()) return
        pickSource(Uri.fromFile(parts.first()))
        takeParts = parts.map { Uri.fromFile(it) }
        takeCues = cues?.takeIf { !it.isEmpty }
        startRender()
        pruneRaw()
    }

    /** Nur die letzten 30 Rohstücke behalten (zum Neu-Entwickeln mit anderem Look). */
    private fun pruneRaw() {
        val dir = File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES), "roh")
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(30)?.forEach { it.delete() }
    }

    // ---------- Bilder ----------

    /** Nach dem Löschen: Vorschaubild auf den dann neuesten Film setzen (oder leeren). */
    fun refreshLastFilm() {
        viewModelScope.launch {
            val u = withContext(Dispatchers.IO) { latestFilm() }
            if (u != null) setLastFilm(u) else { lastFilm = null; lastFilmThumb = null }
        }
    }

    private suspend fun setLastFilm(uri: Uri) {
        lastFilm = uri
        lastFilmThumb = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) getApplication<Application>().contentResolver.loadThumbnail(uri, Size(256, 256), null)
                else frameAt(uri, 256)
            }.getOrNull()
        }
    }

    private fun latestFilm(): Uri? {
        val cr = getApplication<Application>().contentResolver
        val sel = if (Build.VERSION.SDK_INT >= 29) "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" else null
        val args = if (Build.VERSION.SDK_INT >= 29) arrayOf("${Environment.DIRECTORY_MOVIES}/VolksKamera%") else null
        cr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID), sel, args,
            "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
        }
        return null
    }

    private fun sampleStill(): Bitmap? = runCatching {
        getApplication<Application>().assets.open("beispiel/garten.jpg").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private suspend fun frameOf(uri: Uri): Bitmap? = withContext(Dispatchers.IO) { frameAt(uri, 960) }

    /** Standbild aus der Mitte des Videos (schon richtig gedreht). */
    private fun frameAt(uri: Uri, longSide: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(getApplication(), uri)
            val durUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            val frame = r.getFrameAtTime(durUs / 2, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val s = longSide / maxOf(frame.width, frame.height).toFloat()
            Bitmap.createScaledBitmap(frame, (frame.width * s).toInt(), (frame.height * s).toInt(), true)
                .also { if (it != frame) frame.recycle() }
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }
}
