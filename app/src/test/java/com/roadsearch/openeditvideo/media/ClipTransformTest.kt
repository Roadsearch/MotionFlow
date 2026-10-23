package com.roadsearch.openeditvideo.media

import android.net.Uri
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.EffectSettings
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.keyframesAt
import com.roadsearch.openeditvideo.scene.SceneGraph
import org.junit.Assert.assertEquals
import org.junit.Test

/** The preview and the export must read the very same transform for the same instant of the same footage. */
class ClipTransformTest {
    // Source 1 000..5 000 ms shown at 10 000 ms on the timeline: timeline = source + 9 000.
    private fun clip(animation: TransformAnimation, parentId: Long? = null, effects: EffectSettings = EffectSettings()) = VideoClip(
        id = 1L, uri = Uri.parse("content://video/1"), name = "c", startMs = 1_000L, endMs = 5_000L,
        sourceDurationMs = 8_000L, timelineStartMs = 10_000L, animation = animation, parentId = parentId, effects = effects,
    )

    private val moving = TransformAnimation(
        x = listOf(AnimatedKeyframe(1_000L, 0f), AnimatedKeyframe(5_000L, 540f)),
        scale = listOf(AnimatedKeyframe(1_000L, 1f), AnimatedKeyframe(5_000L, 3f)),
        rotation = listOf(AnimatedKeyframe(1_000L, 0f), AnimatedKeyframe(5_000L, 80f)),
        opacity = listOf(AnimatedKeyframe(1_000L, 1f), AnimatedKeyframe(5_000L, 0f)),
    )

    @Test fun `source time maps to timeline time through the trim and the clip position`() {
        val c = clip(moving)
        assertEquals(10_000L, ClipTransform.timelineTimeOfSource(c, 1_000L))
        assertEquals(12_500L, ClipTransform.timelineTimeOfSource(c, 3_500L))
        assertEquals(10_000L, ClipTransform.timelineTimeOfSource(c, 0L)) // before the trim never goes before the clip start
    }

    @Test fun `preview lookup by source time equals export lookup by timeline time`() {
        val c = clip(moving)
        for (source in 1_000L..5_000L step 250L) {
            val preview = ClipTransform.resolveAtSource(c, emptyList(), source)
            val export = ClipTransform.resolveAtTimeline(c, emptyList(), source + 9_000L)
            assertEquals(preview, export.copy(timeMs = preview.timeMs))
        }
    }

    @Test fun `the transform follows the footage after the clip is moved`() {
        val moved = clip(moving).copy(timelineStartMs = 30_000L)
        assertEquals(
            ClipTransform.resolveAtTimeline(clip(moving), emptyList(), 12_000L).x,
            ClipTransform.resolveAtTimeline(moved, emptyList(), 32_000L).x, 0f,
        )
    }

    @Test fun `export overlay applies position scale rotation and opacity exactly once`() {
        val overlay = ClipTransform.overlayAt(clip(moving), emptyList(), globalMs = 12_000L) // halfway
        assertEquals(270f / 540f, overlay.anchorX, 0.001f)
        assertEquals(0f, overlay.anchorY, 0.001f)
        assertEquals(2f, overlay.scale, 0.001f)
        assertEquals(40f, overlay.rotationDegrees, 0.001f)
        assertEquals(0.5f, overlay.alpha, 0.001f)
    }

    @Test fun `the static effect rotation is not added a second time by the compositor`() {
        val overlay = ClipTransform.overlayAt(clip(moving, effects = EffectSettings(rotation = 30f)), emptyList(), 12_000L)
        assertEquals(40f, overlay.rotationDegrees, 0.001f)
    }

    @Test fun `opacity combines the keyframes the parent and the transition fade once each`() {
        val parent = NullObject(9L, "p", null, TransformAnimation(baseOpacity = 0.5f))
        val overlay = ClipTransform.overlayAt(clip(moving, parentId = 9L), listOf(parent), globalMs = 12_000L, fadeFactor = 0.5f)
        assertEquals(0.5f * 0.5f * 0.5f, overlay.alpha, 0.001f)
    }

    @Test fun `parent controllers move and scale the child as the scene graph says`() {
        val parent = NullObject(9L, "p", null, TransformAnimation(baseX = 100f, baseScale = 2f))
        val c = clip(TransformAnimation(baseX = 50f), parentId = 9L)
        val direct = SceneGraph.resolve(c.keyframesAt(11_000L), 9L, listOf(parent), 11_000L)
        val overlay = ClipTransform.overlayAt(c, listOf(parent), 11_000L)
        assertEquals(200f, direct.x, 0.001f) // 100 + 2 * 50
        assertEquals(direct.x / 540f, overlay.anchorX, 0.001f)
        assertEquals(2f, overlay.scale, 0.001f)
    }

    @Test fun `static values drive an unanimated clip`() {
        val overlay = ClipTransform.overlayAt(clip(TransformAnimation(baseY = -480f, baseScale = 1.5f, baseOpacity = 0.25f)), emptyList(), 11_000L)
        assertEquals(0.5f, overlay.anchorY, 0.001f)
        assertEquals(1.5f, overlay.scale, 0.001f)
        assertEquals(0.25f, overlay.alpha, 0.001f)
    }

    @Test fun `overlay values are clamped to what the compositor accepts`() {
        val overlay = ClipTransform.overlayAt(clip(TransformAnimation(baseX = 5_000f, baseScale = 99f)), emptyList(), 11_000L)
        assertEquals(1f, overlay.anchorX, 0f)
        assertEquals(20f, overlay.scale, 0f)
    }
}
