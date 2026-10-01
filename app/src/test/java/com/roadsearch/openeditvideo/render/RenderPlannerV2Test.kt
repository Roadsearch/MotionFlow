package com.roadsearch.openeditvideo.render

import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderPlannerV2Test {
    @Test fun normalTimelineUsesMedia3() {
        val p = RenderPlannerV2.plan(EditorUiState(), RenderCapabilities(apiLevel = 32), false)
        assertEquals(RenderBackend.MEDIA3_GPU, p.backend)
    }
    @Test fun advancedBlendRequiresAdvancedBackend() {
        val p = RenderPlannerV2.plan(EditorUiState(blendModes = mapOf(1L to BlendMode.MULTIPLY)), RenderCapabilities(apiLevel = 32), false)
        assertEquals(RenderBackend.UNSUPPORTED, p.backend)
    }
}
