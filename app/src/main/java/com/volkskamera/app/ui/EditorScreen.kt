package com.volkskamera.app.ui

import com.volkskamera.app.t
import com.volkskamera.app.tf
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.data.FilmClip
import com.volkskamera.app.data.Films
import com.volkskamera.app.render.RenderJob.Intro
import com.volkskamera.app.render.RenderJob.Outro
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmRed
import com.volkskamera.app.ui.theme.FilmSurface
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mini-Editor: eigene Filme aneinanderreihen, Intro und Outro wählen, als neuen Film speichern.
 * Oben die Reihenfolge (verschieben/entfernen), darunter alle Filme zum Hinzufügen.
 */
@UnstableApi
@Composable
fun EditorScreen(vm: FilmViewModel, onBack: () -> Unit, onOpenResult: (Uri) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val all by produceState<List<FilmClip>?>(null) { value = withContext(Dispatchers.IO) { Films.list(context) } }
    val busy = vm.montage is RenderState.Running
    val on = !busy
    // fertig -> direkt im Player zeigen
    LaunchedEffect(vm.montage) { (vm.montage as? RenderState.Done)?.let { onOpenResult(it.uri) } }

    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(t("‹ Filme"), selected = false, onClick = onBack)
            Text(t("Zusammenstellen"), color = FilmAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(start = 16.dp).weight(1f))
            Pill(t("Film erstellen"), selected = true, enabled = on && vm.editClips.isNotEmpty()) { vm.startMontage() }
        }
        when (val r = vm.montage) {
            is RenderState.Running -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(tf("Erstelle Film … %d %%", r.percent), color = FilmWhite)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { r.percent / 100f }, modifier = Modifier.fillMaxWidth(),
                    color = FilmAccent, trackColor = FilmSurface)
            }
            is RenderState.Failed -> Text(tf("Fehler: %s", r.message), color = FilmRed, modifier = Modifier.padding(horizontal = 16.dp))
            else -> {}
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val total = vm.editClips.sumOf { it.durationMs } / 1000
            Section(tf("Reihenfolge  ·  %1\$d Clips  ·  %2\$d:%3\$02d", vm.editClips.size, total / 60, total % 60))
            if (vm.editClips.isEmpty()) {
                Text(t("Unten Filme antippen, um sie hinzuzufügen."), color = FilmWhite.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            vm.editClips.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = FilmAccent, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Thumb(c, Modifier.width(96.dp))
                    Text(dur(c.durationMs), color = FilmWhite, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Pill("↑", selected = false, small = true, enabled = on && i > 0) { vm.editMove(i, -1) }
                    Spacer(Modifier.width(6.dp))
                    Pill("↓", selected = false, small = true, enabled = on && i < vm.editClips.lastIndex) { vm.editMove(i, 1) }
                    Spacer(Modifier.width(6.dp))
                    Pill("✕", selected = false, small = true, enabled = on) { vm.editRemove(i) }
                }
            }

            Section(t("Intro"))
            PillRow(Intro.entries.toList(), {
                when (it) { Intro.KEIN -> t("Kein"); Intro.SCHWARZ -> t("Aus Schwarz"); Intro.WEISS -> t("Aus Weiß"); Intro.COUNTDOWN -> t("Countdown 5 … 1") }
            }, { it == vm.editIntro }, on) { vm.editIntro = it }

            Section(t("Outro"))
            PillRow(Outro.entries.toList(), {
                when (it) { Outro.KEIN -> t("Kein"); Outro.SCHWARZ -> t("In Schwarz"); Outro.WEISS -> t("In Weiß"); Outro.FILMRISS -> t("Filmriss") }
            }, { it == vm.editOutro }, on) { vm.editOutro = it }
            if (vm.editOutro == Outro.FILMRISS) {
                Spacer(Modifier.height(6.dp))
                val burns = vm.catalog?.burns.orEmpty()
                val sel = vm.editBurn ?: burns.firstOrNull()
                PillRow(burns, { t("Filmriss ") + it.name.filter(Char::isDigit).trimStart('0') }, { it.id == sel?.id }, on) { vm.editBurn = it }
            }
            val fades = vm.editIntro == Intro.SCHWARZ || vm.editIntro == Intro.WEISS ||
                vm.editOutro == Outro.SCHWARZ || vm.editOutro == Outro.WEISS
            LabeledSlider(t("Blende"), (vm.editFadeSec - 0.5f) / 2.5f, on && fades) { v -> vm.editFadeSec = 0.5f + v * 2.5f }
            Text(t("Blenddauer %.1f s").format(vm.editFadeSec), color = FilmWhite.copy(alpha = 0.5f), fontSize = 12.sp,
                modifier = Modifier.padding(start = 112.dp))

            Section(t("Filme hinzufügen"))
            val list = all
            when {
                list == null -> {}
                list.isEmpty() -> Text(t("Noch keine Filme."), color = FilmWhite.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp))
                else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.uri.toString() }) { c ->
                        Column(Modifier.width(150.dp).clickable(enabled = on) { vm.editAdd(c) }) {
                            Thumb(c, Modifier.fillMaxWidth())
                            Text("+ " + dur(c.durationMs), color = FilmWhite.copy(alpha = 0.8f), fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun dur(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Thumb(c: FilmClip, modifier: Modifier) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, c.uri) { value = withContext(Dispatchers.IO) { Films.thumbnail(context, c.uri) } }
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(FilmSurface)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
