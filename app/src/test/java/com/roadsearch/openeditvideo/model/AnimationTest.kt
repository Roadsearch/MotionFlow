package com.roadsearch.openeditvideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTest {
    @Test fun linearInterpolation() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f),
            AnimatedKeyframe(1_000L, 100f),
        )
        assertEquals(50f, keys.valueAt(500L, -1f), 0.001f)
    }

    @Test fun easeInIsStoredOnTheStartingKeyframe() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f, Easing.EASE_IN),
            AnimatedKeyframe(1_000L, 100f),
        )
        assertEquals(25f, keys.valueAt(500L, -1f), 0.001f)
    }

    @Test fun easeOutChangesProgression() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f, Easing.EASE_OUT),
            AnimatedKeyframe(1_000L, 100f),
        )
        assertEquals(75f, keys.valueAt(500L, -1f), 0.001f)
    }

    @Test fun cubicBezierCanRepresentSmoothEase() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f, Easing.CUBIC_BEZIER, 0.25f, 0.1f, 0.25f, 1f),
            AnimatedKeyframe(1_000L, 100f),
        )
        assertEquals(80.2f, keys.valueAt(500L, -1f), 1.0f)
    }

    @Test fun holdKeepsTheStartValueUntilTheEnd() {
        val keys = listOf(
            AnimatedKeyframe(0L, 10f, Easing.HOLD),
            AnimatedKeyframe(1_000L, 20f),
        )
        assertEquals(10f, keys.valueAt(500L, -1f), 0.001f)
        assertEquals(20f, keys.valueAt(1_000L, -1f), 0.001f)
    }

    @Test fun stepsProducesDiscreteProgress() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f, Easing.STEPS),
            AnimatedKeyframe(1_000L, 100f),
        )
        assertEquals(25f, keys.valueAt(300L, -1f), 0.001f)
    }

    @Test fun clampsOutsideRange() {
        val keys = listOf(AnimatedKeyframe(100L, 10f), AnimatedKeyframe(200L, 20f))
        assertEquals(10f, keys.valueAt(0L, -1f), 0.001f)
        assertEquals(20f, keys.valueAt(300L, -1f), 0.001f)
    }

    @Test fun transformPropertiesHaveIndependentKeyframeLanes() {
        val animation = TransformAnimation(
            x = listOf(AnimatedKeyframe(0L, 0f), AnimatedKeyframe(1_000L, 50f)),
            scale = listOf(AnimatedKeyframe(250L, 1f), AnimatedKeyframe(750L, 2f)),
        )
        assertEquals(2, animation.keyframes(AnimatedProperty.X).size)
        assertEquals(2, animation.keyframes(AnimatedProperty.SCALE).size)
        assertTrue(animation.keyframes(AnimatedProperty.OPACITY).isEmpty())
        assertEquals(1, animation.withKeyframes(AnimatedProperty.OPACITY, listOf(AnimatedKeyframe(0L, 1f))).opacity.size)
    }
}
