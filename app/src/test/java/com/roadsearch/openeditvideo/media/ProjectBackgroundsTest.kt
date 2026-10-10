package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.AspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectBackgroundsTest {
    @Test fun presetsHaveUniqueIdsAndAtLeastOneColour() {
        val ids = ProjectBackgrounds.presets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ProjectBackgrounds.presets.all { it.colors.isNotEmpty() })
        assertTrue(ProjectBackgrounds.presets.size >= 8)
    }

    @Test fun solidAndGradientPresetsAreDistinguished() {
        assertTrue(ProjectBackgrounds.presets.any { !it.isGradient })
        assertTrue(ProjectBackgrounds.presets.any { it.isGradient })
    }

    @Test fun renderedSizeMatchesTheAspectRatioExactly() {
        AspectRatio.entries.forEach { aspect ->
            val (w, h) = ProjectBackgrounds.sizeFor(aspect)
            assertEquals(w.toLong() * aspect.h, h.toLong() * aspect.w)
        }
    }
}
