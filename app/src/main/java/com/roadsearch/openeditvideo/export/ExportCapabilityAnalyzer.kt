package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.keyframesAt
import com.roadsearch.openeditvideo.scene.SceneGraph

/** Explicit gate for features that still require a custom compositor beyond public Media3 APIs. */
object ExportCapabilityAnalyzer {
    fun errors(state: EditorUiState): List<String> = buildList {
        // Secondary video tracks and gaps in V1 are composited by MultiTrackCompositionFactory: one compositor input per
        // clip over a black background input, each with its own placement, opacity and time window.
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
