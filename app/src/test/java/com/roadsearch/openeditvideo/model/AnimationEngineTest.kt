package com.roadsearch.openeditvideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Interpolation engine: segments, boundaries, presets, independent lanes, close or duplicated keyframes. */
class AnimationEngineTest {
    private fun two(easing: Easing, from: Float = 0f, to: Float = 100f) =
        listOf(AnimatedKeyframe(0L, from, easing), AnimatedKeyframe(1_000L, to))

    @Test fun `two keyframes interpolate linearly`() {
        val keys = two(Easing.LINEAR)
        assertEquals(0f, keys.valueAt(0L, -1f), 0f)
        assertEquals(25f, keys.valueAt(250L, -1f), 0.001f)
        assertEquals(100f, keys.valueAt(1_000L, -1f), 0f)
    }

    @Test fun `a single keyframe produces no artificial movement`() {
        val keys = listOf(AnimatedKeyframe(500L, 7f, Easing.BOUNCE))
        listOf(0L, 499L, 500L, 501L, 99_999L).forEach { assertEquals(7f, keys.valueAt(it, -1f), 0f) }
    }

    @Test fun `no keyframe returns the static fallback`() {
        assertEquals(3.5f, emptyList<AnimatedKeyframe>().valueAt(123L, 3.5f), 0f)
    }

    @Test fun `values hold before the first and after the last keyframe`() {
        val keys = listOf(AnimatedKeyframe(100L, 10f), AnimatedKeyframe(200L, 20f))
        assertEquals(10f, keys.valueAt(-50L, -1f), 0f)
        assertEquals(10f, keys.valueAt(100L, -1f), 0f)
        assertEquals(20f, keys.valueAt(200L, -1f), 0f)
        assertEquals(20f, keys.valueAt(10_000L, -1f), 0f)
    }

    @Test fun `every preset starts and ends exactly on the keyframe values`() {
        Easing.values().forEach { easing ->
            val keys = two(easing, from = 10f, to = 30f)
            assertEquals("$easing start", 10f, keys.valueAt(0L, -1f), 0f)
            assertEquals("$easing end", 30f, keys.valueAt(1_000L, -1f), 0f)
        }
    }

    @Test fun `presets progress as expected at mid segment`() {
        assertEquals(25f, two(Easing.EASE_IN).valueAt(500L, 0f), 0.01f)
        assertEquals(75f, two(Easing.EASE_OUT).valueAt(500L, 0f), 0.01f)
        assertEquals(50f, two(Easing.EASE_IN_OUT).valueAt(500L, 0f), 0.01f)
        assertEquals(0f, two(Easing.HOLD).valueAt(999L, 0f), 0f)
        assertEquals(50f, two(Easing.STEPS).valueAt(500L, 0f), 0.01f)  // floor(0.5 * 8) / 8
        assertEquals(37.5f, two(Easing.STEPS).valueAt(400L, 0f), 0.01f) // floor(0.4 * 8) / 8 = 0.375
    }

    @Test fun `bounce and elastic overshoot or settle but land on the end value`() {
        val bounce = two(Easing.BOUNCE)
        assertTrue(bounce.valueAt(900L, 0f) in 90f..100f)
        val elastic = two(Easing.ELASTIC)
        assertTrue((0 until 1000 step 7).any { elastic.valueAt(it.toLong(), 0f) > 100f })
        assertEquals(100f, elastic.valueAt(1_000L, 0f), 0f)
    }

    @Test fun `cubic bezier with aligned handles is linear`() {
        val keys = listOf(AnimatedKeyframe(0L, 0f, Easing.CUBIC_BEZIER, 0f, 0f, 1f, 1f), AnimatedKeyframe(1_000L, 100f))
        listOf(100L, 250L, 500L, 900L).forEach { assertEquals(it / 10f, keys.valueAt(it, 0f), 0.2f) }
    }

    @Test fun `cubic bezier ease out handles front load the movement`() {
        val keys = listOf(AnimatedKeyframe(0L, 0f, Easing.CUBIC_BEZIER, 0f, 0.8f, 0.4f, 1f), AnimatedKeyframe(1_000L, 100f))
        assertTrue(keys.valueAt(250L, 0f) > 25f)
        assertTrue(keys.valueAt(250L, 0f) < keys.valueAt(500L, 0f))
    }

    @Test fun `the curve of a keyframe drives the segment to the NEXT keyframe only`() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f, Easing.HOLD),
            AnimatedKeyframe(1_000L, 100f, Easing.LINEAR),
            AnimatedKeyframe(2_000L, 200f),
        )
        assertEquals(0f, keys.valueAt(500L, -1f), 0f)       // segment 1 holds
        assertEquals(150f, keys.valueAt(1_500L, -1f), 0.01f) // segment 2 is linear
    }

    @Test fun `very close keyframes stay stable`() {
        val keys = listOf(AnimatedKeyframe(1_000L, 0f), AnimatedKeyframe(1_001L, 10f), AnimatedKeyframe(2_000L, 20f))
        assertEquals(0f, keys.valueAt(1_000L, -1f), 0f)
        assertEquals(10f, keys.valueAt(1_001L, -1f), 0f)
        assertTrue(keys.valueAt(1_500L, -1f) in 10f..20f)
        (900L..2_100L).forEach { assertTrue(keys.valueAt(it, -1f).isFinite()) }
    }

    @Test fun `unordered keyframes behave as if sorted`() {
        val shuffled = listOf(AnimatedKeyframe(1_000L, 100f), AnimatedKeyframe(0L, 0f), AnimatedKeyframe(500L, 10f))
        assertEquals(5f, shuffled.valueAt(250L, -1f), 0.01f)
        assertEquals(55f, shuffled.valueAt(750L, -1f), 0.01f)
    }

    @Test fun `duplicate times collapse to the last stored keyframe without division by zero`() {
        val keys = listOf(AnimatedKeyframe(0L, 0f), AnimatedKeyframe(500L, 1f), AnimatedKeyframe(500L, 50f), AnimatedKeyframe(1_000L, 100f))
        assertEquals(50f, keys.valueAt(500L, -1f), 0f)
        assertEquals(75f, keys.valueAt(750L, -1f), 0.01f)
        assertEquals(listOf(0L, 500L, 1_000L), keys.normalized().map { it.timeMs })
        assertEquals(50f, keys.normalized()[1].value, 0f)
    }

    @Test fun `properties are independent lanes with their own curves`() {
        val animation = TransformAnimation(
            x = listOf(AnimatedKeyframe(0L, 0f, Easing.HOLD), AnimatedKeyframe(1_000L, 100f)),
            scale = listOf(AnimatedKeyframe(0L, 1f, Easing.LINEAR), AnimatedKeyframe(1_000L, 3f)),
        )
        val at = animation.at(500L)
        assertEquals(0f, at.x, 0f)
        assertEquals(2f, at.scale, 0.001f)
        assertEquals(0f, at.y, 0f); assertEquals(0f, at.rotation, 0f); assertEquals(1f, at.opacity, 0f)
        val edited = animation.withKeyframes(AnimatedProperty.OPACITY, listOf(AnimatedKeyframe(0L, .2f)))
        assertEquals(animation.x, edited.x); assertEquals(animation.scale, edited.scale)
    }

    @Test fun `static values apply only to properties without keyframes`() {
        val animation = TransformAnimation(
            x = listOf(AnimatedKeyframe(0L, 10f), AnimatedKeyframe(1_000L, 20f)),
            baseX = 999f, baseY = 5f, baseScale = 2f, baseRotation = 30f, baseOpacity = .4f,
        )
        val at = animation.at(0L)
        assertEquals(10f, at.x, 0f)
        assertEquals(5f, at.y, 0f); assertEquals(2f, at.scale, 0f); assertEquals(30f, at.rotation, 0f); assertEquals(.4f, at.opacity, 0f)
        assertTrue(animation.hasTransform()); assertTrue(animation.hasOpacity()); assertTrue(!animation.isIdentity())
        assertTrue(TransformAnimation().isIdentity())
    }

    @Test fun `neutral values and sanitising match the renderer`() {
        assertEquals(1f, AnimatedProperty.SCALE.neutralValue, 0f)
        assertEquals(1f, AnimatedProperty.OPACITY.neutralValue, 0f)
        assertEquals(0f, AnimatedProperty.ROTATION.neutralValue, 0f)
        assertEquals(0.01f, AnimatedProperty.SCALE.sanitize(-4f), 0f)
        assertEquals(1f, AnimatedProperty.OPACITY.sanitize(2f), 0f)
        assertEquals(-2500f, AnimatedProperty.X.sanitize(-2500f), 0f)
    }
}
