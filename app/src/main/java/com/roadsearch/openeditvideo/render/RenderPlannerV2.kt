package com.roadsearch.openeditvideo.render

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TransitionType

/** Deterministic backend planner. It never silently downgrades a requested effect. */
object RenderPlannerV2 {
    data class Plan(
        val backend: RenderBackend,
        val reasons: List<String>,
        val warnings: List<String> = emptyList(),
    )

    fun plan(state: EditorUiState, caps: RenderCapabilities, ffmpegAvailable: Boolean): Plan {
        val blends = state.blendModes.values.filter { it != BlendMode.NORMAL }.distinct()
        val transitions = state.transitions.filter { it.type != TransitionType.CUT }
        val advanced = buildList {
            if (blends.isNotEmpty()) add("blend=${blends.joinToString()}")
            if (transitions.isNotEmpty()) add("transition=${transitions.map { it.type }.distinct().joinToString()}")
        }
        if (advanced.isNotEmpty() && !ffmpegAvailable) {
            return Plan(RenderBackend.UNSUPPORTED, advanced, listOf("Backend avancé requis: activez FFmpeg ou implémentez le compositeur GPU deux-entrées."))
        }
        if (advanced.isNotEmpty()) return Plan(RenderBackend.FFMPEG_OPTIONAL, advanced)
        if (caps.supportsCustomHdrGlEffects()) return Plan(RenderBackend.MEDIA3_HDR, emptyList())
        return Plan(RenderBackend.MEDIA3_GPU, emptyList())
    }
}
