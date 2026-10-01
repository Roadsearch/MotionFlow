package com.roadsearch.openeditvideo.render

import com.roadsearch.openeditvideo.model.BlendMode

/**
 * Pure GLSL catalog for a future two-input framebuffer compositor.
 * This does not pretend that a one-input Media3 GlShaderProgram can sample a destination texture.
 */
object BlendShaderCatalog {
    fun function(mode: BlendMode): String = when (mode) {
        BlendMode.NORMAL -> """
            vec3 blendNormal(vec3 base, vec3 blend) { return blend; }
        """.trimIndent()
        BlendMode.ADD -> """
            vec3 blendAdd(vec3 base, vec3 blend) { return min(base + blend, vec3(1.0)); }
        """.trimIndent()
        BlendMode.MULTIPLY -> """
            vec3 blendMultiply(vec3 base, vec3 blend) { return base * blend; }
        """.trimIndent()
        BlendMode.SCREEN -> """
            vec3 blendScreen(vec3 base, vec3 blend) { return 1.0 - (1.0 - base) * (1.0 - blend); }
        """.trimIndent()
        BlendMode.OVERLAY -> """
            vec3 blendOverlay(vec3 base, vec3 blend) {
                return mix(2.0 * base * blend, 1.0 - 2.0 * (1.0 - base) * (1.0 - blend), step(0.5, base));
            }
        """.trimIndent()
        BlendMode.DARKEN -> """
            vec3 blendDarken(vec3 base, vec3 blend) { return min(base, blend); }
        """.trimIndent()
        BlendMode.LIGHTEN -> """
            vec3 blendLighten(vec3 base, vec3 blend) { return max(base, blend); }
        """.trimIndent()
    }

    fun applyExpression(mode: BlendMode): String = when (mode) {
        BlendMode.NORMAL -> "blendNormal(base.rgb, src.rgb)"
        BlendMode.ADD -> "blendAdd(base.rgb, src.rgb)"
        BlendMode.MULTIPLY -> "blendMultiply(base.rgb, src.rgb)"
        BlendMode.SCREEN -> "blendScreen(base.rgb, src.rgb)"
        BlendMode.OVERLAY -> "blendOverlay(base.rgb, src.rgb)"
        BlendMode.DARKEN -> "blendDarken(base.rgb, src.rgb)"
        BlendMode.LIGHTEN -> "blendLighten(base.rgb, src.rgb)"
    }
}
