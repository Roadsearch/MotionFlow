package com.roadsearch.openeditvideo.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSettingsTest {
    @Test
    fun defaultsAre1080pAt30Fps() {
        val s = ExportSettings()
        assertEquals(1080, s.resolution.width)
        assertEquals(1920, s.resolution.height)
        assertEquals(30, s.fps)
    }

    @Test
    fun higherResolutionAndFrameRateMeanBiggerFiles() {
        val low = ExportSettings(ExportResolution.HD, 24, highQuality = false)
        val high = ExportSettings(ExportResolution.UHD, 60, highQuality = true)
        assertTrue(high.estimatedBytes(60_000) > low.estimatedBytes(60_000))
    }

    @Test
    fun highQualityRaisesBitrate() {
        val std = ExportSettings(highQuality = false).videoBitrate
        val hq = ExportSettings(highQuality = true).videoBitrate
        assertTrue(hq > std)
    }

    @Test
    fun estimateScalesLinearlyWithDuration() {
        val s = ExportSettings()
        val diff = kotlin.math.abs(s.estimatedBytes(10_000) * 2 - s.estimatedBytes(20_000))
        assertTrue(diff <= 2L)
    }

    @Test
    fun bitrateIsClamped() {
        val max = ExportSettings(ExportResolution.UHD, 60, true).videoBitrate
        val min = ExportSettings(ExportResolution.HD, 24, false).videoBitrate
        assertTrue(max <= 80_000_000)
        assertTrue(min >= 2_000_000)
    }
}
