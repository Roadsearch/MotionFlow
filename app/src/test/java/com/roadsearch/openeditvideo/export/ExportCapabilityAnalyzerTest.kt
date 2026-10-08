package com.roadsearch.openeditvideo.export

import android.net.Uri
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransitionType
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCapabilityAnalyzerTest {
    private fun clip(id: Long, timeline: Long) = VideoClip(
        id = id,
        uri = Uri.parse("content://video/$id"),
        name = "clip$id",
        startMs = 0,
        endMs = 1_000,
        sourceDurationMs = 1_000,
        timelineStartMs = timeline,
    )

    @Test fun rejectsSecondaryVideoTrack() {
        val errors = ExportCapabilityAnalyzer.errors(EditorUiState(clips = listOf(clip(1, 0).copy(track = 1))))
        assertTrue(errors.any { it.contains("V2/V3+") })
    }

    @Test fun rejectsTimelineGap() {
        val errors = ExportCapabilityAnalyzer.errors(EditorUiState(clips = listOf(clip(1, 0), clip(2, 2_000))))
        assertTrue(errors.any { it.contains("trou") })
    }

    @Test fun allowsMedia3FadeTransitionsThroughCapabilityGate() {
        val state = EditorUiState(
            clips = listOf(clip(1, 0), clip(2, 1_000)),
            transitions = listOf(Transition(1, 1, 2, 500, TransitionType.FADE_THROUGH)),
        )
        assertTrue(ExportCapabilityAnalyzer.errors(state).isEmpty())
    }
}
