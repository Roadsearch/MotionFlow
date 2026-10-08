package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.STILL_SOURCE_MS
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransitionType
import com.roadsearch.openeditvideo.model.VideoClip
import kotlin.math.abs

/**
 * Turns "transition between clip A and clip B" into per-clip opacity ramps (and the extra source frames a
 * cross-fade needs). Pure math, shared by the exporter and the preview.
 *
 *  - FADE_THROUGH: A fades out over the last half, B fades in over the first half (dip to black). No overlap needed.
 *  - CROSS_FADE: A's tail and B's head are extended by half the duration each (from the source file) so both are
 *    visible together while B fades in on top. Without enough source material it degrades to FADE_THROUGH.
 *  - WIPE_*: not rendered (ignored here; the export planner refuses them).
 */
object TransitionRenderPlan {
    data class Fade(val startMs: Long, val endMs: Long)

    data class ClipFades(val fadeIn: Fade? = null, val fadeOut: Fade? = null) {
        /** Opacity multiplier at timeline time [timeMs]. */
        fun factor(timeMs: Long): Float {
            var f = 1f
            fadeIn?.let { f *= progress(timeMs, it) }
            fadeOut?.let { f *= 1f - progress(timeMs, it) }
            return f
        }

        private fun progress(t: Long, w: Fade): Float {
            val length = (w.endMs - w.startMs).coerceAtLeast(1L)
            return ((t - w.startMs).toFloat() / length).coerceIn(0f, 1f)
        }
    }

    class Result(val clips: List<VideoClip>, val fades: Map<Long, ClipFades>)

    private const val MIN_OVERLAP_MS = 100L
    private const val MAX_TRANSITION_MS = 3_000L
    private const val ADJACENT_MS = 250L
    private const val UNLIMITED = Long.MAX_VALUE / 4

    /** Playable length of a clip, same rule the exporter uses. */
    fun lengthMs(clip: VideoClip): Long =
        (clip.endMs - clip.startMs).takeIf { it > 0L }
            ?: clip.sourceDurationMs.takeIf { it > clip.startMs }?.minus(clip.startMs)
            ?: TimelineMath.MIN_CLIP_DURATION_MS

    fun prepare(transitions: List<Transition>, clips: List<VideoClip>, includeCrossFade: Boolean = true): Result {
        val byId = clips.associateBy { it.id }.toMutableMap()
        val fadeIn = HashMap<Long, Fade>()
        val fadeOut = HashMap<Long, Fade>()

        transitions
            .filter { it.type == TransitionType.FADE_THROUGH || (includeCrossFade && it.type == TransitionType.CROSS_FADE) }
            .forEach { t ->
                val a = byId[t.fromClipId] ?: return@forEach
                val b = byId[t.toClipId] ?: return@forEach
                val aLen = lengthMs(a)
                val bLen = lengthMs(b)
                val aEnd = a.timelineStartMs + aLen
                val bStart = b.timelineStartMs
                if (abs(bStart - aEnd) > ADJACENT_MS) return@forEach
                val d = t.durationMs.coerceIn(MIN_OVERLAP_MS, MAX_TRANSITION_MS).coerceAtMost(minOf(aLen, bLen))
                if (d < MIN_OVERLAP_MS) return@forEach

                if (t.type == TransitionType.CROSS_FADE) {
                    val aSourceEnd = a.startMs + aLen
                    val tail = if (a.sourceDurationMs >= STILL_SOURCE_MS) UNLIMITED else (a.sourceDurationMs - aSourceEnd).coerceAtLeast(0L)
                    val head = if (b.sourceDurationMs >= STILL_SOURCE_MS) UNLIMITED else b.startMs
                    val w = minOf(d, 2 * tail, 2 * head)
                    if (w >= MIN_OVERLAP_MS) {
                        val h = w / 2
                        byId[a.id] = a.copy(endMs = aSourceEnd + h)
                        byId[b.id] =
                            if (b.sourceDurationMs >= STILL_SOURCE_MS) b.copy(timelineStartMs = bStart - h, endMs = b.startMs + bLen + h)
                            else b.copy(timelineStartMs = bStart - h, startMs = b.startMs - h, endMs = b.startMs + bLen)
                        fadeIn[b.id] = Fade(bStart - h, bStart + h)
                        return@forEach
                    }
                }
                // Dip to black (also the fallback for a cross-fade without enough source material).
                val half = d / 2
                fadeOut[a.id] = Fade(aEnd - half, aEnd)
                fadeIn[b.id] = Fade(bStart, bStart + (d - half))
            }

        val fades = (fadeIn.keys + fadeOut.keys).associateWith { ClipFades(fadeIn[it], fadeOut[it]) }
        return Result(clips.map { byId[it.id] ?: it }, fades)
    }
}
