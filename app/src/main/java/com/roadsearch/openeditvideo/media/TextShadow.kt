package com.roadsearch.openeditvideo.media

import android.text.TextPaint
import android.text.style.CharacterStyle
import com.roadsearch.openeditvideo.model.TextPreset

/** Soft dark drop shadow that keeps text readable on busy footage; one recipe for the preview and the export. */
object TextShadow {
    val COLOR: Int = 0x99000000.toInt()

    fun radius(size: Float): Float = (size * 0.09f).coerceAtLeast(2f)

    fun offsetY(size: Float): Float = size * 0.03f

    /** Neon and 3D bring their own look; the other presets get the soft shadow. */
    fun appliesTo(preset: TextPreset): Boolean = preset != TextPreset.NEON && preset != TextPreset.BOLD3D
}

/** Applies [TextShadow] to a run of text rendered by Android's text layout (used for the exported text bitmap). */
class TextShadowSpan(private val radius: Float, private val dy: Float, private val color: Int) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) {
        tp.setShadowLayer(radius, 0f, dy, color)
    }
}
