package com.roadsearch.openeditvideo.media

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.Saturation
import androidx.media3.effect.ScaleAndRotateTransformation
import com.roadsearch.openeditvideo.model.EffectSettings
import com.roadsearch.openeditvideo.model.VideoFilter

/** Builds the GPU effect chain shared by preview and export. */
@UnstableApi
object GpuEffectFactory {
    fun build(settings: EffectSettings, rotation: Float = 0f, opacity: Float = 1f): List<Effect> = buildList {
        val totalRotation = rotation + settings.rotation
        if (totalRotation != 0f) {
            add(ScaleAndRotateTransformation.Builder().setRotationDegrees(totalRotation).build())
        }
        if (settings.brightness != 0f) add(Brightness(settings.brightness.coerceIn(-1f, 1f)))
        if (settings.contrast != 0f) add(Contrast(settings.contrast.coerceIn(-1f, 1f)))
        if (settings.saturation != 1f) add(Saturation(settings.saturation.coerceIn(0f, 2f)))
        if (settings.hue != 0f) add(HslAdjustment.Builder().adjustHue(settings.hue).build())
        if (settings.blur > 0.01f) add(GaussianBlur(settings.blur.coerceAtLeast(0f)))
        if (opacity < 0.999f) add(AlphaScale(opacity.coerceIn(0f, 1f)))
        if (settings.filter == VideoFilter.NOIR) add(RgbFilter.createGrayscaleFilter())
        addAll(AdvancedEffects.color(settings.filter))
    }
}
