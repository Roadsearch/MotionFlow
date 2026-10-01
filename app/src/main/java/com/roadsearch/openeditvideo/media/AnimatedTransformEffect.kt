package com.roadsearch.openeditvideo.media

import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.valueAt
import kotlin.math.max

/**
 * Time-varying Media3 transformation. The effect is evaluated for every encoded/preview frame,
 * so the same keyframe curve can drive ExoPlayer and Transformer.
 * Coordinates are expressed in the editor's 1080x1920 design space and mapped to NDC.
 */
class AnimatedTransformEffect(
    private val animation: TransformAnimation,
    private val sourceStartMs: Long = 0L,
    private val designWidth: Float = 1080f,
    private val designHeight: Float = 1920f,
) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val sourceTimeMs = sourceStartMs + max(0L, presentationTimeUs / 1000L)
        val x = animation.x.valueAt(sourceTimeMs, 0f)
        val y = animation.y.valueAt(sourceTimeMs, 0f)
        val scale = animation.scale.valueAt(sourceTimeMs, 1f)
        val rotation = animation.rotation.valueAt(sourceTimeMs, 0f)

        return Matrix().apply {
            // Media3 MatrixTransformation operates in normalized device coordinates.
            postTranslate(x / (designWidth / 2f), -y / (designHeight / 2f))
            postScale(scale, scale)
            postRotate(rotation)
        }
    }
}
