package com.volkskamera.app.ui

import com.volkskamera.app.t
import com.volkskamera.app.tf
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.data.Combo
import com.volkskamera.app.data.FilmCatalog
import com.volkskamera.app.render.BwFilter
import com.volkskamera.app.render.MicProfile
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite

/** Kombinationen aus Film, Farbfilter und Ton: speichern, verwenden, teilen, einfügen. */
@UnstableApi
@Composable
fun ComboScreen(vm: FilmViewModel, catalog: FilmCatalog, onUsed: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    androidx.activity.compose.BackHandler { onBack() }
    var name by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }

    fun label(c: Combo) = c.customLutName?.let { tf("Eigene LUT: %s", it) } ?: c.filmId?.let { catalog.byId(it)?.fullLabel } ?: "–"

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(top = 12.dp, start = 16.dp, end = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(t("‹ Zurück"), selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t("Kombinationen"), color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text(t("Eine Kombination merkt sich Film, Farbfilter und Ton (Mikrofon, Rauschen, Knacken, Brummen) – zum schnellen Wechseln oder zum Teilen mit anderen."),
            color = FilmWhite.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))

        // aktuelle Einstellung speichern
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text(t("Name (z. B. „Sommer 1960“)")) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = FilmWhite, unfocusedTextColor = FilmWhite,
                    focusedBorderColor = FilmAccent, focusedLabelColor = FilmAccent, cursorColor = FilmAccent),
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Pill(t("Aktuelle speichern"), selected = true) { vm.saveCombo(name); name = ""; info = t("Gespeichert.") }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(t("Aus Zwischenablage einfügen"), selected = false, small = true) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                info = if (vm.importCombo(text)) t("Kombination eingefügt.") else t("In der Zwischenablage ist keine Volkskamera-Kombination.")
            }
        }
        info?.let { Text(it, color = FilmAccent, fontSize = 12.sp) }
        Spacer(Modifier.height(6.dp))
        if (vm.combos.isEmpty()) Text(t("Noch keine Kombinationen gespeichert."), color = FilmWhite.copy(alpha = 0.6f), fontSize = 13.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(vm.combos.reversed(), key = { it.name }) { c -> ComboRow(c, label(c),
                onUse = { vm.applyCombo(c, catalog); onUsed() },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, tf("Volkskamera-Kombination „%s“", c.name))
                        .putExtra(Intent.EXTRA_TEXT, c.toShareText(label(c)))
                    context.startActivity(Intent.createChooser(send, t("Kombination teilen")))
                },
                onDelete = { vm.deleteCombo(c.name) }) }
        }
    }
}

@Composable
private fun ComboRow(c: Combo, film: String, onUse: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp)) {
        Text(c.name, color = FilmWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        ComboLine(t("Film"), film)
        if (c.bwFilter != "KEINER") ComboLine(t("Filter"), BwFilter.byName(c.bwFilter).fullName)
        ComboLine(t("Mikrofon"), c.mic?.let { m -> runCatching { MicProfile.valueOf(m).label }.getOrNull() } ?: t("Originalton"))
        val extra = listOfNotNull(t("Knacken").takeIf { c.crackleOn }, t("Brummen").takeIf { c.humOn })
        if (extra.isNotEmpty()) ComboLine(t("Störungen"), extra.joinToString(", "))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(t("Verwenden"), selected = true, small = true) { onUse() }
            Pill(t("Teilen"), selected = false, small = true) { onShare() }
            Pill(if (confirm) t("Wirklich löschen?") else t("Löschen"), selected = false, small = true) { if (confirm) onDelete() else confirm = true }
        }
    }
}

@Composable private fun ComboLine(k: String, v: String) {
    Row { Text(k, color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.width(90.dp)); Text(v, color = FilmWhite, fontSize = 12.sp) }
}

/** Bestätigung, wenn eine Kombination über „Teilen“ an die App geschickt wurde. */
@UnstableApi
@Composable
fun IncomingComboDialog(vm: FilmViewModel, catalog: FilmCatalog) {
    val c = vm.pendingCombo ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.pendingCombo = null },
        title = { Text(tf("Kombination „%s“", c.name)) },
        text = { Text(t("Film: ") + (c.customLutName ?: c.filmId?.let { catalog.byId(it)?.fullLabel } ?: "–") +
            t("\nMikrofon: ") + (c.mic?.let { runCatching { MicProfile.valueOf(it).label }.getOrNull() } ?: t("Originalton")) +
            t("\n\nSpeichern und gleich verwenden?")) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            vm.importCombo(Combo.MARK + c.toJson().toString()); vm.applyCombo(c, catalog)
        }) { Text(t("Übernehmen")) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.pendingCombo = null }) { Text(t("Abbrechen")) } },
    )
}

