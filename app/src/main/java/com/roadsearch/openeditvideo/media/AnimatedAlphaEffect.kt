package com.roadsearch.openeditvideo.media

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.roadsearch.openeditvideo.R
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.valueAt
import java.io.IOException

/** Time-varying alpha effect used by both ExoPlayer preview and Transformer export. */
@UnstableApi
class AnimatedAlphaEffect(
    private val keyframes: List<AnimatedKeyframe>,
    private val sourceStartMs: Long = 0L,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("AnimatedAlphaEffect currently supports SDR input only")
        return Program(context)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = keyframes.isEmpty() ||
        keyframes.all { it.value >= 0.999f }

    private inner class Program(context: Context) : BaseGlShaderProgram(false, 1) {
        private val program: GlProgram

        init {
            try {
                program = GlProgram(context, R.raw.vertex_shader_openedit, R.raw.fragment_shader_alpha)
            } catch (e: IOException) {
                throw VideoFrameProcessingException(e)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val sourceTimeMs = sourceStartMs + (presentationTimeUs / 1000L).coerceAtLeast(0L)
                val alpha = keyframes.valueAt(sourceTimeMs, 1f).coerceIn(0f, 1f)
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uAlpha", alpha)
                program.bindAttributesAndUniforms()
                android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (_: GlUtil.GlException) {
                // Best-effort GL cleanup.
            }
        }
    }
}
