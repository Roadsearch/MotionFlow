package com.roadsearch.openeditvideo.media

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.roadsearch.openeditvideo.R
import java.io.IOException

/**
 * GPU chroma-key effect. Uses RGB distance + smooth threshold in the fragment shader.
 * It is intentionally SDR-only for now; HDR falls back to an explicit error.
 */
@UnstableApi
class ChromaKeyEffect(
    private val red: Float,
    private val green: Float,
    private val blue: Float,
    private val threshold: Float = 0.18f,
    private val softness: Float = 0.08f,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("ChromaKeyEffect currently supports SDR input only")
        return Program(context)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = threshold <= 0f

    private inner class Program(context: Context) : BaseGlShaderProgram(false, 1) {
        private val program: GlProgram
        init {
            try {
                program = GlProgram(context, R.raw.vertex_shader_openedit, R.raw.fragment_shader_chroma_key)
            } catch (e: IOException) {
                throw VideoFrameProcessingException(e)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            program.setFloatUniform("uKeyR", red.coerceIn(0f, 1f))
            program.setFloatUniform("uKeyG", green.coerceIn(0f, 1f))
            program.setFloatUniform("uKeyB", blue.coerceIn(0f, 1f))
            program.setFloatUniform("uThreshold", threshold.coerceIn(0f, 1f))
            program.setFloatUniform("uSoftness", softness.coerceIn(0.001f, 1f))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try { program.delete() } catch (_: GlUtil.GlException) { }
        }
    }
}
