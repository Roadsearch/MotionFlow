package com.roadsearch.openeditvideo.core.editor

import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.VideoClip

/** Pure, deterministic timeline operations. UI/ViewModel are consumers of this engine. */
class TimelineEditor(private val minDurationMs: Long = TimelineMath.MIN_CLIP_DURATION_MS) {

    /**
     * Cuts [clipId] at [timelinePositionMs]. The left part keeps the original id, the right part receives [newId]
     * (callers pass an id that is unique in the project). The clips are returned untouched (same instance) when the
     * cut point is not at least [minDurationMs] away from both edges of the clip, or when [newId] is already used.
     */
    fun split(clips: List<VideoClip>, clipId: Long, timelinePositionMs: Long, newId: Long = System.nanoTime()): List<VideoClip> {
        val clip = clips.firstOrNull { it.id == clipId } ?: return clips
        if (newId == clipId || clips.any { it.id == newId }) return clips
        val end = TimelineMath.end(clip, clip.sourceDurationMs.coerceAtLeast(clip.startMs + minDurationMs))
        val offset = timelinePositionMs - clip.timelineStartMs
        val sourceSplit = clip.startMs + offset
        if (sourceSplit < clip.startMs + minDurationMs || sourceSplit > end - minDurationMs) return clips
        val left = clip.copy(endMs = sourceSplit)
        val right = clip.copy(
            id = newId,
            startMs = sourceSplit,
            timelineStartMs = timelinePositionMs,
        )
        return clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) }
    }

    /**
     * Ripple deletes on one track without double-shifting when several clips are selected. Every clip in [ids] is
     * removed, whatever its track; only the clips of [track] that sit after a removed clip close the gap.
     */
    fun rippleDelete(clips: List<VideoClip>, ids: Set<Long>, track: Int = 0): List<VideoClip> {
        if (ids.isEmpty()) return clips
        val removed = clips
            .filter { it.id in ids && it.track == track }
            .sortedBy { it.timelineStartMs }
        if (removed.isEmpty()) return clips.filterNot { it.id in ids }

        val removedWithEnd = removed.map { clip ->
            val duration = TimelineMath.duration(
                clip,
                clip.sourceDurationMs.coerceAtLeast(clip.startMs + minDurationMs),
            )
            clip.timelineStartMs to duration
        }

        return clips
            .filterNot { it.id in ids }
            .map { clip ->
                if (clip.track != track) clip
                else {
                    val shift = removedWithEnd
                        .filter { (start, _) -> start < clip.timelineStartMs }
                        .sumOf { (_, duration) -> duration }
                    clip.copy(timelineStartMs = (clip.timelineStartMs - shift).coerceAtLeast(0L))
                }
            }
    }

    /**
     * Trims the left edge of [clip] to the playhead. Null when nothing can change: the clip is too short to be
     * trimmed, or the playhead is not after its current start.
     */
    fun trimStartAt(clip: VideoClip, positionMs: Long, fallbackDurationMs: Long): VideoClip? {
        val end = TimelineMath.end(clip, fallbackDurationMs)
        val maxStart = end - minDurationMs
        if (maxStart <= clip.startMs) return null
        val newStart = (positionMs - clip.timelineStartMs + clip.startMs).coerceIn(clip.startMs, maxStart)
        if (newStart == clip.startMs) return null
        return clip.copy(
            startMs = newStart,
            timelineStartMs = (clip.timelineStartMs + (newStart - clip.startMs)).coerceAtLeast(0L),
        )
    }

    /** Trims the right edge of [clip] to the playhead. Null when nothing can change (see [trimStartAt]). */
    fun trimEndAt(clip: VideoClip, positionMs: Long, fallbackDurationMs: Long): VideoClip? {
        val end = TimelineMath.end(clip, fallbackDurationMs)
        val minEnd = clip.startMs + minDurationMs
        if (end <= minEnd) return null
        val newEnd = (positionMs - clip.timelineStartMs + clip.startMs).coerceIn(minEnd, end)
        if (newEnd >= end) return null
        return clip.copy(endMs = newEnd)
    }

    fun move(clip: VideoClip, deltaMs: Long, peers: List<VideoClip>): VideoClip =
        TimelineMath.move(clip, deltaMs, peers.filter { it.id != clip.id }, clip.sourceDurationMs.coerceAtLeast(clip.startMs + minDurationMs))
}
