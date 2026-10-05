package com.roadsearch.openeditvideo.export

object ExportKeys {
    const val PROJECT_ID = "project_id"
    const val OUTPUT_NAME = "output_name"
    const val PROGRESS = "progress"
    const val OUTPUT_URI = "output_uri"
    const val ERROR = "error"
    const val EXPORT_WORK_NAME = "openeditvideo_export"
    const val RESOLUTION = "export_resolution"
    const val FPS = "export_fps"
    const val HIGH_QUALITY = "export_high_quality"
}

/** Output canvas (portrait, matching the editor's 9:16 design space). */
enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    HD("720P", 720, 1280),
    FHD("1080P", 1080, 1920),
    QHD("2K", 1440, 2560),
    UHD("4K", 2160, 3840),
}

/**
 * User-selectable export options. Frame rate is a ceiling (frames are dropped, never invented), so 60 keeps the
 * source rate. HDR and non-MP4 containers are not offered: the GPU effects are SDR-only and Transformer writes MP4.
 */
data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.FHD,
    val fps: Int = 30,
    val highQuality: Boolean = true,
) {
    /** Target video bitrate in bits per second (bits-per-pixel model, clamped to what phone encoders accept). */
    val videoBitrate: Int
        get() {
            val bitsPerPixel = if (highQuality) 0.10 else 0.06
            return (resolution.width.toLong() * resolution.height * fps * bitsPerPixel).toLong()
                .coerceIn(2_000_000L, 80_000_000L).toInt()
        }

    fun estimatedBytes(durationMs: Long): Long =
        ((videoBitrate + AUDIO_BITRATE) / 8.0 * (durationMs / 1000.0)).toLong()

    companion object {
        const val AUDIO_BITRATE = 128_000
        val FPS_OPTIONS = listOf(24, 30, 60)
    }
}
