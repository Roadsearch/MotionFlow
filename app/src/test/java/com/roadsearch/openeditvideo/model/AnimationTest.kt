package com.roadsearch.openeditvideo.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationTest {
    @Test fun linearInterpolation() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f),
            AnimatedKeyframe(1_000L, 100f, Easing.LINEAR),
        )
        assertEquals(50f, keys.valueAt(500L, -1f), 0.001f)
    }

    @Test fun easeInChangesProgression() {
        val keys = listOf(
            AnimatedKeyframe(0L, 0f),
            AnimatedKeyframe(1_000L, 100f, Easing.EASE_IN),
        )
        assertEquals(25f, keys.valueAt(500L, -1f), 0.001f)
    }

    @Test fun clampsOutsideRange() {
        val keys = listOf(AnimatedKeyframe(100L, 10f), AnimatedKeyframe(200L, 20f))
        assertEquals(10f, keys.valueAt(0L, -1f), 0.001f)
        assertEquals(20f, keys.valueAt(300L, -1f), 0.001f)
    }
}
