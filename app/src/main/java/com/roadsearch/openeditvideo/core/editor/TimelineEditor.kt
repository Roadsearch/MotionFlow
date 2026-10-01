package com.roadsearch.openeditvideo.core.editor

import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.VideoClip

/** Pure, deterministic timeline operations. UI/ViewModel are consumers of this engine. */
class TimelineEditor(private val minDurationMs: Long = TimelineMath.MIN_CLIP_DURATION_MS) {
    fun split(clips: List<VideoClip>, clipId: Long, timelinePositionMs: Long): List<VideoClip> {
        val clip = clips.firstOrNull { it.id == clipId } ?: return clips
        val end = TimelineMath.end(clip, clip.sourceDurationMs.coerceAtLeast(clip.startMs + minDurationMs))
        val offset = timelinePositionMs - clip.timelineStartMs
        val sourceSplit = clip.startMs + offset
        if (sourceSplit < clip.startMs + minDurationMs || sourceSplit > end - minDurationMs) return clips
        val left = clip.copy(id = clip.id, endMs = sourceSplit)
        val right = clip.copy(
            id = clip.id xor Long.MIN_VALUE,
            startMs = sourceSplit,
            timelineStartMs = timelinePositionMs,
        )
        return clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) }
    }

    /** Ripple deletes on one track without double-shifting when several clips are selected. */
    fun rippleDelete(clips: List<VideoClip>, ids: Set<Long>, track: Int = 0): List<VideoClip> {
        if (ids.isEmpty()) return clips
        val removed = clips
            .filter { it.id in ids && it.track == track }
            .sortedBy { it.timelineStartMs }
        if (removed.isEmpty()) return clips

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

    fun move(clip: VideoClip, deltaMs: Long, peers: List<VideoClip>): VideoClip =
        TimelineMath.move(clip, deltaMs, peers.filter { it.id != clip.id }, clip.sourceDurationMs.coerceAtLeast(clip.startMs + minDurationMs))
}
