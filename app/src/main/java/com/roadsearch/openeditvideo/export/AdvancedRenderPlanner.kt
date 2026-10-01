package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TransitionType

/**
 * Plans rendering without silently downgrading unsupported effects.
 * Media3 remains the default backend. Optional FFmpeg is selected only when explicitly enabled
 * and when the feature can be represented by the available filter graph.
 */
object AdvancedRenderPlanner {
    enum class Backend { MEDIA3, FFMPEG_OPTIONAL, UNSUPPORTED }

    data class Decision(
        val backend: Backend,
        val reasons: List<String>,
        val warnings: List<String> = emptyList(),
    )

    fun decide(state: EditorUiState, ffmpegAvailable: Boolean): Decision {
        val blend = state.blendModes.filterValues { it != BlendMode.NORMAL }.values.distinct()
        val transitions = state.transitions.filter { it.type != TransitionType.CUT }
        val reasons = buildList {
            if (blend.isNotEmpty()) add("Blend modes: ${blend.joinToString()}")
            if (transitions.isNotEmpty()) add("Transitions: ${transitions.map { it.type }.distinct().joinToString()}")
        }
        if (reasons.isEmpty()) return Decision(Backend.MEDIA3, emptyList())
        if (!ffmpegAvailable) {
            return Decision(
                Backend.UNSUPPORTED,
                reasons,
                listOf("Activez le backend FFmpeg optionnel pour les fonctions avancées non couvertes par Media3."),
            )
        }
        // The advanced backend in V66–V75 implements blend modes and absolute-time overlays.
        // Two-input transitions still require the dedicated programmable compositor, so do not
        // route them to a backend that cannot actually render them.
        if (transitions.isNotEmpty()) return Decision(Backend.UNSUPPORTED, reasons, listOf("Les transitions deux-entrées attendent le compositeur programmable."))
        return Decision(Backend.FFMPEG_OPTIONAL, reasons)
    }
}
