package com.roadsearch.openeditvideo.media

import android.content.Context
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import com.roadsearch.openeditvideo.model.MaskSettings

/**
 * GPU mask program. Values are refreshed before every frame, so a future keyframed mask can be
 * evaluated without rebuilding the GL program.
 */
@UnstableApi
class TimeAwareMaskShaderProgram(
    context: Context,
    private val settingsAtFrame: (Long) -> MaskSettings,
) : BaseGlShaderProgram(false, 1) {
    private val program = androidx.media3.common.util.GlProgram(
        context,
        com.roadsearch.openeditvideo.R.raw.vertex_shader_openedit,
        com.roadsearch.openeditvideo.R.raw.fragment_shader_mask,
    )
    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        val identity = GlUtil.create4x4IdentityMatrix()
        program.setFloatsUniform("uTransformationMatrix", identity)
        program.setFloatsUniform("uTexTransformationMatrix", identity)
    }
    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val s = settingsAtFrame(presentationTimeUs / 1000L)
            program.use()
            program.setFloatsUniform("uMaskRect", floatArrayOf(s.x.coerceIn(0f, 1f), s.y.coerceIn(0f, 1f), s.width.coerceIn(0.001f, 1f), s.height.coerceIn(0.001f, 1f)))
            program.setFloatUniform("uFeather", s.feather.coerceIn(0f, 0.5f))
            program.setFloatUniform("uMaskType", s.type.ordinal.toFloat())
            program.setFloatUniform("uInvert", 0f)
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }
    override fun release() { super.release(); try { program.delete() } catch (_: GlUtil.GlException) {} }
}
