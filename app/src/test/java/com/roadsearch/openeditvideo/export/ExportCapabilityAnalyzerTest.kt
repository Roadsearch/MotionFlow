package com.roadsearch.openeditvideo.export

import android.net.Uri
import com.roadsearch.openeditvideo.model.BlendMode
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

    @Test fun allowsSecondaryVideoTracks() {
        val errors = ExportCapabilityAnalyzer.errors(EditorUiState(clips = listOf(clip(1, 0), clip(2, 0).copy(track = 1))))
        assertTrue(errors.isEmpty())
    }

    @Test fun allowsAGapInTheMainTrackRenderedAsBlack() {
        val errors = ExportCapabilityAnalyzer.errors(EditorUiState(clips = listOf(clip(1, 0), clip(2, 2_000))))
        assertTrue(errors.isEmpty())
    }

    private fun transitionState(type: TransitionType, withBlend: Boolean = false) = EditorUiState(
        clips = listOf(clip(1, 0), clip(2, 1_000)),
        transitions = listOf(Transition(1, 1, 2, 500, type)),
        blendModes = if (withBlend) mapOf(1L to BlendMode.MULTIPLY) else emptyMap(),
    )

    @Test fun rejectsWipeTransitionsAtCapabilityGate() {
        val errors = ExportCapabilityAnalyzer.errors(transitionState(TransitionType.WIPE_LEFT))
        assertTrue(errors.any { it.contains("wipe") })
    }

    @Test fun rejectsFadeTransitionsCombinedWithAdvancedBlendModes() {
        val errors = ExportCapabilityAnalyzer.errors(transitionState(TransitionType.CROSS_FADE, withBlend = true))
        assertTrue(errors.any { it.contains("mode de fusion") })
    }

    @Test fun allowsMedia3FadeTransitionsThroughCapabilityGate() {
        val state = EditorUiState(
            clips = listOf(clip(1, 0), clip(2, 1_000)),
            transitions = listOf(Transition(1, 1, 2, 500, TransitionType.FADE_THROUGH)),
        )
        assertTrue(ExportCapabilityAnalyzer.errors(state).isEmpty())
    }
}
