package com.roadsearch.openeditvideo.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaStorePublisher @Inject constructor(@ApplicationContext private val context: Context) {
    fun publishVideo(source: File, displayName: String): Uri {
        require(source.exists() && source.isFile && source.length() > 0L) {
            "Le fichier exporté est vide ou introuvable"
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName.ensureMp4())
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OpenEditVideo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Impossible de créer le fichier vidéo dans la galerie")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Impossible d'ouvrir la sortie MediaStore")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun String.ensureMp4(): String = if (endsWith(".mp4", ignoreCase = true)) this else "$this.mp4"
}
