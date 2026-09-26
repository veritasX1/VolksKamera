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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.volkskamera.app.data.FilmClip
import com.volkskamera.app.data.Films
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmSurface
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Übersicht aller mit VolksKamera entwickelten Filme, neuester zuerst. */
@Composable
fun FilmsScreen(onOpen: (Uri) -> Unit, onBack: () -> Unit, onCompose: () -> Unit = {}) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val films by produceState<List<FilmClip>?>(null) { value = withContext(Dispatchers.IO) { Films.list(context) } }

    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(t("‹ Kamera"), selected = false, onClick = onBack)
            Text(t("Meine Filme"), color = FilmAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(start = 16.dp))
            films?.let { Text("  ${it.size}", color = FilmWhite.copy(alpha = 0.5f)) }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Pill(t("Zusammenstellen"), selected = true, enabled = !films.isNullOrEmpty(), onClick = onCompose)
        }
        val list = films
        when {
            list == null -> {}
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(t("Noch keine Filme – einfach losfilmen."), color = FilmWhite.copy(alpha = 0.7f))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.uri.toString() }) { f -> FilmTile(f) { onOpen(f.uri) } }
            }
        }
    }
}

@Composable
private fun FilmTile(f: FilmClip, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, f.uri) { value = withContext(Dispatchers.IO) { Films.thumbnail(context, f.uri) } }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(FilmSurface)) {
            thumb?.let { Image(it.asImageBitmap(), f.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            val s = f.durationMs / 1000
            Text("%d:%02d".format(s / 60, s % 60), color = FilmWhite, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(
            SimpleDateFormat("d. MMM yyyy, HH:mm", Locale.GERMANY).format(Date(f.dateAddedSec * 1000)),
            color = FilmWhite.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}
