package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransitionType

/** Pure timing math shared by preview and export backends. */
object TransitionPlanner {
    data class Window(val startMs: Long, val endMs: Long)

    fun window(transition: Transition, boundaryMs: Long): Window {
        val half = (transition.durationMs.coerceAtLeast(1L)) / 2L
        return Window((boundaryMs - half).coerceAtLeast(0L), boundaryMs + (transition.durationMs - half))
    }

    fun alpha(type: TransitionType, progress: Float): Pair<Float, Float> = when (type) {
        TransitionType.CUT -> 1f to 0f
        TransitionType.CROSS_FADE -> (1f - progress) to progress
        TransitionType.FADE_THROUGH -> {
            val p = progress.coerceIn(0f, 1f)
            if (p < .5f) (1f - p * 2f) to 0f else 0f to ((p - .5f) * 2f)
        }
        TransitionType.WIPE_LEFT, TransitionType.WIPE_RIGHT -> 1f to 1f
    }
}
