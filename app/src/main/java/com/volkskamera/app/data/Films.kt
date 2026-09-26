package com.volkskamera.app.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size

/** Ein mit der App entwickelter Film (liegt in Filme/VolksKamera). */
data class FilmClip(val uri: Uri, val name: String, val dateAddedSec: Long, val durationMs: Long)

/** Die eigenen Filme aus der Galerie lesen, Vorschaubilder holen, löschen. */
object Films {
    fun list(context: Context): List<FilmClip> {
        val cr = context.contentResolver
        val sel = if (Build.VERSION.SDK_INT >= 29) "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" else null
        val args = if (Build.VERSION.SDK_INT >= 29) arrayOf("${Environment.DIRECTORY_MOVIES}/VolksKamera%") else null
        val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DURATION)
        val out = mutableListOf<FilmClip>()
        cr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                out += FilmClip(
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                    c.getString(1) ?: "", c.getLong(2), c.getLong(3),
                )
            }
        }
        return out
    }

    fun thumbnail(context: Context, uri: Uri, size: Int = 320): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        else MediaMetadataRetriever().run {
            try { setDataSource(context, uri); getFrameAtTime(0) } finally { release() }
        }
    }.getOrNull()

    /** Löschen – die App hat die Datei selbst angelegt, darf sie also ohne Rückfrage des Systems löschen. */
    fun delete(context: Context, uri: Uri): Boolean = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
}
