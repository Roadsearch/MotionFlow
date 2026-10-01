package com.roadsearch.openeditvideo.media

import androidx.media3.common.Effect
import androidx.media3.effect.*
import androidx.media3.common.util.UnstableApi
import com.roadsearch.openeditvideo.model.VideoFilter

/** V16 built-in GPU effects. Media3 turns GlEffects into GL shader programs. */
@UnstableApi
object AdvancedEffects {
    fun color(filter: VideoFilter): List<Effect> = when (filter) {
        VideoFilter.NONE -> emptyList()
        VideoFilter.CINEMATIC -> listOf(
            Contrast(0.18f),
            HslAdjustment.Builder().adjustSaturation(-8f).adjustLightness(-4f).build()
        )
        VideoFilter.VINTAGE -> listOf(
            Contrast(-0.12f),
            HslAdjustment.Builder().adjustSaturation(-22f).adjustHue(8f).build()
        )
        VideoFilter.COOL -> listOf(
            RgbAdjustment.Builder().setRedScale(0.92f).setBlueScale(1.08f).build(),
            HslAdjustment.Builder().adjustSaturation(4f).build()
        )
        VideoFilter.WARM -> listOf(
            RgbAdjustment.Builder().setRedScale(1.08f).setBlueScale(0.92f).build(),
            HslAdjustment.Builder().adjustSaturation(5f).build()
        )
        VideoFilter.NOIR -> listOf(
            HslAdjustment.Builder().adjustSaturation(-100f).adjustLightness(-3f).build(),
            Contrast(0.22f)
        )
    }

    fun blur(sigma: Float): Effect? = if (sigma > 0.01f) GaussianBlur(sigma) else null
}
