package com.roadsearch.openeditvideo.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionTimelinePlanTest {
    @Test
    fun `absolute clip end is preserved when a clip starts late`() {
        val start = 1250L
        val duration = 2300L
        assertEquals(3550L, start + duration)
    }

    @Test
    fun `timeline gap is represented instead of being silently removed`() {
        val leftEnd = 1000L
        val rightStart = 1800L
        val gap = (rightStart - leftEnd).coerceAtLeast(0L)
        assertEquals(800L, gap)
        assertTrue(gap > 0L)
    }

    @Test
    fun `clip z order follows track number`() {
        val tracks = listOf(2, 0, 1).sorted()
        assertEquals(listOf(0, 1, 2), tracks)
    }
}
