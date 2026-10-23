package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.TextPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextShadowTest {
    @Test fun neonAndThreeDKeepTheirOwnLook() {
        assertFalse(TextShadow.appliesTo(TextPreset.NEON))
        assertFalse(TextShadow.appliesTo(TextPreset.BOLD3D))
        assertTrue(TextShadow.appliesTo(TextPreset.CLASSIC))
        assertTrue(TextShadow.appliesTo(TextPreset.SCRIPT))
    }

    @Test fun shadowScalesWithTheTextSizeWithAFloor() {
        assertEquals(2f, TextShadow.radius(10f), 1e-4f)
        assertTrue(TextShadow.radius(128f) > TextShadow.radius(64f))
        assertEquals(1.92f, TextShadow.offsetY(64f), 1e-4f)
    }
}
