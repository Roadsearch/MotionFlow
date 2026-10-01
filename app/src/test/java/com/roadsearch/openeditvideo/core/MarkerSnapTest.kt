package com.roadsearch.openeditvideo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerSnapTest {

    @Test
    fun `seek snaps to nearest marker within threshold`() {
        val markers = listOf(1_000L, 5_000L, 12_000L)
        assertEquals(5_000L, TimelineMath.snapToMarkers(4_950L, markers))
        assertEquals(1_000L, TimelineMath.snapToMarkers(1_119L, markers))
    }

    @Test
    fun `seek outside threshold stays untouched`() {
        val markers = listOf(1_000L, 5_000L)
        assertEquals(3_000L, TimelineMath.snapToMarkers(3_000L, markers))
    }

    @Test
    fun `empty marker list is a no-op`() {
        assertEquals(2_500L, TimelineMath.snapToMarkers(2_500L, emptyList()))
    }

    @Test
    fun `negative positions are clamped to zero`() {
        assertEquals(0L, TimelineMath.snapToMarkers(-500L, emptyList()))
    }
}
