package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.keyframesAt
import com.roadsearch.openeditvideo.scene.SceneGraph

/** Explicit gate for features that still require a custom compositor beyond public Media3 APIs. */
object ExportCapabilityAnalyzer {
    fun errors(state: EditorUiState): List<String> = buildList {
        if (state.clips.any { it.track > 0 }) {
            add("Les pistes vidéo secondaires (V2/V3+) ne sont pas encore honorées par l'export public Media3.")
        }
        val primary = state.clips.filter { it.track == 0 }.sortedBy { it.timelineStartMs }
        primary.zipWithNext().forEach { (current, next) ->
            val currentEnd = current.timelineStartMs + TimelineMath.duration(current, state.durationMs)
            if (next.timelineStartMs > currentEnd + TimelineMath.MIN_CLIP_DURATION_MS / 2) {
                add("Un trou dans la piste V1 entre deux clips ne peut pas être exporté proprement.")
            }
        }
        if (state.blendModes.any { it.value != BlendMode.NORMAL } && state.clips.none { it.track == 0 }) {
            add("Un mode de fusion avancé nécessite une piste V1 de fond.")
        }
        state.blendModes.forEach { (id, mode) ->
            if (mode != BlendMode.NORMAL) {
                val clip = state.clips.firstOrNull { it.id == id }
                val animated = clip?.let { c -> c.animation.x.isNotEmpty() || c.animation.y.isNotEmpty() || c.animation.scale.isNotEmpty() || c.animation.rotation.isNotEmpty() } == true
                val staticMoved = clip?.keyframesAt(clip.timelineStartMs)?.let { it.x != 0f || it.y != 0f || it.scale != 1f || it.rotation != 0f } == true
                if (animated || staticMoved) add("Le blend ${mode.name} du clip $id nécessite encore un blend programmable positionné; le backend avancé actuel ne sait fusionner que le plein cadre.")
            }
        }
        addAll(SceneGraph.validationErrors(state.clips, state.nullObjects, state.textOverlays))
        // Fade-through and cross-fade are represented by opacity ramps in the Media3 composition.
        // Unsupported wipes and combinations are rejected by AdvancedRenderPlanner at export time.
    }
}
