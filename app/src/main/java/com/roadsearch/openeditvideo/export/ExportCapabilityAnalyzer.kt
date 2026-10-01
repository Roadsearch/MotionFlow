package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TransitionType

/** Explicit gate for features that still require a custom compositor beyond public Media3 APIs. */
object ExportCapabilityAnalyzer {
    fun errors(state: EditorUiState): List<String> = buildList {
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
        state.transitions
            .filter { it.type != TransitionType.CUT }
            .takeIf { it.isNotEmpty() }
            ?.let {
                add("Transitions non-CUT : le crossfade/transition à deux entrées doit encore passer par le compositeur programmable; l'export Media3 public ne les honore pas encore de manière générale.")
            }
    }
}
