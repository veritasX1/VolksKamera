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

/** Bildraten, die das Zählwerk BILD/S anbietet. */
val FPS_CHOICES = listOf(18f, 24f, 25f, 30f, 48f, 50f, 60f)
