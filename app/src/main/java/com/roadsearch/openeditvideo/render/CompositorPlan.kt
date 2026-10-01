package com.roadsearch.openeditvideo.render

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.VideoClip

/** Immutable, testable render description compiled from the editor state. */
data class CompositorLayer(
    val clipId: Long,
    val track: Int,
    val timelineStartMs: Long,
    val durationMs: Long,
    val zIndex: Int,
    val blendMode: BlendMode,
)

data class CompositorPlan(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val layers: List<CompositorLayer>,
    val transitions: List<Transition>,
)

object CompositorPlanBuilder {
    fun build(state: EditorUiState, width: Int = 1080, height: Int = 1920): CompositorPlan {
        val layers = state.clips
            .map { clip ->
                CompositorLayer(
                    clipId = clip.id,
                    track = clip.track,
                    timelineStartMs = clip.timelineStartMs,
                    durationMs = clipDurationMs(clip),
                    zIndex = clip.track,
                    blendMode = state.blendModes[clip.id] ?: BlendMode.NORMAL,
                )
            }
            .sortedWith(compareBy<CompositorLayer> { it.zIndex }.thenBy { it.timelineStartMs }.thenBy { it.clipId })
        val duration = maxOf(
            state.durationMs,
            layers.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
            state.audioClips.maxOfOrNull { it.timelineStartMs + (it.endMs - it.startMs).coerceAtLeast(0L) } ?: 0L,
        )
        return CompositorPlan(width, height, duration, layers, state.transitions)
    }

    private fun clipDurationMs(clip: VideoClip): Long =
        (clip.endMs - clip.startMs).takeIf { it > 0L }
            ?: clip.sourceDurationMs.coerceAtLeast(0L).minus(clip.startMs).coerceAtLeast(0L)
}
