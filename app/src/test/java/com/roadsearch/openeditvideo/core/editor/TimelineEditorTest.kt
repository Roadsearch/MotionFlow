package com.roadsearch.openeditvideo.core.editor

import android.net.Uri
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineEditorTest {
    private fun clip(id: Long, start: Long, end: Long, timeline: Long) = VideoClip(
        id = id,
        uri = Uri.parse("content://video/$id"),
        name = "c$id",
        startMs = start,
        endMs = end,
        sourceDurationMs = end,
        timelineStartMs = timeline,
    )

    @Test fun splitCreatesTwoAdjacentClips() {
        val original = clip(1, 0, 10_000, 0)
        val result = TimelineEditor().split(listOf(original), 1, 5_000)
        assertEquals(2, result.size)
        assertEquals(5_000L, result[0].endMs)
        assertEquals(5_000L, result[1].startMs)
        assertEquals(5_000L, result[1].timelineStartMs)
    }

    @Test fun rippleDeleteSeveralSelectedClipsOnlyShiftsOnce() {
        val a = clip(1, 0, 1_000, 0)
        val b = clip(2, 0, 1_000, 1_000)
        val c = clip(3, 0, 1_000, 2_000)
        val d = clip(4, 0, 1_000, 3_000)
        val result = TimelineEditor().rippleDelete(listOf(a, b, c, d), setOf(1, 2))
        assertEquals(listOf(3L, 4L), result.map(VideoClip::id))
        assertEquals(0L, result.first().timelineStartMs)
        assertEquals(1_000L, result[1].timelineStartMs)
    }
}
