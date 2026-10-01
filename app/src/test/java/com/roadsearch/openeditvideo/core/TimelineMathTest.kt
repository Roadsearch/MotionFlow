package com.roadsearch.openeditvideo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineMathTest {
    @Test fun moveWindowSnapsToNeighborEnd() {
        val current = TimelineMath.ClipWindow(2_000L, 1_000L)
        val neighbor = TimelineMath.ClipWindow(4_000L, 1_000L)
        val moved = TimelineMath.moveWindow(current, 930L, listOf(neighbor))
        assertEquals(3_000L, moved.timelineStartMs)
    }

    @Test fun snapStartSnapsClipStartToZero() {
        val current = TimelineMath.ClipWindow(100L, 1_000L)
        val moved = TimelineMath.moveWindow(current, -80L, emptyList())
        assertEquals(0L, moved.timelineStartMs)
    }
}
