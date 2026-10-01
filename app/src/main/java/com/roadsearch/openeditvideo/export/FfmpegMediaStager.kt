package com.roadsearch.openeditvideo.export

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Materializes content:// media to private cache files for native FFmpeg backends. */
class FfmpegMediaStager(private val context: Context) {
    data class Session(val files: List<File>) {
        fun cleanup() { files.forEach { runCatching { it.delete() } } }
    }

    fun stage(uris: List<Uri>): Session {
        val unique = uris.distinct()
        val files = ArrayList<File>(unique.size)
        try {
            unique.forEachIndexed { index, uri ->
                val target = File(context.cacheDir, "ffmpeg_input_${System.nanoTime()}_$index.bin")
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output, DEFAULT_BUFFER) }
                    }
                } ?: throw IllegalArgumentException("Impossible d'ouvrir le média $uri")
                require(target.length() > 0L) { "Média vide: $uri" }
                files += target
            }
        } catch (t: Throwable) {
            files.forEach { runCatching { it.delete() } }
            throw t
        }
        return Session(files)
    }

    companion object { private const val DEFAULT_BUFFER = 64 * 1024 }
}
