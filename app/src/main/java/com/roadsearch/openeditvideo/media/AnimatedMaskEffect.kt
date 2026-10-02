package com.roadsearch.openeditvideo.media

import android.content.Context
import androidx.media3.effect.GlEffect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.UnstableApi
import com.roadsearch.openeditvideo.model.MaskSettings

/**
 * Time-aware mask facade. The current Media3 GL implementation uses a stable mask per clip;
 * keyframed mask values are resolved by the editor before this effect is constructed.
 */
@UnstableApi
class AnimatedMaskEffect(private val settingsAtFrame: (Long) -> MaskSettings) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): androidx.media3.effect.BaseGlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("AnimatedMaskEffect: HDR path requires a 16-bit/BT.2020 shader")
        return TimeAwareMaskShaderProgram(context, settingsAtFrame)
    }
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}
