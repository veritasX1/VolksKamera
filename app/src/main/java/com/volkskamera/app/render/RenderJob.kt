package com.volkskamera.app.render

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import android.media.MediaMetadataRetriever
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ein Video entwickeln: Media3 Transformer dekodiert, schickt jedes Bild durch
 * FilmLookEffect, kodiert neu und reicht den Ton durch. Rotation, Codec-Wahl und
 * Rückfall auf Software-Decoder übernimmt Media3 – das war in RetroCam Handarbeit.
 *
 * Sicherheitsprinzip aus RetroCam: das Original wird nie angefasst. Gerendert wird in
 * eine Cache-Datei, erst die fertige Datei wird in die Galerie kopiert.
 */
@UnstableApi
class RenderJob(private val context: Context) {

    suspend fun render(source: Uri, requested: FilmLook, onProgress: (Int) -> Unit): Uri =
        render(listOf(source), requested, null, onProgress)

    /**
     * Mehrere Stücke (Aufnahme mit Pausen) als EINEN Film entwickeln: Countdown vor dem ersten,
     * Filmriss am Ende des letzten Stücks. Stücke mit anderer Auflösung (z.B. Frontkamera)
     * werden auf die Größe des ersten Stücks gebracht.
     */
    suspend fun render(sources: List<Uri>, requested: FilmLook, cues: SoundCues? = null, onProgress: (Int) -> Unit): Uri {
        require(sources.isNotEmpty())
        val tmp = File(context.cacheDir, "render_${System.currentTimeMillis()}.mp4")
        // Mehr Bilder als die Quelle hat, gehen nicht (60 fps aus 30-fps-Material = 30 fps).
        // Die tatsächliche Filmrate gilt dann für Bild UND Ton (Projektor-Klicks im selben Takt).
        val srcFps = sources.mapNotNull { sourceFps(it) }.minOrNull()
        val look = if (requested.targetFps != null && srcFps != null && requested.targetFps > srcFps + 0.5f)
            requested.copy(targetFps = srcFps) else requested
        try {
            // Projektoraufnahme, Ambient-Regler und Effekte vorab dekodieren (der Tonprozessor greift nur zu)
            withContext(Dispatchers.IO) {
                com.volkskamera.app.data.SoundLibrary.builtIn(context)
                SoundMix.neededIds(look, cues).forEach { com.volkskamera.app.data.SoundLibrary.load(context, it) }
            }
            val hasMix = !SoundMix(48000, look, cues).isEmpty
            val silent = look.audioMode == AudioMode.STUMM && !look.projector && !look.hasBackground && !hasMix
            val samples = ClickSamples.load(context)
            val infos = sources.map { sourceInfo(it) }
            // Beginn jedes Stücks in der Aufnahmezeit (für Effekte und Reglerbewegungen)
            val offsets = infos.runningFold(0L) { acc, inf -> acc + inf.durationUs / 1000 }
            val first = infos.first()
            var anyAudioFx = false
            val clips = sources.mapIndexed { i, src ->
                val last = i == sources.lastIndex
                // Ton: eigener Prozessor je Stück (alte Aufnahme / Projektor); ganz stumm = Tonspur weg
                val audio = FilmAudioProcessor.forLook(look, samples, cues, offsets[i])
                if (audio != null) anyAudioFx = true
                val video = buildList {
                    if (sources.size > 1) add(Presentation.createForWidthAndHeight(first.width, first.height, Presentation.LAYOUT_SCALE_TO_FIT))
                    // Filmriss nur am Ende des letzten Stücks
                    add(if (last) FilmLookEffect(look, infos[i].durationUs) else FilmLookEffect(look.copy(burnEnd = null)))
                    // gewählte Auflösung (kurze Seite: 480, 576, 720 oder 1080)
                    add(Presentation.createForShortSide(com.volkskamera.app.data.CameraPrefs.resolution(context)))
                }
                EditedMediaItem.Builder(MediaItem.fromUri(src))
                    .setRemoveAudio(silent)
                    .setEffects(Effects(listOfNotNull(audio), video))
                    .build()
            }
            val items = buildList {
                if (look.countdown) add(countdownItem(look, first))
                addAll(clips)
            }
            // Countdown (Bild ohne Ton) oder Video ohne Tonspur: Media3 erzeugt eine stille Spur,
            // auf die Piepser/Projektor kommen
            val sequence = EditedMediaItemSequence.Builder(items)
                .experimentalSetForceAudioTrack(anyAudioFx || look.countdown)
                .build()
            val composition = Composition.Builder(sequence)
                // HDR-Aufnahmen vorher auf SDR bringen, die LUTs erwarten normales Rec.709
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()
            export(composition, tmp, bitrateFor(sources.first()), onProgress)
            return withContext(Dispatchers.IO) { saveToGallery(tmp) }
        } finally {
            tmp.delete()
        }
    }

    private class SourceInfo(val width: Int, val height: Int, val durationUs: Long)

    /** Größe (so wie das Video angezeigt wird, also nach Drehung) und Länge der Quelle. */
    private fun sourceInfo(source: Uri): SourceInfo {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, source)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val dur = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            if (rot % 180 != 0) SourceInfo(h, w, dur) else SourceInfo(w, h, dur)
        } catch (e: Exception) {
            SourceInfo(1920, 1080, 0)
        } finally {
            r.release()
        }
    }

    /**
     * Countdown-Vorspann als eigener Abschnitt VOR dem Clip: ein schwarzes Platzhalterbild in
     * Videogröße, 5 s lang, das CountdownEffect übermalt. Danach der Film-Look wie beim Clip
     * (ohne Leak und Filmriss), Ton = Piepser + ggf. Projektor.
     */
    private fun countdownItem(look: FilmLook, info: SourceInfo): EditedMediaItem {
        val img = File(context.cacheDir, "vorspann_${info.width}x${info.height}.png")
        if (!img.exists()) {
            val b = android.graphics.Bitmap.createBitmap(info.width, info.height, android.graphics.Bitmap.Config.ARGB_8888)
            b.eraseColor(android.graphics.Color.BLACK)
            img.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }
        val media = MediaItem.Builder().setUri(Uri.fromFile(img))
            .setImageDurationMs(CountdownRenderer.SECONDS * 1000L).build()
        return EditedMediaItem.Builder(media)
            .setFrameRate(Math.round(look.targetFps ?: 30f))
            .setEffects(Effects(
                listOf(FilmAudioProcessor.forCountdown(look, ClickSamples.load(context))),
                listOf(CountdownEffect(), FilmLookEffect(look.copy(leak = null, burnEnd = null))),
            ))
            .build()
    }

    /** Bildrate der Quelle (Bilder / Dauer); null, wenn nicht ermittelbar. */
    private fun sourceFps(source: Uri): Float? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, source)
            val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toFloatOrNull()
            if (frames != null && durMs != null && durMs > 0) (frames / (durMs / 1000f)).let { Math.round(it).toFloat() } else null
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    /** Mindestens die Bitrate des Originals: Korn und Staub brauchen viele Daten,
     * sonst bügelt der Encoder sie glatt (Media3-Standard lag bei der Hälfte). */
    private fun bitrateFor(source: Uri): Int {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, source)
            val src = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            maxOf(src ?: 0, w * h * 10)   // 1080p -> ~20 Mbit/s Untergrenze
        } catch (e: Exception) {
            20_000_000
        } finally {
            r.release()
        }
    }

    enum class Intro { KEIN, SCHWARZ, WEISS, COUNTDOWN }
    enum class Outro { KEIN, SCHWARZ, WEISS, FILMRISS }

    /**
     * Mini-Editor: fertige Filme aneinanderreihen, mit Intro (aus Schwarz/Weiß einblenden oder
     * Countdown) und Outro (in Schwarz/Weiß ausblenden oder Filmriss). Die Clips haben ihren
     * Look schon – sie werden nicht erneut "entwickelt". Ton blendet mit.
     */
    suspend fun renderMontage(
        clips: List<Uri>,
        intro: Intro,
        outro: Outro,
        fadeSec: Float,
        burn: com.volkskamera.app.data.SequenceAsset?,
        countdownLook: FilmLook,
        onProgress: (Int) -> Unit,
    ): Uri {
        require(clips.isNotEmpty())
        val tmp = File(context.cacheDir, "montage_${System.currentTimeMillis()}.mp4")
        try {
            val infos = clips.map { sourceInfo(it) }
            val first = infos.first()
            val fadeUs = (fadeSec * 1_000_000).toLong()
            val items = mutableListOf<EditedMediaItem>()
            if (intro == Intro.COUNTDOWN) items += countdownItem(countdownLook, first)
            clips.forEachIndexed { i, src ->
                val isFirst = i == 0
                val isLast = i == clips.lastIndex
                val dur = infos[i].durationUs
                val inUs = if (isFirst && (intro == Intro.SCHWARZ || intro == Intro.WEISS)) fadeUs else 0L
                val outUs = if (isLast && (outro == Outro.SCHWARZ || outro == Outro.WEISS)) fadeUs else 0L
                val video = buildList {
                    // alles auf die Größe des ersten Clips bringen (Hoch-/Querformat, andere Auflösung)
                    add(Presentation.createForWidthAndHeight(first.width, first.height, Presentation.LAYOUT_SCALE_TO_FIT))
                    if (inUs > 0 || outUs > 0) {
                        // ein Clip kann Anfang und Ende zugleich sein; Farbe: die des Intros bzw. Outros
                        val white = if (inUs > 0) intro == Intro.WEISS else outro == Outro.WEISS
                        if (inUs > 0 && outUs > 0 && (intro == Intro.WEISS) != (outro == Outro.WEISS)) {
                            add(FadeEffect(inUs, 0, dur, intro == Intro.WEISS))
                            add(FadeEffect(0, outUs, dur, outro == Outro.WEISS))
                        } else {
                            add(FadeEffect(inUs, outUs, dur, white))
                        }
                    }
                    if (isLast && outro == Outro.FILMRISS && burn != null) {
                        // nur der Filmriss, sonst nichts vom Look (der Clip hat ihn schon)
                        add(FilmLookEffect(FilmLook(aspect = null, targetFps = null, burnEnd = burn), dur))
                    }
                }
                val audioOutUs = if (isLast && outro != Outro.KEIN) fadeUs.coerceAtLeast(1_000_000) else outUs
                val audio = if (inUs > 0 || audioOutUs > 0) FadeAudioProcessor(inUs, audioOutUs, dur) else null
                items += EditedMediaItem.Builder(MediaItem.fromUri(src))
                    .setEffects(Effects(listOfNotNull(audio), video))
                    .build()
            }
            val sequence = EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(true).build()
            val composition = Composition.Builder(sequence)
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()
            export(composition, tmp, bitrateFor(clips.first()), onProgress)
            return withContext(Dispatchers.IO) { saveToGallery(tmp, "VolksKamera_Montage") }
        } finally {
            tmp.delete()
        }
    }

    private suspend fun export(composition: Composition, out: File, bitrate: Int, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.Main) {
            coroutineScope {
                lateinit var transformer: Transformer
                val poll = launch {
                    val holder = ProgressHolder()
                    while (true) {
                        delay(300)
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress)
                        }
                    }
                }
                try {
                    suspendCancellableCoroutine { cont ->
                        transformer = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedVideoEncoderSettings(
                                        VideoEncoderSettings.Builder().setBitrate(bitrate).build(),
                                    )
                                    .build(),
                            )
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    cont.resume(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    cont.resumeWithException(exportException)
                                }
                            })
                            .build()
                        cont.invokeOnCancellation { transformer.cancel() }
                        transformer.start(composition, out.absolutePath)
                    }
                } finally {
                    poll.cancel()
                }
            }
        }

    private fun saveToGallery(file: File, prefix: String = "VolksKamera"): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${prefix}_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VolksKamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Galerie-Eintrag konnte nicht angelegt werden")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
