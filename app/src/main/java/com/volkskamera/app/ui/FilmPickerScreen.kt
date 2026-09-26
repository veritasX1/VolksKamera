package com.volkskamera.app.ui

import com.volkskamera.app.t
import com.volkskamera.app.tf
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import com.volkskamera.app.R
import com.volkskamera.app.data.CustomLut
import com.volkskamera.app.render.BwFilter
import com.volkskamera.app.data.FilmCatalog
import com.volkskamera.app.data.FilmStock
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite

/**
 * Film-Auswahl in den Einstellungen: Hersteller -> Breite (8/16/35 mm) -> Variante.
 * Jede Variante zeigt das Jahr der Ersterscheinung. Auswahl ruft [onSelect].
 * [onEdit] öffnet den (späteren) LUT-Editor für den gewählten Film.
 */
/** Wo man in der Filmauswahl gerade ist (Hersteller → Breite → aufgeklappter Film). */
data class PickerNav(val hersteller: String?, val breite: Int?, val expandedId: String?, val eigene: Boolean)

@androidx.media3.common.util.UnstableApi
@Composable
fun FilmPickerScreen(
    vm: FilmViewModel,
    catalog: FilmCatalog,
    onSelect: (FilmStock) -> Unit,
    onEdit: (FilmStock) -> Unit,
    onSelectCustom: (CustomLut) -> Unit,
    onEditCustom: (CustomLut) -> Unit,
    onHousing: () -> Unit,
    onMic: () -> Unit,
    onHelp: () -> Unit,
    onRecording: () -> Unit,
    onCombos: () -> Unit,
    onFilter: (FilmStock) -> Unit,
    onBack: () -> Unit,
) {
    // Navigationszustand liegt im ViewModel: bleibt beim Ausflug in Filter, Editor, Mikrofon,
    // Gehäuse erhalten. Von der Kamera aus geöffnet (pickerNav = null) → beim Film in Verwendung.
    val selectedId = vm.selectedFilmId.takeIf { vm.activeCustom == null }
    if (vm.pickerNav == null) {
        val cur = vm.selectedFilm.takeIf { vm.activeCustom == null }
        vm.pickerNav = PickerNav(cur?.hersteller, cur?.breite, selectedId, eigene = false)
    }
    val nav = vm.pickerNav!!
    val hersteller = nav.hersteller
    val breite = nav.breite
    val eigene = nav.eigene
    val expandedId = nav.expandedId
    fun go(n: PickerNav) { vm.pickerNav = n }
    val pickRef = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setReference(it) }
    }

    fun up() {
        when {
            eigene -> go(nav.copy(eigene = false))
            breite != null -> go(nav.copy(breite = null, expandedId = null))
            hersteller != null -> go(nav.copy(hersteller = null))
            else -> onBack()
        }
    }
    androidx.activity.compose.BackHandler { up() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(16.dp)) {
        // Kopf: Zurück + Brotkrume, daneben (quer) bzw. darunter (hoch) Hilfe und die Einstellungs-Knöpfe.
        // Hoch in EINER Zeile blieb für die Brotkrume fast keine Breite → sie brach Buchstabe für Buchstabe um.
        val crumb = (if (eigene) listOf(t("Film"), t("Eigene LUTs")) else listOfNotNull(t("Film"), hersteller, breite?.let { "$it mm" }))
            .joinToString("  ›  ")
        val buttons: @Composable RowScope.() -> Unit = {
            // Hilfe: Kreis mit Fragezeichen
            Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, FilmAccent, androidx.compose.foundation.shape.CircleShape)
                .clickable { onHelp() }, contentAlignment = Alignment.Center) {
                Text("?", color = FilmAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Chip(t("Aufnahme")) { onRecording() }
            Spacer(Modifier.width(6.dp))
            Chip(t("Mikrofon")) { onMic() }
            Spacer(Modifier.width(6.dp))
            Chip(t("Gehäuse")) { onHousing() }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val schmal = maxWidth < 600.dp
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(t("‹ Zurück")) { up() }
                    Spacer(Modifier.width(12.dp))
                    Text(crumb, color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!schmal) buttons()
                }
                if (schmal) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, content = buttons)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Zustand einmal lesen: die Listen-Lambdas laufen evtl. erst nach dem Zurückgehen erneut
        val h = hersteller
        val br = breite
        when {
            eigene -> {
                Text(t("Eigene LUTs"), color = FilmAccent, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                if (vm.customLuts.isEmpty()) Text(
                    t("Noch keine eigenen LUTs. Wähle einen Film, tippe „Bearbeiten“ und speichere deine Fassung."),
                    color = FilmWhite.copy(alpha = 0.6f), fontSize = 13.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.customLuts.reversed(), key = { it.id }) { l ->
                        CustomRow(vm, l, catalog.byId(l.baseFilmId ?: "")?.fullLabel,
                            selected = vm.activeCustom?.id == l.id,
                            onSelect = { onSelectCustom(l) }, onEdit = { onEditCustom(l) },
                            onDelete = { vm.deleteCustomLut(l.id) })
                    }
                }
            }
            h == null -> {
                // Referenzbild für die Beispielbilder
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text(t("Beispielbild:"), color = FilmWhite.copy(alpha = 0.6f), fontSize = 12.sp)
                    vm.builtinReferences.forEach { (id, name) ->
                        Spacer(Modifier.width(6.dp))
                        Pill(t(name), selected = vm.referenceChoice == id, small = true) { vm.chooseReference(id) }
                    }
                    Spacer(Modifier.width(6.dp))
                    Pill(if (vm.hasOwnReference) t("Eigenes") else t("Eigenes wählen …"), selected = vm.referenceChoice == "eigen", small = true) {
                        if (vm.hasOwnReference && vm.referenceChoice != "eigen") vm.chooseReference("eigen")
                        else pickRef.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (vm.hasOwnReference) {
                        Spacer(Modifier.width(6.dp))
                        Pill(t("Neues wählen …"), selected = false, small = true) {
                            pickRef.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                }
                // Sprache der App (Namen in der jeweiligen Sprache)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text(t("Sprache:"), color = FilmWhite.copy(alpha = 0.6f), fontSize = 12.sp)
                    val act = androidx.compose.ui.platform.LocalContext.current
                    com.volkskamera.app.I18n.Lang.entries.forEach { l ->
                        Spacer(Modifier.width(6.dp))
                        Pill(l.label, selected = com.volkskamera.app.I18n.lang == l, small = true) {
                            if (com.volkskamera.app.I18n.lang != l) {
                                com.volkskamera.app.I18n.choose(act, l)
                                (act as? android.app.Activity)?.recreate()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(t("Hersteller"), color = FilmAccent, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        RowItem(t("★  Kombinationen"), "${vm.combos.size}") { onCombos() }
                    }
                    item {
                        RowItem(t("★  Eigene LUTs"), "${vm.customLuts.size}") { go(nav.copy(eigene = true)) }
                    }
                    items(catalog.hersteller) { h ->
                        val n = catalog.films.count { it.hersteller == h }
                        RowItem(h, if (n == 1) t("1 Film") else tf("%d Filme", n), logoRes = herstellerLogo(h)) { go(nav.copy(hersteller = h)) }
                    }
                }
            }
            br == null -> {
                Text(t("Breite"), color = FilmAccent, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catalog.breitenVon(h)) { b ->
                        val n = catalog.variantenVon(h, b).size
                        RowItem(if (b > 0) "$b mm" else t("unbekannt"), if (n == 1) t("1 Film") else tf("%d Filme", n)) { go(nav.copy(breite = b)) }
                    }
                }
            }
            else -> {
                val list = catalog.variantenVon(h, br)
                Text(t("Variante"), color = FilmAccent, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { f -> FilmRow(vm, f, inUse = f.id == selectedId, expanded = f.id == expandedId,
                        onToggle = { go(nav.copy(expandedId = if (expandedId == f.id) null else f.id)) },
                        onUse = { onSelect(f) }, onEdit = { onEdit(f) }, onFilter = { onFilter(f) }) }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp)
    ) { Text(label, color = FilmWhite, fontSize = 14.sp, maxLines = 1, softWrap = false) }
}

/** Hersteller → Logo-Ressource (einheitlich freigestellt); null = kein Logo. */
private fun herstellerLogo(name: String): Int? = when {
    name == "3M" -> R.drawable.logo_3m
    name.startsWith("Adox") -> R.drawable.logo_adox
    name.startsWith("Agfa") -> R.drawable.logo_agfa
    name == "Gevaert" -> R.drawable.logo_gevaert
    name == "CineStill" -> R.drawable.logo_cinestill
    name == "DuPont" -> R.drawable.logo_dupont
    name.contains("Ferrania") -> R.drawable.logo_ferrania
    name == "Foma" -> R.drawable.logo_foma
    name == "Fujifilm" -> R.drawable.logo_fujifilm
    name.startsWith("Harman") -> R.drawable.logo_harman
    name == "Ilford" -> R.drawable.logo_ilford
    name == "Konica" -> R.drawable.logo_konica
    name.contains("Kodak") -> R.drawable.logo_kodak
    name == "Lomography" -> R.drawable.logo_lomography
    name == "Polaroid" -> R.drawable.logo_polaroid
    name.startsWith("Rollei") -> R.drawable.logo_rollei
    else -> null
}

@Composable
private fun RowItem(title: String, sub: String, logoRes: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = FilmWhite, fontSize = 16.sp, modifier = Modifier.weight(1f))
        // einheitlicher heller Chip mit dem Hersteller-Logo (gleiche Höhe für alle)
        logoRes?.let {
            Box(
                Modifier.height(30.dp).widthIn(max = 104.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xFFF2ECE2))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(it), contentDescription = title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight())
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(sub, color = FilmWhite.copy(alpha = 0.5f), fontSize = 13.sp)
        Text("  ›", color = FilmWhite.copy(alpha = 0.5f), fontSize = 16.sp)
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun FilmRow(vm: FilmViewModel, f: FilmStock, inUse: Boolean, expanded: Boolean, onToggle: () -> Unit,
                    onUse: () -> Unit, onEdit: () -> Unit, onFilter: () -> Unit) {
    val selected = expanded
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) FilmAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
            .then(if (inUse) Modifier.border(2.dp, FilmAccent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            f.look?.lut?.let { LutThumb(vm, it, Modifier.padding(end = 12.dp)) }
            Text(t(f.variantLabel), color = FilmWhite, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f).padding(top = 2.dp))
            // Rechts: Jahr oben, darunter das Kamera-Typ-Icon (nach Breite), golden getönt
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                f.jahr?.let { Text("$it", color = FilmAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                val camIcon = when (f.breite) {
                    8 -> R.drawable.cam_8mm
                    16 -> R.drawable.cam_16mm
                    35 -> R.drawable.cam_35mm
                    else -> null
                }
                camIcon?.let {
                    Spacer(Modifier.height(4.dp))
                    Image(painterResource(it), contentDescription = "${f.breite} mm",
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(FilmAccent),
                        modifier = Modifier.size(38.dp))
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        val info = buildString {
            append(t(f.farbeSw)); append(" · "); append(t(f.negativUmkehr))
            f.iso?.let { append(t(" · ISO ")); append(it) }
        }
        Text(info, color = FilmWhite.copy(alpha = 0.6f), fontSize = 12.sp)
        if (f.name != f.variantLabel && f.variante.isNotBlank())
            Text(t(f.name), color = FilmWhite.copy(alpha = 0.45f), fontSize = 11.sp)
        // Gewählter Film: großes Beispielbild (+ Original zum Vergleich), Charakteristik, Beschreibung
        if (selected) f.look?.let { lk ->
            var showOriginal by remember { mutableStateOf(false) }
            val image: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    Row(verticalAlignment = Alignment.Top) {
                        val lut = vm.effectiveLut(f) ?: lk.lut
                        Box(Modifier.weight(1f)) {
                            if (showOriginal) ReferenceImage(vm, Modifier.fillMaxWidth())
                            else LutThumb(vm, lut, Modifier.fillMaxWidth(), big = true)
                            Text(if (showOriginal) t("Original") else t(f.variantLabel) + if (vm.bwFilter != BwFilter.KEINER && f.farbeSw == "S/W") " · ${vm.bwFilter.label}" else "",
                                color = FilmWhite, fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        // kleiner Vergleichsknopf neben dem Bild
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (showOriginal) FilmAccent else Color.White.copy(alpha = 0.15f))
                                    .clickable { showOriginal = !showOriginal },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("◐", color = if (showOriginal) Color.Black else FilmWhite, fontSize = 20.sp)
                            }
                            Text(if (showOriginal) t("Film") else t("Original"), color = FilmWhite.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                }
            }
            val info: @Composable () -> Unit = {
                Column {
                    // Charakteristik
                    Text(t("Charakteristik"), color = FilmAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    CharLine(t("Material"), listOf(t(f.farbeSw), t(f.negativUmkehr)).joinToString(" · ") +
                        (f.materialtyp.takeIf { it.isNotBlank() && it != "${f.farbeSw} ${f.negativUmkehr}" }?.let { " (${t(it)})" } ?: ""))
                    CharLine(t("Empfindlichkeit"), f.iso?.let { "ISO $it / $it ASA" } ?: t(f.isoRoh.ifBlank { t("ohne Normangabe") }))
                    CharLine(t("Breite"), f.breiteLabel)
                    f.jahr?.let { CharLine(t("Erschienen"), "$it") }
                    CharLine(t("Korn"), t(lk.korn.replaceFirstChar { it.uppercase() }))
                    CharLine(t("Filmfamilie"), familyName(lk.familie))
                    if (lk.halation) CharLine(t("Lichthof"), t("ja – leuchtende Säume um helle Lichter"))
                    if (f.anmerkung.isNotBlank()) CharLine(t("Anmerkung"), t(f.anmerkung))
                    Spacer(Modifier.height(6.dp))
                    Text(t(lk.beschreibung), color = FilmWhite.copy(alpha = 0.85f), fontSize = 12.sp)
                    if (lk.quelle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(tf("Quelle: %s", t(lk.quelle)), color = FilmWhite.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // quer: Bild und Steckbrief nebeneinander, hoch: untereinander
            BoxWithConstraints {
                if (maxWidth > 600.dp) Row {
                    image(Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f)) { info() }
                } else Column {
                    image(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    info()
                }
            }
        }
        if (inUse) Text(t("✓ In Verwendung"), color = FilmAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp))
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!inUse) Pill(t("Verwenden"), selected = true) { onUse() }
                Chip(t("Bearbeiten")) { onEdit() }
                if (f.farbeSw == "S/W") Chip(tf("Farbfilter: %s ›", vm.bwFilter.label)) { onFilter() }
            }
        }
    }
}

@Composable
private fun CharLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(k, color = FilmWhite.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.width(110.dp))
        Text(v, color = FilmWhite.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}

/** Das Referenzbild ohne Film (Vergleich). */
@androidx.media3.common.util.UnstableApi
@Composable
private fun ReferenceImage(vm: FilmViewModel, modifier: Modifier) {
    val ref = vm.reference ?: return
    Image(ref.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
}

/** Beispielbild: das Referenzbild mit der LUT, klein und zwischengespeichert. */
@androidx.media3.common.util.UnstableApi
@Composable
internal fun LutThumb(vm: FilmViewModel, lut: String, modifier: Modifier = Modifier, big: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ref = vm.reference
    val key = "$lut@${vm.referenceVersion}@$big"
    val bmp by produceState(ThumbCache.get(key), key, ref) {
        if (value == null && ref != null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                com.volkskamera.app.render.LutFile.apply(ThumbCache.small(ref, if (big) 720 else 240), com.volkskamera.app.render.LutFile.load(context, lut))
            }.getOrNull()?.also { ThumbCache.put(key, it) }
        }
    }
    val m = if (big) modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
        else modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(6.dp))
    Box(m.background(Color.Black)) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()) }
    }
}

private object ThumbCache {
    private val cache = android.util.LruCache<String, android.graphics.Bitmap>(120)
    private val scaled = HashMap<Int, Pair<android.graphics.Bitmap, android.graphics.Bitmap>>()
    fun get(k: String): android.graphics.Bitmap? = cache.get(k)
    fun put(k: String, b: android.graphics.Bitmap) { cache.put(k, b) }
    /** Referenzbild verkleinert auf [side] Pixel lange Seite (je Größe einmal). */
    @Synchronized fun small(ref: android.graphics.Bitmap, side: Int): android.graphics.Bitmap {
        scaled[side]?.let { (src, bmp) -> if (src === ref) return bmp }
        val s = side.toFloat() / maxOf(ref.width, ref.height)
        val bmp = if (s >= 1f) ref else android.graphics.Bitmap.createScaledBitmap(ref, (ref.width * s).toInt(), (ref.height * s).toInt(), true)
        scaled[side] = ref to bmp
        return bmp
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun CustomRow(vm: FilmViewModel, l: CustomLut, baseName: String?, selected: Boolean,
                      onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) FilmAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onSelect).padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LutThumb(vm, l.file, Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name, color = FilmWhite, fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                baseName?.let { Text(tf("Basis: %s", it), color = FilmWhite.copy(alpha = 0.55f), fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Bearbeiten")) { onEdit() }
            Chip(if (confirm) t("Wirklich löschen?") else t("Löschen")) { if (confirm) onDelete() else confirm = true }
        }
    }
}

/** Filmfamilien (Look-Gruppen) lesbar. */
private fun familyName(id: String): String = when (id) {
    "agfa_early" -> t("Frühes Agfacolor")
    "agfa_ultra" -> t("Agfa Ultra (hochgesättigt)")
    "agfa_vista" -> t("Agfa Vista (Amateur-Negativ)")
    "agfachrome" -> t("Agfachrome (Diafilm)")
    "chromogen" -> t("Chromogener S/W-Film (C-41)")
    "cinestill_t" -> t("CineStill Kunstlicht")
    "classic_pan" -> t("Klassischer Panfilm")
    "consumer_neg" -> t("Amateur-Farbnegativ")
    "eastman_early" -> t("Frühes Eastman-Farbnegativ")
    "ektachrome" -> t("Ektachrome (Diafilm)")
    "ektachrome_vs" -> t("Ektachrome VS (kräftig)")
    "ektar" -> t("Ektar (feinstes Farbnegativ)")
    "eterna" -> t("Fuji Eterna (Kino, flach)")
    "extended_red" -> t("Erweiterte Rotempfindlichkeit")
    "ferrania_early" -> t("Frühes Ferraniacolor")
    "fine_pan" -> t("Feinkorn-Panfilm")
    "fujichrome_early" -> t("Frühes Fujichrome")
    "fujicolor" -> t("Fujicolor (Negativ)")
    "gold" -> t("Kodak Gold (Amateur, warm)")
    "harman_phoenix" -> t("Harman Phoenix (experimentell)")
    "highspeed" -> t("Hochempfindlicher S/W-Film")
    "highspeed_reversal" -> t("Hochempfindlicher Diafilm")
    "infrared" -> t("Infrarotfilm")
    "kodachrome" -> "Kodachrome"
    "lomo_purple" -> t("LomoChrome (Farbtausch)")
    "ortho" -> t("Orthochromatisch (rotblind)")
    "orwochrom" -> t("ORWOchrom (Diafilm)")
    "orwocolor" -> t("ORWOcolor (Negativ)")
    "portra" -> t("Portra (Porträt-Negativ)")
    "portra_vc" -> t("Portra VC (kräftiger)")
    "pro400h" -> t("Fuji Pro 400H (pastell)")
    "provia" -> t("Provia (neutraler Dia)")
    "reala" -> t("Reala (natürliches Negativ)")
    "reversal_bw" -> t("S/W-Umkehrfilm")
    "superia" -> t("Superia (Amateur-Negativ)")
    "technical" -> t("Technischer Film (Dokumente)")
    "tmax" -> t("T-Max (Flachkristall)")
    "ultrafine" -> t("Feinstkorn-Film")
    "ultramax" -> t("UltraMax (Amateur)")
    "velvia100" -> "Velvia 100"
    "velvia50" -> t("Velvia 50 (extrem gesättigt)")
    "vision3" -> t("Vision3 (Kinonegativ)")
    "vision_old" -> t("Kodak-Kinonegativ (ältere Generation)")
    "wolfen_nc" -> t("Wolfen NC (Negativ)")
    else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
