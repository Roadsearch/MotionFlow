package com.roadsearch.openeditvideo.export

/**
 * Pure FFmpeg command builder for operations not covered by the stable Media3 path.
 * It deliberately emits plain arguments so a future self-contained LGPL FFmpeg bridge can execute it.
 */
object FfmpegCommandBuilder {
    fun reverseVideo(input: String, output: String): List<String> = listOf(
        "-y", "-i", input,
        "-vf", "reverse",
        "-af", "areverse",
        // Keep the default fallback LGPL-friendly: avoid x264 here (GPL build).
        // MPEG-4 Part 2 is broadly available in FFmpeg builds and works without GPL codecs.
        "-c:v", "mpeg4",
        "-q:v", "3",
        "-c:a", "aac",
        output,
    )

    fun extractPcmMono(input: String, outputWav: String, sampleRate: Int = 16_000): List<String> = listOf(
        "-y", "-i", input,
        "-vn", "-ac", "1", "-ar", sampleRate.toString(),
        "-c:a", "pcm_s16le",
        outputWav,
    )
}
