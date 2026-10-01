package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FfmpegTimelineCommandBuilderTest {
    @Test fun commandContainsTimelineInputsAndOutputs() {
        val state = EditorUiState(
            clips = listOf(
                VideoClip(1, android.net.Uri.parse("file:///a.mp4"), "A", endMs = 1000, sourceDurationMs = 1000),
                VideoClip(2, android.net.Uri.parse("file:///b.mp4"), "B", endMs = 1000, sourceDurationMs = 1000, track = 1),
            ),
            durationMs = 1000,
        )
        val command = FfmpegTimelineCommandBuilder.build(state, File("/tmp/out.mp4"))
        assertTrue(command.args.contains("-filter_complex"))
        assertTrue(command.args.contains("[vout]"))
        assertTrue(command.reasons.isEmpty())
    }
}
