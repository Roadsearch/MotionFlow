package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderPlannerTransitionsTest {
    private fun with(type: TransitionType, blend: Boolean = false) = EditorUiState(
        transitions = listOf(Transition(1, 1, 2, 500, type)),
        blendModes = if (blend) mapOf(1L to BlendMode.MULTIPLY) else emptyMap(),
    )

    @Test fun `fades are rendered by Media3`() {
        assertEquals(AdvancedRenderPlanner.Backend.MEDIA3, AdvancedRenderPlanner.decide(with(TransitionType.CROSS_FADE), false).backend)
        assertEquals(AdvancedRenderPlanner.Backend.MEDIA3, AdvancedRenderPlanner.decide(with(TransitionType.FADE_THROUGH), true).backend)
    }

    @Test fun `wipes are refused`() {
        assertEquals(AdvancedRenderPlanner.Backend.UNSUPPORTED, AdvancedRenderPlanner.decide(with(TransitionType.WIPE_LEFT), true).backend)
    }

    @Test fun `fades combined with blend modes are refused rather than silently dropped`() {
        assertEquals(AdvancedRenderPlanner.Backend.UNSUPPORTED, AdvancedRenderPlanner.decide(with(TransitionType.CROSS_FADE, blend = true), true).backend)
    }
}
