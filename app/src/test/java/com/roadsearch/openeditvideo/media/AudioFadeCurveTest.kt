package com.roadsearch.openeditvideo.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFadeCurveTest {
    @Test fun noFadesPreserveClipVolume() {
        assertEquals(0.75f, AudioFadeCurve.gainAt(5_000, 1_000, 10_000, .75f, 0, 0), .0001f)
    }

    @Test fun fadeInStartsSilentAndReachesFullVolume() {
        assertEquals(0f, AudioFadeCurve.gainAt(0, 1_000, 10_000, 1f, 2_000, 0), .0001f)
        assertEquals(.5f, AudioFadeCurve.gainAt(1_000, 1_000, 10_000, 1f, 2_000, 0), .0001f)
        assertEquals(1f, AudioFadeCurve.gainAt(2_000, 1_000, 10_000, 1f, 2_000, 0), .0001f)
    }

    @Test fun fadeOutReachesSilenceAtClipEnd() {
        assertEquals(1f, AudioFadeCurve.gainAt(8_000, 1_000, 10_000, 1f, 0, 2_000), .0001f)
        assertEquals(.5f, AudioFadeCurve.gainAt(9_000, 1_000, 10_000, 1f, 0, 2_000), .0001f)
        assertEquals(0f, AudioFadeCurve.gainAt(10_000, 1_000, 10_000, 1f, 0, 2_000), .0001f)
    }

    @Test fun overlappingFadesNeverExceedClipVolume() {
        assertEquals(.25f, AudioFadeCurve.gainAt(5_000, 1_000, 10_000, .5f, 8_000, 8_000), .0001f)
    }
}
