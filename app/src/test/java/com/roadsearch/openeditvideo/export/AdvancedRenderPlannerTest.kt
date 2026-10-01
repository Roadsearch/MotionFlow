package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedRenderPlannerTest {
    @Test fun `plain project stays on Media3`() {
        val d = AdvancedRenderPlanner.decide(EditorUiState(), ffmpegAvailable = false)
        assertEquals(AdvancedRenderPlanner.Backend.MEDIA3, d.backend)
    }

    @Test fun `advanced blend requests optional backend`() {
        val state = EditorUiState(blendModes = mapOf(1L to BlendMode.MULTIPLY))
        val d = AdvancedRenderPlanner.decide(state, ffmpegAvailable = true)
        assertEquals(AdvancedRenderPlanner.Backend.FFMPEG_OPTIONAL, d.backend)
        assertTrue(d.reasons.any { it.contains("MULTIPLY") })
    }
}
