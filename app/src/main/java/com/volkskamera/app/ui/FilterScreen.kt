package com.volkskamera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.render.BwFilter
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Farbfilter für den gewählten Schwarzweißfilm: jeder Filter mit Beispielbild, Bezeichnungen und Einsatz. */
@UnstableApi
@Composable
fun FilterScreen(vm: FilmViewModel, film: com.volkskamera.app.data.FilmStock?, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    // Filter-LUTs im Hintergrund vorbereiten (werden zwischengespeichert)
    val base = (if (film?.id == vm.selectedFilm?.id) vm.activeCustom?.file else null) ?: film?.look?.lut
    val paths by produceState<Map<BwFilter, String>>(emptyMap(), film, base) {
        if (film == null || base == null) return@produceState
        val m = HashMap<BwFilter, String>()
        for (f in BwFilter.entries) {
            withContext(Dispatchers.IO) { vm.effectiveLut(film, base, f) }?.let { m[f] = it; value = HashMap(m) }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ Zurück", selected = false) { onBack() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Farbfilter", color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(film?.let { "für ${it.fullLabel}" } ?: "", color = FilmAccent, fontSize = 12.sp)
            }
        }
        Text("Ein Filter hellt seine eigene Farbe auf und dunkelt die Gegenfarbe ab. Die längere Belichtung (Filterfaktor) ist bereits ausgeglichen." +
            if (film?.materialtyp?.contains("ortho", ignoreCase = true) == true || film?.name?.contains("ortho", ignoreCase = true) == true)
                " Hinweis: orthochromatischer Film ist rotblind – Rot- und Orangefilter sperren fast alles, was er sieht, und waren dafür in der Praxis kaum brauchbar." else "",
            color = FilmWhite.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(BwFilter.entries, key = { it.name }) { f ->
                val sel = f == vm.bwFilter
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (sel) FilmAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                        .border(if (sel) 2.dp else 0.dp, if (sel) FilmAccent else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { vm.chooseBwFilter(f) }.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.width(150.dp)) {
                        paths[f]?.let { LutThumb(vm, it, Modifier.fillMaxWidth(), big = true) }
                            ?: Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(8.dp)))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(filterColor(f)))
                            Spacer(Modifier.width(6.dp))
                            Text(f.label, color = FilmWhite, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                        if (f != BwFilter.KEINER) {
                            Text(listOf("Kodak ${f.wratten}", f.others).filter { it.isNotBlank() }.joinToString(" · "),
                                color = FilmAccent.copy(alpha = 0.9f), fontSize = 11.sp)
                            Text("Filterfaktor ${f.factor}", color = FilmWhite.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(f.use, color = FilmWhite.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun filterColor(f: BwFilter): Color =
    if (f == BwFilter.KEINER) Color(0x33FFFFFF)
    else Color(f.r.coerceIn(0f, 1f), f.g.coerceIn(0f, 1f), f.b.coerceIn(0f, 1f))
