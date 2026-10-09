package com.roadsearch.openeditvideo.core

import android.net.Uri
import com.roadsearch.openeditvideo.core.editor.TimelineOps
import com.roadsearch.openeditvideo.model.AudioClip
import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.MaskSettings
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.TextOverlay
import com.roadsearch.openeditvideo.model.TextStyleSpec
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineValidatorTest {
    private fun clip(id: Long, timeline: Long, length: Long = 1_000L, track: Int = 0) = VideoClip(
        id = id, uri = Uri.parse("content://video/$id"), name = "c$id",
        endMs = length, sourceDurationMs = length, timelineStartMs = timeline, track = track,
    )

    private fun codes(state: EditorUiState) = TimelineValidator.validate(state).map { it.code }

    private val clean = EditorUiState(
        clips = listOf(clip(1, 0), clip(2, 1_000)),
        audioClips = listOf(AudioClip(5, Uri.parse("content://a/5"), "a", endMs = 1_000L, sourceDurationMs = 1_000L)),
        textOverlays = listOf(TextOverlay(9, "hi", 0, 500)),
        transitions = listOf(Transition(10, 1, 2)),
        masks = mapOf(1L to MaskSettings(enabled = true)),
    )

    @Test fun aCleanProjectHasNoIssue() {
        assertEquals(emptyList<String>(), codes(clean))
    }

    @Test fun duplicateIdsAreErrors() {
        assertTrue("DUPLICATE_CLIP_ID" in codes(clean.copy(clips = clean.clips + clip(1, 5_000))))
    }

    @Test fun danglingTransitionIsAnError() {
        val issues = TimelineValidator.validate(clean.copy(transitions = listOf(Transition(10, 1, 404))))
        assertTrue(issues.any { it.code == "DANGLING_TRANSITION" && it.severity == IssueSeverity.ERROR })
    }

    @Test fun orphanClipSettingsAreOnlyAWarning() {
        val issues = TimelineValidator.validate(clean.copy(blendModes = mapOf(404L to BlendMode.ADD)))
        assertTrue(issues.any { it.code == "ORPHAN_CLIP_SETTINGS" && it.severity == IssueSeverity.WARNING })
    }

    @Test fun invalidRangesAreErrors() {
        val bad = clean.copy(
            clips = listOf(clip(1, -5)),
            textOverlays = listOf(TextOverlay(9, "hi", 800, 800)),
        )
        val found = codes(bad)
        assertTrue("NEGATIVE_START" in found)
        assertTrue("INVALID_TEXT_RANGE" in found)
    }

    @Test fun unknownParentIsAnError() {
        val bad = clean.copy(textOverlays = listOf(TextOverlay(9, "hi", 0, 500, parentId = 404)))
        assertTrue("UNKNOWN_PARENT" in codes(bad))
        val withNull = bad.copy(nullObjects = listOf(NullObject(404, "Null", null)))
        assertTrue("UNKNOWN_PARENT" !in codes(withNull))
    }

    @Test fun clipsOverlappingOnTheSameTrackAreFlaggedButAdjacentOnesAreNot() {
        assertTrue("CLIP_OVERLAP" in codes(clean.copy(clips = listOf(clip(1, 0, 2_000), clip(2, 1_000)))))
        assertTrue("CLIP_OVERLAP" !in codes(clean))
    }

    @Test fun textsPrintingOverEachOtherAreFlagged() {
        val a = TextOverlay(1, "A", 0, 1_000)
        val b = TextOverlay(2, "B", 500, 1_500)
        val apart = TextOverlay(3, "C", 500, 1_500, style = TextStyleSpec(posY = .6f))
        assertEquals(1, TimelineValidator.textOverlaps(clean.copy(textOverlays = listOf(a, b))).size)
        assertTrue(TimelineValidator.textOverlaps(clean.copy(textOverlays = listOf(a, apart))).isEmpty())
        assertTrue(TimelineValidator.textOverlaps(clean.copy(textOverlays = listOf(a, TextOverlay(4, "D", 1_000, 2_000)))).isEmpty())
        assertTrue("TEXT_OVERLAP" in codes(clean.copy(textOverlays = listOf(a, b))))
    }

    @Test fun repairDropsLeftoversAndKeepsEverythingElse() {
        val dirty = clean.copy(
            transitions = listOf(Transition(10, 1, 2), Transition(11, 2, 404)),
            masks = mapOf(1L to MaskSettings(enabled = true), 404L to MaskSettings(enabled = true)),
            textOverlays = listOf(TextOverlay(9, "hi", 0, 500, parentId = 404)),
            clips = clean.clips.map { if (it.id == 1L) it.copy(groupId = 3) else it },
        )
        val fixed = TimelineValidator.repair(dirty)
        assertEquals(listOf(10L), fixed.transitions.map { it.id })
        assertEquals(setOf(1L), fixed.masks.keys)
        assertNull(fixed.textOverlays.single().parentId)
        assertNull(fixed.clips.first { it.id == 1L }.groupId)       // single-member group released
        assertEquals(clean.clips.size, fixed.clips.size)
        assertTrue(TimelineValidator.validate(fixed).none { it.severity == IssueSeverity.ERROR })
    }

    @Test fun repairLeavesACleanProjectUntouched() {
        assertEquals(clean, TimelineValidator.repair(clean))
        assertEquals(clean, TimelineOps.normalizeGroups(clean))
    }
}
