package com.roadsearch.openeditvideo.render

import com.roadsearch.openeditvideo.model.BlendMode

/**
 * Contract for the eventual framebuffer compositor. It deliberately stays independent from
 * Media3's one-input GlShaderProgram API, so the render math can be tested now and the Android
 * VideoGraph/NDK backend can be swapped in later without changing the editor model.
 */
interface ProgrammableCompositor {
    fun render(layer: FrameLayer, destination: FrameSurface): FrameSurface
}

data class FrameLayer(
    val textureId: Int,
    val alpha: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
)

data class FrameSurface(val textureId: Int, val width: Int, val height: Int)
