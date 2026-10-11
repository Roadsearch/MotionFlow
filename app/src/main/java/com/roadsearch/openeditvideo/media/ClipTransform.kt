package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.keyframesAt
import com.roadsearch.openeditvideo.scene.SceneGraph

/**
 * The single definition of "where does this clip sit at time T", shared by the preview and the export.
 *
 * Preview (ExoPlayer effects) and export (Media3 compositor) both resolve a clip through [resolveAtTimeline]:
 * own keyframes (or static values) composed with every parent controller. Each stage applies the result once.
 * On export that stage is the compositor ([overlayAt]); the per-clip effect chain must not apply it again.
 */
object ClipTransform {
    /** Timeline time at which the clip shows source time [sourceMs] (source time = trimmed footage time). */
    fun timelineTimeOfSource(clip: VideoClip, sourceMs: Long): Long =
        clip.timelineStartMs + (sourceMs - clip.startMs).coerceAtLeast(0L)

    /** Transform of [clip] at [timelineMs], parents included. */
    fun resolveAtTimeline(clip: VideoClip, nullObjects: List<NullObject>, timelineMs: Long): Keyframe =
        SceneGraph.resolve(clip.keyframesAt(timelineMs), clip.parentId, nullObjects, timelineMs)

    /** Same as [resolveAtTimeline], addressed by the clip's source time (what Media3 effects receive). */
    fun resolveAtSource(clip: VideoClip, nullObjects: List<NullObject>, sourceMs: Long): Keyframe =
        resolveAtTimeline(clip, nullObjects, timelineTimeOfSource(clip, sourceMs))

    /** Placement of a clip inside the export canvas, in the units of Media3's StaticOverlaySettings. */
    data class Overlay(
        val anchorX: Float,
        val anchorY: Float,
        val scale: Float,
        val rotationDegrees: Float,
        val alpha: Float,
    )

    /**
     * What the export compositor applies to [clip] at [globalMs]: position, uniform scale, keyframed rotation and
     * opacity multiplied by the transition fade [fadeFactor]. The clip's static EffectSettings rotation is NOT part of
     * it: it is applied per clip by the GPU effect chain, exactly like in the preview.
     */
    fun overlayAt(
        clip: VideoClip,
        nullObjects: List<NullObject>,
        globalMs: Long,
        fadeFactor: Float = 1f,
        designWidth: Float = 1080f,
        designHeight: Float = 1920f,
    ): Overlay {
        val transform = resolveAtTimeline(clip, nullObjects, globalMs)
        return Overlay(
            anchorX = (transform.x / (designWidth / 2f)).coerceIn(-1f, 1f),
            anchorY = (-transform.y / (designHeight / 2f)).coerceIn(-1f, 1f),
            scale = transform.scale.coerceIn(0.01f, 20f),
            rotationDegrees = transform.rotation,
            alpha = transform.opacity.coerceIn(0f, 1f) * fadeFactor,
        )
    }
}
