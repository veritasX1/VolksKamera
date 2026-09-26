package com.volkskamera.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.volkskamera.app.data.SoundEntry
import com.volkskamera.app.data.SoundKind
import com.volkskamera.app.data.SoundLibrary
import com.volkskamera.app.render.ProjectorPreview
import com.volkskamera.app.render.SoundMix
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmRed
import com.volkskamera.app.ui.theme.FilmSurface
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Spielt einzelne Geräusche zum Vorhören ab (Ambient/Projektor als Schleife, Effekte einmal). */
class SoundPlayer(private val context: Context, private val scope: CoroutineScope) {
    var playingId by mutableStateOf<String?>(null); private set
    private var preview: ProjectorPreview? = null

    fun toggle(id: String, loop: Boolean) {
        if (playingId == id) { stop(); return }
        play(id, loop)
    }

    fun play(id: String, loop: Boolean) {
        stop()
        playingId = id
        scope.launch {
            withContext(Dispatchers.IO) { SoundLibrary.builtIn(context); SoundLibrary.load(context, id) }
            if (playingId != id) return@launch
            val gen = SoundMix.generator(48000, id, loop) ?: run { playingId = null; return@launch }
            preview = ProjectorPreview { _, _ -> gen }.also { it.start(com.volkskamera.app.render.FilmLook()) }
        }
    }

    fun stop() {
        preview?.stop()
        preview = null
        playingId = null
    }
}

@Composable
fun rememberSoundPlayer(): SoundPlayer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = remember { SoundPlayer(context.applicationContext, scope) }
    DisposableEffect(Unit) { onDispose { p.stop() } }
    return p
}

/**
 * Auswahl eines Geräuschs: mitgelieferte (nach Gruppen), erzeugte und eigene Dateien (mp3/wav).
 * Jede Zeile lässt sich vorhören. [onPick] mit null = "Keins".
 */
@Composable
fun SoundPickerDialog(title: String, kind: SoundKind, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = rememberSoundPlayer()
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val entries = remember(refresh) { SoundLibrary.all(context, kind) }
    val loop = kind != SoundKind.EFFEKT

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val e = withContext(Dispatchers.IO) { SoundLibrary.addCustom(context, uri, kind) }
            busy = false
            if (e == null) error = "Die Datei lässt sich nicht abspielen (unterstützt: mp3, wav)."
            else { error = null; refresh++; player.stop(); onPick(e.id) }
        }
    }

    Dialog(onDismissRequest = { player.stop(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f).clip(RoundedCornerShape(18.dp)).background(FilmSurface).padding(14.dp),
        ) {
            Text(title, color = FilmAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (busy) "Lese Datei …" else "Eigene Datei (mp3/wav) …", selected = false, enabled = !busy) {
                    openFile.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"))
                }
                Pill("Keins", selected = current == null) { player.stop(); onPick(null) }
            }
            error?.let { Text(it, color = FilmRed, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
            val order = listOf("Regen", "Gewitter", "Brummen", "Vorführraum", "Erzeugt", "Donner", "Horror", "Projektor",
                "Sonstiges", "Aufnahmen", "Eigene Dateien")
            val groups = entries.groupBy { it.group }.toList()
                .sortedBy { (g, _) -> order.indexOf(g).let { if (it < 0) order.size else it } }
            LazyColumn(Modifier.weight(1f)) {
                groups.forEach { (g, list) ->
                    item(key = "g:$g") {
                        Text(g.uppercase(), color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.id + it.kind }) { e ->
                        SoundRow(e, e.id == current, player.playingId == e.id,
                            onPlay = { player.toggle(e.id, loop) },
                            onPick = { player.stop(); onPick(e.id) },
                            onRemove = if (e.isFile) ({ SoundLibrary.removeCustom(context, e.id); refresh++ }) else null)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Pill("Schließen", selected = false) { player.stop(); onDismiss() }
            }
        }
    }
}

@Composable
private fun SoundRow(e: SoundEntry, selected: Boolean, playing: Boolean, onPlay: () -> Unit, onPick: () -> Unit, onRemove: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) FilmAccent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onPick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(if (playing) "■" else "▶", selected = playing, small = true, onClick = onPlay)
        Text(e.name, color = if (selected) FilmAccent else FilmWhite, fontSize = 15.sp, modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        onRemove?.let { Pill("✕", selected = false, small = true, onClick = it) }
    }
}
