package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransitionType
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionRenderPlanTest {
    private fun clip(id: Long, start: Long, end: Long, source: Long, timeline: Long) =
        VideoClip(id, android.net.Uri.parse("file:///c$id.mp4"), "c$id", startMs = start, endMs = end, sourceDurationMs = source, timelineStartMs = timeline)

    @Test fun `fade through dips both clips around the cut`() {
        val a = clip(1, 0, 2000, 2000, 0)
        val b = clip(2, 0, 2000, 2000, 2000)
        val r = TransitionRenderPlan.prepare(listOf(Transition(1, 1, 2, 1000, TransitionType.FADE_THROUGH)), listOf(a, b))
        assertEquals(TransitionRenderPlan.Fade(1500, 2000), r.fades.getValue(1).fadeOut)
        assertEquals(TransitionRenderPlan.Fade(2000, 2500), r.fades.getValue(2).fadeIn)
        assertEquals(1f, r.fades.getValue(1).factor(1000), .001f)
        assertEquals(.5f, r.fades.getValue(1).factor(1750), .001f)
        assertEquals(0f, r.fades.getValue(2).factor(2000), .001f)
        assertEquals(1f, r.fades.getValue(2).factor(2600), .001f)
    }

    @Test fun `cross fade extends both clips from the source and ramps the second`() {
        val a = clip(1, 0, 2000, 5000, 0)
        val b = clip(2, 1000, 3000, 5000, 2000)
        val r = TransitionRenderPlan.prepare(listOf(Transition(1, 1, 2, 1000, TransitionType.CROSS_FADE)), listOf(a, b))
        val a2 = r.clips.first { it.id == 1L }
        val b2 = r.clips.first { it.id == 2L }
        assertEquals(2500L, a2.endMs)
        assertEquals(1500L, b2.timelineStartMs)
        assertEquals(500L, b2.startMs)
        assertEquals(3000L, b2.endMs)
        assertEquals(TransitionRenderPlan.Fade(1500, 2500), r.fades.getValue(2).fadeIn)
        assertNull(r.fades[1])
    }

    @Test fun `cross fade without source material falls back to a dip`() {
        val a = clip(1, 0, 2000, 2000, 0)
        val b = clip(2, 0, 2000, 2000, 2000)
        val r = TransitionRenderPlan.prepare(listOf(Transition(1, 1, 2, 1000, TransitionType.CROSS_FADE)), listOf(a, b))
        assertEquals(2000L, r.clips.first { it.id == 1L }.endMs)
        assertTrue(r.fades.getValue(1).fadeOut != null)
    }

    @Test fun `non adjacent clips and cuts are ignored`() {
        val a = clip(1, 0, 2000, 2000, 0)
        val b = clip(2, 0, 2000, 2000, 5000)
        val r = TransitionRenderPlan.prepare(listOf(Transition(1, 1, 2, 500, TransitionType.CROSS_FADE), Transition(2, 1, 2, 500, TransitionType.CUT)), listOf(a, b))
        assertTrue(r.fades.isEmpty())
    }

    @Test fun `preview mode ignores cross fades`() {
        val a = clip(1, 0, 2000, 5000, 0)
        val b = clip(2, 1000, 3000, 5000, 2000)
        val r = TransitionRenderPlan.prepare(listOf(Transition(1, 1, 2, 1000, TransitionType.CROSS_FADE)), listOf(a, b), includeCrossFade = false)
        assertTrue(r.fades.isEmpty())
        assertEquals(2000L, r.clips.first { it.id == 1L }.endMs)
    }
}
