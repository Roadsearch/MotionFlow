package com.roadsearch.openeditvideo.export

import android.content.Context
import com.roadsearch.openeditvideo.model.EditorUiState
import java.io.File

/** Actual advanced video export path. It stays optional so normal builds remain Media3-first. */
class AdvancedFfmpegVideoExporter(private val context: Context) : VideoExporter {
    private val bridge = ReflectiveFfmpegBridge()
    private val stager = FfmpegMediaStager(context)

    override suspend fun export(state: EditorUiState, output: File, onProgress: (Float) -> Unit) {
        check(bridge.isAvailable()) {
            "Le backend FFmpeg optionnel n'est pas installé. Activez -PenableFfmpeg=true."
        }
        val uris = (state.clips.map { it.uri } + state.audioClips.map { it.uri }).distinct()
        val session = stager.stage(uris)
        try {
            val replacement = session.files.associateBy { file ->
                // stable file order maps to the distinct URI list below.
                uris[session.files.indexOf(file)].toString()
            }
            val stagedState = state.copy(
                clips = state.clips.map { it.copy(uri = android.net.Uri.fromFile(replacement.getValue(it.uri.toString()))) },
                audioClips = state.audioClips.map { it.copy(uri = android.net.Uri.fromFile(replacement.getValue(it.uri.toString()))) },
            )
            val command = FfmpegTimelineCommandBuilder.build(stagedState, output)
            onProgress(0f)
            check(bridge.execute(command.args)) { "FFmpeg a échoué à générer le rendu avancé." }
            onProgress(1f)
        } finally {
            session.cleanup()
        }
    }
}
