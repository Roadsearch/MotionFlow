package com.roadsearch.openeditvideo.export

import android.net.Uri
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A backend that cannot render a transform must refuse the export instead of producing a wrong video. */
class KeyframeExportGateTest {
    private fun clip(id: Long, animation: TransformAnimation = TransformAnimation(), parentId: Long? = null) = VideoClip(
        id = id, uri = Uri.parse("content://video/$id"), name = "c$id", startMs = 0L, endMs = 1_000L,
        sourceDurationMs = 1_000L, timelineStartMs = 0L, track = (id - 1).toInt(), animation = animation, parentId = parentId,
    )

    private fun blended(animation: TransformAnimation, nulls: List<NullObject> = emptyList(), parentId: Long? = null) = EditorUiState(
        clips = listOf(clip(1L), clip(2L, animation, parentId)),
        blendModes = mapOf(2L to BlendMode.MULTIPLY),
        nullObjects = nulls,
    )

    @Test fun `ffmpeg blend without any transform is still routed to ffmpeg`() {
        val decision = AdvancedRenderPlanner.decide(blended(TransformAnimation()), ffmpegAvailable = true)
        assertEquals(AdvancedRenderPlanner.Backend.FFMPEG_OPTIONAL, decision.backend)
    }

    @Test fun `ffmpeg refuses keyframed clips`() {
        val animation = TransformAnimation(x = listOf(AnimatedKeyframe(0L, 0f), AnimatedKeyframe(500L, 100f)))
        val decision = AdvancedRenderPlanner.decide(blended(animation), ffmpegAvailable = true)
        assertEquals(AdvancedRenderPlanner.Backend.UNSUPPORTED, decision.backend)
        assertTrue(decision.reasons.any { it.contains("FFmpeg") })
    }

    @Test fun `ffmpeg refuses static position scale rotation and opacity`() {
        listOf(
            TransformAnimation(baseX = 10f), TransformAnimation(baseY = -5f), TransformAnimation(baseScale = 1.2f),
            TransformAnimation(baseRotation = 15f), TransformAnimation(baseOpacity = 0.5f),
        ).forEach { animation ->
            assertEquals("$animation", AdvancedRenderPlanner.Backend.UNSUPPORTED, AdvancedRenderPlanner.decide(blended(animation), true).backend)
        }
    }

    @Test fun `ffmpeg refuses a clip moved by an animated parent controller`() {
        val parent = NullObject(5L, "p", null, TransformAnimation(opacity = listOf(AnimatedKeyframe(0L, 1f), AnimatedKeyframe(500L, 0f))))
        val decision = AdvancedRenderPlanner.decide(blended(TransformAnimation(), listOf(parent), parentId = 5L), true)
        assertEquals(AdvancedRenderPlanner.Backend.UNSUPPORTED, decision.backend)
    }

    @Test fun `media3 export of keyframed clips is not blocked`() {
        val animation = TransformAnimation(x = listOf(AnimatedKeyframe(0L, 0f), AnimatedKeyframe(500L, 100f)))
        val state = EditorUiState(clips = listOf(clip(1L), clip(2L, animation)))
        assertEquals(AdvancedRenderPlanner.Backend.MEDIA3, AdvancedRenderPlanner.decide(state, ffmpegAvailable = false).backend)
        assertTrue(ExportCapabilityAnalyzer.errors(state).isEmpty())
    }

    @Test fun `capability gate counts static transforms and parents for advanced blends`() {
        val staticScale = ExportCapabilityAnalyzer.errors(blended(TransformAnimation(baseScale = 2f)))
        assertTrue(staticScale.any { it.contains("MULTIPLY") })
        val parent = NullObject(5L, "p", null, TransformAnimation(baseRotation = 20f))
        val inherited = ExportCapabilityAnalyzer.errors(blended(TransformAnimation(), listOf(parent), parentId = 5L))
        assertTrue(inherited.any { it.contains("MULTIPLY") })
        assertTrue(ExportCapabilityAnalyzer.errors(blended(TransformAnimation())).isEmpty())
    }
}
