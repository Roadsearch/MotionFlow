package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionPlannerTest {
    @Test fun `cross fade alpha is complementary`() {
        val result = TransitionPlanner.alpha(TransitionType.CROSS_FADE, .25f)
        assertEquals(.75f, result.first, .0001f)
        assertEquals(.25f, result.second, .0001f)
    }

    @Test fun `transition window is centered on boundary`() {
        val t = Transition(1, 2, 3, 500, TransitionType.CROSS_FADE)
        val w = TransitionPlanner.window(t, 5000)
        assertEquals(4750L, w.startMs)
        assertEquals(5250L, w.endMs)
    }
}
