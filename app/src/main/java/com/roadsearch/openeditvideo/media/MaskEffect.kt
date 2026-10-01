package com.roadsearch.openeditvideo.media

import android.content.Context
import androidx.annotation.RawRes
import androidx.media3.common.Effect
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.common.util.GlProgram
import com.roadsearch.openeditvideo.model.MaskSettings
import com.roadsearch.openeditvideo.model.MaskType
import java.io.IOException

@UnstableApi
class MaskEffect(private val settings: MaskSettings) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("MaskEffect currently supports SDR input only")
        return MaskShaderProgram(context, settings)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        !settings.enabled || (settings.x <= 0f && settings.y <= 0f && settings.width >= 1f && settings.height >= 1f && settings.feather <= 0f && settings.type == MaskType.RECTANGLE)
}

@UnstableApi
private class MaskShaderProgram(
    context: Context,
    private val settings: MaskSettings,
) : BaseGlShaderProgram(false, 1) {
    private val program: GlProgram

    init {
        try {
            program = GlProgram(context, VERTEX_SHADER_RES_ID, FRAGMENT_SHADER_RES_ID)
        } catch (e: IOException) {
            throw VideoFrameProcessingException(e)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        val identity = GlUtil.create4x4IdentityMatrix()
        program.setFloatsUniform("uTransformationMatrix", identity)
        program.setFloatsUniform("uTexTransformationMatrix", identity)
        program.setFloatsUniform("uMaskRect", settings.x.coerceIn(0f,1f), settings.y.coerceIn(0f,1f), settings.width.coerceIn(0.001f,1f), settings.height.coerceIn(0.001f,1f))
        program.setFloatUniform("uFeather", settings.feather.coerceIn(0f, 0.5f))
        program.setFloatUniform("uMaskType", settings.type.ordinal.toFloat())
        program.setFloatUniform("uInvert", 0f)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (_: GlUtil.GlException) { }
    }

    companion object {
        // Resource IDs are replaced by the generated resource table at runtime.
        private const val VERTEX_SHADER_RES_ID: Int = com.roadsearch.openeditvideo.R.raw.vertex_shader_openedit
        private const val FRAGMENT_SHADER_RES_ID: Int = com.roadsearch.openeditvideo.R.raw.fragment_shader_mask
    }
}
