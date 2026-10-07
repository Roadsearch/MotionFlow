package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TransitionType
import com.roadsearch.openeditvideo.model.keyframesAt

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
        // Cross-fade and fade-through are rendered by the Media3 path through opacity ramps
        // (see TransitionRenderPlan / AdvancedRenderPlanner); this gate must stay aligned with that planner.
        val active = state.transitions.filter { it.type != TransitionType.CUT }
        val wipes = active.filter { it.type == TransitionType.WIPE_LEFT || it.type == TransitionType.WIPE_RIGHT }
        if (wipes.isNotEmpty()) {
            add("Les transitions de type volet (wipe) ne sont pas encore rendues à l'export : remplacez-les par un fondu ou une coupe.")
        }
        val fades = active.filter { it.type == TransitionType.CROSS_FADE || it.type == TransitionType.FADE_THROUGH }
        if (fades.isNotEmpty() && state.blendModes.any { it.value != BlendMode.NORMAL }) {
            add("Les fondus enchaînés ne sont pas encore combinables avec un mode de fusion avancé.")
        }
    }
}
