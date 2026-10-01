package com.roadsearch.openeditvideo.core

import com.roadsearch.openeditvideo.model.VideoClip
import kotlin.math.abs

object TimelineMath {
    const val MIN_CLIP_DURATION_MS = 250L
    const val SNAP_THRESHOLD_MS = 120L

    data class ClipWindow(val timelineStartMs: Long, val durationMs: Long)

    fun duration(clip: VideoClip, fallbackDurationMs: Long): Long =
        (end(clip, fallbackDurationMs) - clip.startMs).coerceAtLeast(MIN_CLIP_DURATION_MS)

    fun end(clip: VideoClip, fallbackDurationMs: Long): Long =
        if (clip.endMs > clip.startMs) clip.endMs
        else (clip.sourceDurationMs.takeIf { it > clip.startMs }
            ?: (clip.startMs + fallbackDurationMs.coerceAtLeast(MIN_CLIP_DURATION_MS)))

    fun snapStart(
        positionMs: Long,
        clipDurationMs: Long,
        others: List<VideoClip>,
        fallbackDurationMs: Long,
    ): Long = snapStart(
        positionMs = positionMs,
        clipDurationMs = clipDurationMs,
        others = others.map { ClipWindow(it.timelineStartMs, duration(it, fallbackDurationMs)) },
    )

    fun snapStart(positionMs: Long, clipDurationMs: Long, others: List<ClipWindow>): Long {
        val points = buildList {
            add(0L)
            others.forEach {
                add(it.timelineStartMs)
                add(it.timelineStartMs + it.durationMs.coerceAtLeast(MIN_CLIP_DURATION_MS))
            }
        }
        val candidates = points.flatMap { listOf(it, it - clipDurationMs) }
        return candidates.minByOrNull { abs(it - positionMs) }
            ?.takeIf { abs(it - positionMs) <= SNAP_THRESHOLD_MS }
            ?.coerceAtLeast(0L)
            ?: positionMs.coerceAtLeast(0L)
    }

    fun clampTrimStart(clip: VideoClip, deltaMs: Long, fallbackDurationMs: Long): VideoClip {
        val endMs = end(clip, fallbackDurationMs)
        val maxStart = (endMs - MIN_CLIP_DURATION_MS).coerceAtLeast(clip.startMs)
        val oldStart = clip.startMs
        val newStart = (oldStart + deltaMs).coerceIn(0L, maxStart)
        return clip.copy(
            startMs = newStart,
            timelineStartMs = (clip.timelineStartMs + (newStart - oldStart)).coerceAtLeast(0L),
        )
    }

    fun clampTrimEnd(clip: VideoClip, deltaMs: Long, fallbackDurationMs: Long): VideoClip {
        val currentEnd = end(clip, fallbackDurationMs)
        val sourceLimit = clip.sourceDurationMs.takeIf { it > 0L }?.coerceAtLeast(clip.startMs + MIN_CLIP_DURATION_MS)
        val maxEnd = sourceLimit ?: Long.MAX_VALUE
        val newEnd = (currentEnd + deltaMs)
            .coerceAtLeast(clip.startMs + MIN_CLIP_DURATION_MS)
            .coerceAtMost(maxEnd)
        return clip.copy(endMs = newEnd)
    }

    /** Snap a playhead/seek position to the nearest marker, when close enough. */
    fun snapToMarkers(positionMs: Long, markerPositions: List<Long>, thresholdMs: Long = SNAP_THRESHOLD_MS): Long {
        if (markerPositions.isEmpty()) return positionMs.coerceAtLeast(0L)
        val nearest = markerPositions.minByOrNull { abs(it - positionMs) } ?: return positionMs.coerceAtLeast(0L)
        return if (abs(nearest - positionMs) <= thresholdMs) nearest else positionMs.coerceAtLeast(0L)
    }

    /** Move without overlapping another clip on the same track; gaps remain allowed. */
    fun move(
        clip: VideoClip,
        deltaMs: Long,
        others: List<VideoClip>,
        fallbackDurationMs: Long,
    ): VideoClip {
        val clipDuration = duration(clip, fallbackDurationMs)
        val proposed = (clip.timelineStartMs + deltaMs).coerceAtLeast(0L)
        val snapped = snapStart(proposed, clipDuration, others, fallbackDurationMs)
        val peers = others.sortedBy { it.timelineStartMs }
        val previousEnd = peers
            .filter { it.timelineStartMs < clip.timelineStartMs }
            .maxOfOrNull { it.timelineStartMs + duration(it, fallbackDurationMs) }
            ?: 0L
        val nextStart = peers
            .firstOrNull { it.timelineStartMs > clip.timelineStartMs }
            ?.timelineStartMs
            ?: Long.MAX_VALUE
        val minStart = previousEnd.coerceAtLeast(0L)
        val maxStart = if (nextStart == Long.MAX_VALUE) Long.MAX_VALUE else (nextStart - clipDuration).coerceAtLeast(minStart)
        return clip.copy(timelineStartMs = snapped.coerceIn(minStart, maxStart))
    }

    fun moveWindow(window: ClipWindow, deltaMs: Long, others: List<ClipWindow>): ClipWindow = window.copy(
        timelineStartMs = snapStart(
            positionMs = (window.timelineStartMs + deltaMs).coerceAtLeast(0L),
            clipDurationMs = window.durationMs,
            others = others,
        )
    )
}
