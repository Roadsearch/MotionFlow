package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.EditorUiState
import java.io.File

/** Chooses Media3 or the optional advanced backend without silent feature loss. */
class RoutedVideoExporter(
    private val media3: VideoExporter,
    private val advanced: VideoExporter,
    private val ffmpegAvailable: () -> Boolean,
) : VideoExporter {
    override suspend fun export(state: EditorUiState, output: File, settings: ExportSettings, onProgress: (Float) -> Unit) {
        val decision = AdvancedRenderPlanner.decide(state, ffmpegAvailable())
        when (decision.backend) {
            AdvancedRenderPlanner.Backend.MEDIA3 -> media3.export(state, output, settings, onProgress)
            AdvancedRenderPlanner.Backend.FFMPEG_OPTIONAL -> advanced.export(state, output, settings, onProgress)
            AdvancedRenderPlanner.Backend.UNSUPPORTED ->
                error("Export impossible: ${decision.reasons.joinToString("; ")}")
        }
    }
}
