package com.volkskamera.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.volkskamera.app.data.Films
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmRed
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.delay

/**
 * Schlichter Player für die eigenen Filme: Antippen = Start/Pause, unten Zeitleiste,
 * oben zurück zur Kamera, zur Übersicht, Teilen, Löschen (mit Rückfrage). Läuft in Schleife.
 */
@UnstableApi
@Composable
fun PlayerScreen(uri: Uri, onBack: () -> Unit, onOverview: () -> Unit, onDeleted: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    var playing by remember { mutableStateOf(true) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        playing = true
    }
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) posMs = player.currentPosition
            durMs = player.duration.coerceAtLeast(0L)
            delay(100)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // ganze Fläche: Antippen = Start/Pause
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                playing = !playing
                player.playWhenReady = playing
            },
            contentAlignment = Alignment.Center,
        ) {
            if (!playing) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, "Abspielen", tint = FilmWhite, modifier = Modifier.size(56.dp))
                }
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill("‹ Kamera", selected = false, onClick = onBack)
                Pill("Alle Filme", selected = false, onClick = onOverview)
                Spacer(Modifier.weight(1f))
                if (confirmDelete) {
                    Text("Löschen?", color = FilmWhite)
                    Pill("Ja", selected = true) {
                        player.stop()
                        if (Films.delete(context, uri)) onDeleted()
                        confirmDelete = false
                    }
                    Pill("Nein", selected = false) { confirmDelete = false }
                } else {
                    RoundButton(Icons.Filled.Share, "Teilen") {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Film teilen"))
                    }
                    RoundButton(Icons.Filled.Delete, "Löschen", tint = FilmRed) { confirmDelete = true }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time(if (dragging) (dragPos * durMs).toLong() else posMs), color = FilmWhite, fontSize = 13.sp,
                    modifier = Modifier.width(48.dp))
                Slider(
                    value = if (dragging) dragPos else if (durMs > 0) posMs / durMs.toFloat() else 0f,
                    onValueChange = { dragging = true; dragPos = it },
                    onValueChangeFinished = { player.seekTo((dragPos * durMs).toLong()); dragging = false },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = FilmAccent, activeTrackColor = FilmAccent),
                )
                Text(time(durMs), color = FilmWhite, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun time(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = FilmWhite, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color(0x55000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint) }
}
