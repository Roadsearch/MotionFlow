package com.roadsearch.openeditvideo.media

import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation
import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.valueAt
import kotlin.math.max

/**
 * Time-varying Media3 transformation, evaluated for every preview frame.
 * The preview feeds it through [resolvedTransformAt] (ClipTransform, parents included). It is NOT part of the export
 * effect chain: the export compositor applies the same transform once, see ClipTransform.overlayAt.
 * Coordinates are expressed in the editor's 1080x1920 design space and mapped to NDC.
 */
class AnimatedTransformEffect(
    private val animation: TransformAnimation,
    private val sourceStartMs: Long = 0L,
    private val designWidth: Float = 1080f,
    private val designHeight: Float = 1920f,
    private val resolvedTransformAt: ((Long) -> Keyframe)? = null,
) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val sourceTimeMs = sourceStartMs + max(0L, presentationTimeUs / 1000L)
        val resolved = resolvedTransformAt?.invoke(sourceTimeMs)
        val x = resolved?.x ?: animation.x.valueAt(sourceTimeMs, animation.baseX)
        val y = resolved?.y ?: animation.y.valueAt(sourceTimeMs, animation.baseY)
        val scale = resolved?.scale ?: animation.scale.valueAt(sourceTimeMs, animation.baseScale)
        val rotation = resolved?.rotation ?: animation.rotation.valueAt(sourceTimeMs, animation.baseRotation)

        return Matrix().apply {
            // Media3 MatrixTransformation operates in normalized device coordinates.
            postTranslate(x / (designWidth / 2f), -y / (designHeight / 2f))
            postScale(scale, scale)
            postRotate(rotation)
        }
    }
}
