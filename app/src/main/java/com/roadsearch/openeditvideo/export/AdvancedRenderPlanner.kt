package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TransitionType
import com.roadsearch.openeditvideo.scene.SceneGraph

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
        val active = state.transitions.filter { it.type != TransitionType.CUT }
        // Cross-fade and fade-through are rendered by the Media3 path (opacity ramps); wipes are not rendered at all.
        val fades = active.filter { it.type == TransitionType.CROSS_FADE || it.type == TransitionType.FADE_THROUGH }
        val wipes = active.filter { it.type == TransitionType.WIPE_LEFT || it.type == TransitionType.WIPE_RIGHT }
        val reasons = buildList {
            if (blend.isNotEmpty()) add("Blend modes: ${blend.joinToString()}")
            if (wipes.isNotEmpty()) add("Transitions: ${wipes.map { it.type }.distinct().joinToString()}")
            if (blend.isNotEmpty() && fades.isNotEmpty()) add("Fondus avec modes de fusion : non combinables pour l'instant")
        }
        if (reasons.isEmpty()) return Decision(Backend.MEDIA3, emptyList())
        if (!ffmpegAvailable) {
            return Decision(
                Backend.UNSUPPORTED,
                reasons,
                listOf("Activez le backend FFmpeg optionnel pour les fonctions avancées non couvertes par Media3."),
            )
        }
        // The FFmpeg backend implements blend modes only: it must not receive projects with transitions it would drop.
        if (wipes.isNotEmpty() || fades.isNotEmpty()) {
            return Decision(Backend.UNSUPPORTED, reasons, listOf("Les transitions deux-entrées attendent le compositeur programmable."))
        }
        // The FFmpeg graph stacks full frames at (0,0): it would silently drop keyframes, static transforms and opacity.
        val transformed = state.clips.filter { SceneGraph.transformsClip(it, state.nullObjects) }
        if (transformed.isNotEmpty()) {
            return Decision(
                Backend.UNSUPPORTED,
                reasons + "Position, échelle, rotation ou opacité (images clés ou réglages) sur ${transformed.size} clip(s) : le backend FFmpeg ne les rend pas.",
                listOf("Retirez les animations et transformations, ou les modes de fusion avancés, pour exporter."),
            )
        }
        return Decision(Backend.FFMPEG_OPTIONAL, reasons)
    }
}
