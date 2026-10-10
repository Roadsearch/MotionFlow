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

    @Test fun aGeneratedFileNameMapsBackToItsPreset() {
        assertEquals("ocean", ProjectBackgrounds.presetOfFileName("bg_ocean_1080x1920.png")?.id)
        assertEquals("night", ProjectBackgrounds.presetOfFileName("bg_night_1920x1080.png")?.id)
    }

    @Test fun foreignOrUnknownFileNamesAreIgnored() {
        assertEquals(null, ProjectBackgrounds.presetOfFileName("holiday.png"))
        assertEquals(null, ProjectBackgrounds.presetOfFileName("bg_unknown_1080x1920.png"))
        assertEquals(null, ProjectBackgrounds.presetOfFileName(null))
    }
}
