package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FfmpegTimelineCommandBuilderTest {
    @Test fun audioClipTrimsSourceAndAppliesFadesBeforeTimelineDelay() {
        val state = EditorUiState(
            clips = listOf(
                VideoClip(1, android.net.Uri.parse("file:///a.mp4"), "A", endMs = 6000, sourceDurationMs = 6000),
            ),
            audioClips = listOf(
                AudioClip(
                    id = 2,
                    uri = android.net.Uri.parse("file:///music.wav"),
                    name = "Music",
                    startMs = 2000,
                    endMs = 5000,
                    sourceDurationMs = 10000,
                    volume = 1.5f,
                    timelineStartMs = 4000,
                    fadeInMs = 1000,
                    fadeOutMs = 1000,
                ),
            ),
            durationMs = 6000,
        )
        val graph = FfmpegTimelineCommandBuilder.build(state, File("/tmp/out.mp4"))
            .args.let { args -> args[args.indexOf("-filter_complex") + 1] }

        assertTrue(graph.contains("[1:a]atrim=start=2.000000:duration=3.000000,asetpts=PTS-STARTPTS"))
        assertTrue(graph.contains("volume='1.5*max(0,min(1,min((t/1.000000),((3.000000-t)/1.000000))))'"))
        assertTrue(graph.contains("adelay=4000:all=1"))
    }

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
