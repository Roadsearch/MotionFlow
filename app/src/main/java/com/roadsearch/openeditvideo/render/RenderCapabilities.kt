package com.roadsearch.openeditvideo.render

import android.os.Build

data class RenderCapabilities(
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val hdrEditing: Boolean = apiLevel >= 33,
    val gpuCompositing: Boolean = true,
) {
    fun supportsCustomHdrGlEffects(): Boolean = hdrEditing && apiLevel >= 33
}

enum class RenderBackend { MEDIA3_GPU, MEDIA3_HDR, FFMPEG_OPTIONAL, UNSUPPORTED }
