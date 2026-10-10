package com.roadsearch.openeditvideo.export

import android.content.Context
import android.net.Uri
import com.roadsearch.openeditvideo.media.MediaProbe
import com.roadsearch.openeditvideo.model.AudioClip
import com.roadsearch.openeditvideo.model.STILL_SOURCE_MS
import com.roadsearch.openeditvideo.model.VideoClip

/** Performs cheap, user-facing validation before an expensive Transformer job starts. */
object MediaSourceValidator {
    data class Report(
        val errors: List<String>,
        val warnings: List<String>,
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validate(context: Context, clips: List<VideoClip>, audio: List<AudioClip>): Report {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (clips.none { it.track == 0 }) errors += "Ajoutez au moins un clip sur V1."

        clips.filter { it.track == 0 }.forEach { clip ->
            validateUri(context, clip.uri, clip.name, errors)
            // Photos and generated backgrounds have no source duration to check.
            if (clip.sourceDurationMs >= STILL_SOURCE_MS) return@forEach
            val sourceDuration = MediaProbe.durationMsOrNull(context, clip.uri)
            if (sourceDuration == null) {
                errors += "Durée source indéterminée : ${clip.name}"
            } else {
                val end = if (clip.endMs > clip.startMs) clip.endMs else sourceDuration
                if (clip.startMs < 0 || end <= clip.startMs) errors += "Trim invalide pour ${clip.name}."
                if (end > sourceDuration + 50L) {
                    errors += "Le trim de ${clip.name} dépasse la durée source."
                }
            }
            if (clip.volume !in 0f..2f) warnings += "Volume hors plage normalisée pour ${clip.name}."
        }
        audio.forEach { item -> validateUri(context, item.uri, item.name, errors) }
        
        clips.filter { it.track > 0 }.takeIf { it.isNotEmpty() }?.let {
            errors += "Les pistes vidéo V2+ nécessitent encore le compositeur multi-input de production. Aucun calque vidéo ne sera ignoré silencieusement."
        }
        return Report(errors.distinct(), warnings.distinct())
    }

    private fun validateUri(context: Context, uri: Uri, label: String, errors: MutableList<String>) {
        val readable = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length != 0L || it.fileDescriptor.valid() } ?: false
        }.getOrElse { false }
        if (!readable) errors += "Source inaccessible : $label"
    }
}
