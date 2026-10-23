package com.roadsearch.openeditvideo.core.editor

import android.net.Uri
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineEditorTrimTest {
    private fun clip(start: Long, end: Long, timeline: Long) = VideoClip(
        id = 1, uri = Uri.parse("content://video/1"), name = "c",
        startMs = start, endMs = end, sourceDurationMs = end, timelineStartMs = timeline,
    )

    private val editor = TimelineEditor()

    @Test fun trimStartMovesTheLeftEdgeToThePlayhead() {
        val trimmed = editor.trimStartAt(clip(0, 10_000, 2_000), positionMs = 6_000, fallbackDurationMs = 0)!!
        assertEquals(4_000L, trimmed.startMs)
        assertEquals(6_000L, trimmed.timelineStartMs)
    }

    @Test fun trimEndMovesTheRightEdgeToThePlayhead() {
        val trimmed = editor.trimEndAt(clip(0, 10_000, 2_000), positionMs = 6_000, fallbackDurationMs = 0)!!
        assertEquals(4_000L, trimmed.endMs)
    }

    @Test fun trimsDoNothingWhenThePlayheadIsOutside() {
        val c = clip(0, 10_000, 2_000)
        assertNull(editor.trimStartAt(c, positionMs = 500, fallbackDurationMs = 0))     // before the clip
        assertNull(editor.trimEndAt(c, positionMs = 50_000, fallbackDurationMs = 0))    // after the clip
    }

    @Test fun trimsNeverShrinkBelowTheMinimumDuration() {
        val c = clip(0, 10_000, 0)
        assertEquals(9_750L, editor.trimStartAt(c, positionMs = 10_000, fallbackDurationMs = 0)!!.startMs)
        assertEquals(250L, editor.trimEndAt(c, positionMs = 0, fallbackDurationMs = 0)?.endMs ?: 250L)
    }

    @Test fun trimmingAVeryShortClipDoesNotThrow() {
        val tiny = clip(0, 100, 0)
        assertNull(editor.trimStartAt(tiny, positionMs = 50, fallbackDurationMs = 0))
        assertNull(editor.trimEndAt(tiny, positionMs = 50, fallbackDurationMs = 0))
        assertNotNull(editor.trimEndAt(clip(0, 1_000, 0), positionMs = 500, fallbackDurationMs = 0))
    }
}
