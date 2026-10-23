package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.STILL_SOURCE_MS
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FfmpegStillInputTest {
    @Test fun aPhotoIsLoopedBoundedAndHasNoAudioStream() {
        val state = EditorUiState(
            clips = listOf(
                VideoClip(1, android.net.Uri.parse("file:///bg.png"), "bg", endMs = 5_000, sourceDurationMs = STILL_SOURCE_MS),
                VideoClip(2, android.net.Uri.parse("file:///b.mp4"), "B", endMs = 1_000, sourceDurationMs = 1_000, track = 1),
            ),
            durationMs = 5_000,
        )
        val args = FfmpegTimelineCommandBuilder.build(state, File("/tmp/out.mp4")).args
        val loop = args.indexOf("-loop")
        assertTrue(loop >= 0)
        assertEquals(listOf("-loop", "1", "-t", "5.000000", "-i", "file:///bg.png"), args.subList(loop, loop + 6))
        val graph = args[args.indexOf("-filter_complex") + 1]
        assertFalse(graph.contains("[0:a]"))
        assertTrue(graph.contains("[0:v]trim=start=0.000000"))
    }
}
