package com.roadsearch.openeditvideo.core.editor

import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.AnimatedProperty
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.effectiveAnimation
import com.roadsearch.openeditvideo.model.end
import com.roadsearch.openeditvideo.model.keyframes
import com.roadsearch.openeditvideo.model.neutralValue
import com.roadsearch.openeditvideo.model.normalized
import com.roadsearch.openeditvideo.model.sanitize
import com.roadsearch.openeditvideo.model.selectedClip
import com.roadsearch.openeditvideo.model.value
import com.roadsearch.openeditvideo.model.withKeyframes
import com.roadsearch.openeditvideo.model.withStaticValue

/**
 * Keyframe commands for the selected clip, as pure functions of the editor state.
 *
 * Conventions:
 * - Keyframes live in the clip's SOURCE time (`timelineMs - clip.timelineStartMs + clip.startMs`), so they follow the
 *   footage when the clip is moved or trimmed.
 * - Every command touches exactly one property lane unless its name says "AllProperties".
 * - A command that would change nothing returns null, so callers never record an empty undo step.
 */
object KeyframeEditor {

    /** Playhead position in the clip's source time, or null when the playhead is outside the visible (trimmed) clip. */
    fun localTimeAt(clip: VideoClip, positionMs: Long, fallbackDurationMs: Long): Long? {
        val local = positionMs - clip.timelineStartMs + clip.startMs
        val end = maxOf(clip.end(fallbackDurationMs), clip.startMs)
        return local.takeIf { it in clip.startMs..end }
    }

    /** Timeline position at which the clip shows source time [localMs]. */
    fun timelinePositionOf(clip: VideoClip, localMs: Long): Long = clip.timelineStartMs + localMs - clip.startMs

    /** What the keyframe controls need to know about one property of the selected clip. */
    data class PropertyInfo(
        val hasClip: Boolean,
        val playheadOnClip: Boolean,
        val keyframeCount: Int,
        val keyAtPlayhead: Boolean,
        /** Timeline position of the closest keyframe before the playhead, inside the visible clip. */
        val previousKeyframeMs: Long?,
        /** Timeline position of the closest keyframe after the playhead, inside the visible clip. */
        val nextKeyframeMs: Long?,
        /** Value shown at the playhead (animated, or static when the lane has no keyframe). */
        val value: Float,
        /** Keyframe whose curve drives the segment around the playhead; null with fewer than two keyframes. */
        val segmentStart: AnimatedKeyframe?,
    ) {
        val canAdd: Boolean get() = hasClip && playheadOnClip && !keyAtPlayhead
        val canRemove: Boolean get() = keyAtPlayhead
        val canGoPrevious: Boolean get() = previousKeyframeMs != null
        val canGoNext: Boolean get() = nextKeyframeMs != null
    }

    fun info(state: EditorUiState, property: AnimatedProperty): PropertyInfo {
        val clip = state.selectedClip()
            ?: return PropertyInfo(false, false, 0, false, null, null, property.neutralValue, null)
        val animation = clip.effectiveAnimation()
        val lane = animation.keyframes(property).normalized()
        val end = maxOf(clip.end(state.durationMs), clip.startMs)
        val rawLocal = state.positionMs - clip.timelineStartMs + clip.startMs
        val local = rawLocal.coerceIn(clip.startMs, end)
        val visible = lane.filter { it.timeMs in clip.startMs..end }
        val index = lane.indexOfLast { it.timeMs <= rawLocal }
        val segmentStart = when {
            lane.size < 2 -> null
            index < 0 -> lane.first()
            index >= lane.lastIndex -> lane[lane.lastIndex - 1]
            else -> lane[index]
        }
        return PropertyInfo(
            hasClip = true,
            playheadOnClip = rawLocal in clip.startMs..end,
            keyframeCount = lane.size,
            keyAtPlayhead = visible.any { it.timeMs == rawLocal },
            previousKeyframeMs = visible.lastOrNull { it.timeMs < rawLocal }?.let { timelinePositionOf(clip, it.timeMs) },
            nextKeyframeMs = visible.firstOrNull { it.timeMs > rawLocal }?.let { timelinePositionOf(clip, it.timeMs) },
            value = animation.value(property, local),
            segmentStart = segmentStart,
        )
    }

    /** "◆+": adds a keyframe at the playhead for [property] only, holding the value currently displayed. */
    fun addKeyframe(state: EditorUiState, property: AnimatedProperty): EditorUiState? {
        val clip = state.selectedClip() ?: return null
        val local = localTimeAt(clip, state.positionMs, state.durationMs) ?: return null
        val animation = clip.effectiveAnimation()
        val lane = animation.keyframes(property)
        if (lane.any { it.timeMs == local }) return null
        val key = AnimatedKeyframe(local, animation.value(property, local), state.easing)
        return state.withAnimation(clip.id, animation.withKeyframes(property, (lane + key).normalized()))
    }

    /** Convenience for the "add keyframe" shortcuts that have no active property: every lane that lacks one gets a key. */
    fun addKeyframeOnAllProperties(state: EditorUiState): EditorUiState? {
        var result = state
        var changed = false
        for (property in AnimatedProperty.values()) {
            addKeyframe(result, property)?.let {
                result = it
                changed = true
            }
        }
        return result.takeIf { changed }
    }

    /**
     * "◆−": removes the keyframe of [property] sitting exactly under the playhead; all other keyframes and
     * properties are kept. When it was the last keyframe, the property keeps that keyframe's value as a static value.
     */
    fun removeKeyframe(state: EditorUiState, property: AnimatedProperty): EditorUiState? {
        val clip = state.selectedClip() ?: return null
        val local = localTimeAt(clip, state.positionMs, state.durationMs) ?: return null
        val animation = clip.effectiveAnimation()
        val lane = animation.keyframes(property)
        val removed = lane.lastOrNull { it.timeMs == local } ?: return null
        val remaining = lane.filterNot { it.timeMs == local }
        var updated = animation.withKeyframes(property, remaining)
        if (remaining.isEmpty()) updated = updated.withStaticValue(property, removed.value)
        return state.withAnimation(clip.id, updated)
    }

    /** Removes the keyframes under the playhead on every property (each emptied lane keeps its value statically). */
    fun removeKeyframesOnAllProperties(state: EditorUiState): EditorUiState? {
        var result = state
        var changed = false
        for (property in AnimatedProperty.values()) {
            removeKeyframe(result, property)?.let {
                result = it
                changed = true
            }
        }
        return result.takeIf { changed }
    }

    /** Deletes every keyframe of one property; the property keeps the value it shows at the playhead. */
    fun clearKeyframes(state: EditorUiState, property: AnimatedProperty): EditorUiState? {
        val clip = state.selectedClip() ?: return null
        val animation = clip.effectiveAnimation()
        if (animation.keyframes(property).isEmpty()) return null
        val end = maxOf(clip.end(state.durationMs), clip.startMs)
        val local = (state.positionMs - clip.timelineStartMs + clip.startMs).coerceIn(clip.startMs, end)
        val shown = animation.value(property, local)
        return state.withAnimation(clip.id, animation.withKeyframes(property, emptyList()).withStaticValue(property, shown))
    }

    /** Deletes the keyframes of every property; each property keeps the value it shows at the playhead. */
    fun clearAllKeyframes(state: EditorUiState): EditorUiState? {
        var result = state
        var changed = false
        for (property in AnimatedProperty.values()) {
            clearKeyframes(result, property)?.let {
                result = it
                changed = true
            }
        }
        // A legacy all-properties list is merged into every lane by effectiveAnimation(), so clearing the lanes retires it too.
        return result.takeIf { changed }
    }

    /**
     * Sets [property] to [value] following the hybrid CapCut / Alight Motion rules:
     * - lane already has keyframes: update the keyframe under the playhead (curve preserved) or create one there;
     * - lane has none and [EditorUiState.autoKeyframe] is on: create the first keyframe;
     * - lane has none and auto-keyframe is off: change the static value, no keyframe.
     * Repeated calls at the same playhead position only ever update one keyframe, so a continuous drag cannot
     * scatter keyframes.
     */
    fun editValue(state: EditorUiState, property: AnimatedProperty, value: Float): EditorUiState? {
        if (!value.isFinite()) return null
        val clip = state.selectedClip() ?: return null
        val sanitized = property.sanitize(value)
        val animation = clip.effectiveAnimation()
        val lane = animation.keyframes(property)
        val updated: TransformAnimation = if (lane.isEmpty() && !state.autoKeyframe) {
            animation.withStaticValue(property, sanitized)
        } else {
            val local = localTimeAt(clip, state.positionMs, state.durationMs) ?: return null
            val existing = lane.lastOrNull { it.timeMs == local }
            val key = existing?.copy(value = sanitized) ?: AnimatedKeyframe(local, sanitized, state.easing)
            animation.withKeyframes(property, (lane.filterNot { it.timeMs == local } + key).normalized())
        }
        val next = state.withAnimation(clip.id, updated)
        return next.takeIf { it != state }
    }

    /** Timeline position of the previous/next keyframe of [property] inside the visible clip, or null. */
    fun adjacentKeyframePosition(state: EditorUiState, property: AnimatedProperty, forward: Boolean): Long? {
        val info = info(state, property)
        return if (forward) info.nextKeyframeMs else info.previousKeyframeMs
    }

    /** Stores the interpolation of the segment starting at the keyframe at [keyframeTimeMs]. */
    fun setSegmentCurve(
        state: EditorUiState,
        property: AnimatedProperty,
        keyframeTimeMs: Long,
        easing: Easing,
        curveX1: Float,
        curveY1: Float,
        curveX2: Float,
        curveY2: Float,
        alsoUseAsDefault: Boolean = false,
    ): EditorUiState? {
        if (!(curveX1.isFinite() && curveY1.isFinite() && curveX2.isFinite() && curveY2.isFinite())) return null
        val clip = state.selectedClip() ?: return null
        val animation = clip.effectiveAnimation()
        val lane = animation.keyframes(property)
        if (lane.none { it.timeMs == keyframeTimeMs }) return null
        val updatedLane = lane.map { key ->
            if (key.timeMs == keyframeTimeMs) {
                key.copy(
                    easingToNext = easing,
                    curveX1 = curveX1.coerceIn(0f, 1f),
                    curveY1 = curveY1.coerceIn(-2f, 3f),
                    curveX2 = curveX2.coerceIn(0f, 1f),
                    curveY2 = curveY2.coerceIn(-2f, 3f),
                )
            } else key
        }
        val withCurve = state.withAnimation(clip.id, animation.withKeyframes(property, updatedLane))
        val next = if (alsoUseAsDefault) withCurve.copy(easing = easing) else withCurve
        return next.takeIf { it != state }
    }

    private fun EditorUiState.withAnimation(clipId: Long, animation: TransformAnimation): EditorUiState =
        copy(clips = clips.map { clip ->
            // Writing the per-property lanes also retires the legacy all-properties list (already merged into them).
            if (clip.id == clipId) clip.copy(animation = animation, keyframes = emptyList()) else clip
        })
}
