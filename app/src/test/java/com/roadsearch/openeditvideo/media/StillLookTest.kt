package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.EffectSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StillLookTest {
    private val delta = 1e-3f

    @Test fun neutralSettingsNeedNoColourFilter() {
        assertNull(StillLook.colorMatrix(EffectSettings()))
    }

    @Test fun brightnessIsATranslation() {
        val m = StillLook.colorMatrix(EffectSettings(brightness = 0.5f))!!
        assertEquals(1f, m[0], delta); assertEquals(1f, m[6], delta); assertEquals(1f, m[12], delta)
        assertEquals(127.5f, m[4], delta); assertEquals(127.5f, m[9], delta); assertEquals(127.5f, m[14], delta)
    }

    @Test fun contrastScalesAroundMidGrey() {
        val m = StillLook.colorMatrix(EffectSettings(contrast = 0.5f))!!
        assertEquals(1.5f, m[0], delta)
        assertEquals(-63.75f, m[4], delta)
    }

    @Test fun zeroSaturationGivesGreyRowsAndKeepsAlpha() {
        val m = StillLook.colorMatrix(EffectSettings(saturation = 0f))!!
        for (row in 0..2) {
            assertEquals(0.2126f, m[row * 5], delta)
            assertEquals(0.7152f, m[row * 5 + 1], delta)
            assertEquals(0.0722f, m[row * 5 + 2], delta)
        }
        assertEquals(1f, m[18], delta)
    }

    @Test fun adjustmentsAreCombinedInOrder() {
        val m = StillLook.colorMatrix(EffectSettings(saturation = 0f, brightness = 0.2f))!!
        assertNotNull(m)
        assertEquals(51f, m[4], delta) // brightness is applied after the desaturation
    }

    @Test fun outOfRangeValuesAreClamped() {
        val m = StillLook.colorMatrix(EffectSettings(brightness = 9f))!!
        assertEquals(255f, m[4], delta)
    }
}
