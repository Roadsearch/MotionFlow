package com.roadsearch.openeditvideo.media

import com.roadsearch.openeditvideo.model.EffectSettings

/**
 * Colour adjustments of a still (brightness, contrast, saturation) as a 4x5 colour matrix, Android layout
 * (translation in 0..255), so the preview of a photo matches what the export applies. Null = nothing to apply.
 * Hue, blur and the named filters are only applied at export.
 */
object StillLook {
    private const val RW = 0.2126f
    private const val GW = 0.7152f
    private const val BW = 0.0722f

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun colorMatrix(effects: EffectSettings): FloatArray? {
        val brightness = effects.brightness.coerceIn(-1f, 1f)
        val contrast = effects.contrast.coerceIn(-1f, 1f)
        val saturation = effects.saturation.coerceIn(0f, 2f)
        if (brightness == 0f && contrast == 0f && saturation == 1f) return null
        var m = IDENTITY.copyOf()
        m = multiply(saturationMatrix(saturation), m)
        m = multiply(contrastMatrix(contrast), m)
        m = multiply(brightnessMatrix(brightness), m)
        return m
    }

    private fun saturationMatrix(s: Float) = floatArrayOf(
        RW * (1 - s) + s, GW * (1 - s), BW * (1 - s), 0f, 0f,
        RW * (1 - s), GW * (1 - s) + s, BW * (1 - s), 0f, 0f,
        RW * (1 - s), GW * (1 - s), BW * (1 - s) + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun contrastMatrix(contrast: Float): FloatArray {
        val k = 1f + contrast
        val t = 127.5f * (1f - k)
        return floatArrayOf(
            k, 0f, 0f, 0f, t,
            0f, k, 0f, 0f, t,
            0f, 0f, k, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun brightnessMatrix(brightness: Float): FloatArray {
        val t = brightness * 255f
        return floatArrayOf(
            1f, 0f, 0f, 0f, t,
            0f, 1f, 0f, 0f, t,
            0f, 0f, 1f, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Matrix that applies [b] first, then [a]. */
    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (r in 0 until 4) for (c in 0 until 5) {
            var v = 0f
            for (k in 0 until 4) v += a[r * 5 + k] * b[k * 5 + c]
            if (c == 4) v += a[r * 5 + 4]
            out[r * 5 + c] = v
        }
        return out
    }
}
