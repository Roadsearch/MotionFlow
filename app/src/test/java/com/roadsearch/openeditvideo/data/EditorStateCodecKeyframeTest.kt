package com.roadsearch.openeditvideo.data

import android.net.Uri
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.AnimatedProperty
import com.roadsearch.openeditvideo.model.AudioClip
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.effectiveAnimation
import com.roadsearch.openeditvideo.model.keyframes
import com.roadsearch.openeditvideo.model.value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Persistence of keyframes: round trips, curves, static values and projects saved by older versions. */
class EditorStateCodecKeyframeTest {
    private fun clip(animation: TransformAnimation = TransformAnimation(), legacy: List<Keyframe> = emptyList()) = VideoClip(
        id = 1L, uri = Uri.parse("content://video/1"), name = "clip", startMs = 500L, endMs = 4_500L,
        sourceDurationMs = 9_000L, timelineStartMs = 2_000L, keyframes = legacy, animation = animation,
    )

    private fun reload(state: EditorUiState) = EditorStateCodec.decode(EditorStateCodec.encode(state))

    @Test fun `curves keyframes and static values survive save and reload`() {
        val animation = TransformAnimation(
            x = listOf(
                AnimatedKeyframe(1_000L, 10f, Easing.CUBIC_BEZIER, 0.12f, -0.5f, 0.88f, 1.7f),
                AnimatedKeyframe(3_000L, 80f, Easing.BOUNCE),
            ),
            y = listOf(AnimatedKeyframe(2_000L, -30f, Easing.HOLD)),
            scale = listOf(AnimatedKeyframe(1_000L, 1f, Easing.STEPS), AnimatedKeyframe(4_000L, 2.5f)),
            baseRotation = 33f, baseOpacity = 0.6f,
        )
        val state = EditorUiState(clips = listOf(clip(animation)))
        assertEquals(animation, reload(state).clips.single().animation)
    }

    @Test fun `projects without static values keep the version 9 shape and defaults`() {
        val json = EditorStateCodec.encode(EditorUiState(clips = listOf(clip(TransformAnimation(x = listOf(AnimatedKeyframe(0L, 1f)))))))
        assertFalse("untouched static values must not be written", json.contains("\"base\""))
        assertEquals(TransformAnimation(x = listOf(AnimatedKeyframe(0L, 1f))), EditorStateCodec.decode(json).clips.single().animation)
    }

    @Test fun `null controller animation keeps its curves and static values`() {
        val node = NullObject(7L, "Ctl", null, TransformAnimation(
            opacity = listOf(AnimatedKeyframe(0L, 1f, Easing.EASE_OUT), AnimatedKeyframe(500L, 0f)), baseScale = 1.4f,
        ))
        assertEquals(node, reload(EditorUiState(nullObjects = listOf(node))).nullObjects.single())
    }

    @Test fun `audio fades are persisted`() {
        val audio = AudioClip(3L, Uri.parse("content://a/3"), "a", 0L, 5_000L, 5_000L, 1f, 0L, null, fadeInMs = 400L, fadeOutMs = 900L)
        assertEquals(audio, reload(EditorUiState(audioClips = listOf(audio))).audioClips.single())
    }

    @Test fun `a version 9 project without static values loads with neutral defaults`() {
        val v9 = """{"version":9,"clips":[{"id":1,"uri":"content://video/1","start":0,"end":3000,"sourceDuration":3000,
            "animation":{"x":[{"time":0,"value":4,"easing":"EASE_IN","curveX1":0.25,"curveY1":0.1,"curveX2":0.25,"curveY2":1}],
            "y":[],"scale":[],"rotation":[],"opacity":[]}}]}"""
        val loaded = EditorStateCodec.decode(v9).clips.single()
        assertEquals(listOf(AnimatedKeyframe(0L, 4f, Easing.EASE_IN)), loaded.animation.x)
        assertEquals(TransformAnimation().baseScale, loaded.animation.baseScale, 0f)
        assertEquals(1f, loaded.animation.baseOpacity, 0f)
        assertEquals(0f, loaded.animation.value(AnimatedProperty.ROTATION, 100L), 0f)
    }

    @Test fun `old global keyframes are kept and converted to independent lanes on read`() {
        val v3 = """{"version":3,"clips":[{"id":1,"uri":"content://video/1","start":0,"end":4000,"sourceDuration":4000,
            "keyframes":[{"time":0,"x":0,"y":0,"scale":1,"rotation":0,"opacity":1},
                         {"time":2000,"x":100,"y":-40,"scale":2,"rotation":90,"opacity":0.5}]}]}"""
        val loaded = EditorStateCodec.decode(v3).clips.single()
        assertEquals("historical field kept until the clip is edited", 2, loaded.keyframes.size)
        val lanes = loaded.effectiveAnimation()
        assertEquals(listOf(0f, 100f), lanes.keyframes(AnimatedProperty.X).map { it.value })
        assertEquals(listOf(0f, -40f), lanes.keyframes(AnimatedProperty.Y).map { it.value })
        assertEquals(listOf(1f, 2f), lanes.keyframes(AnimatedProperty.SCALE).map { it.value })
        assertEquals(listOf(0f, 90f), lanes.keyframes(AnimatedProperty.ROTATION).map { it.value })
        assertEquals(listOf(1f, .5f), lanes.keyframes(AnimatedProperty.OPACITY).map { it.value })
        assertEquals(50f, lanes.value(AnimatedProperty.X, 1_000L), 0.001f)
    }

    @Test fun `legacy keyframes are not lost by a save and reload`() {
        val legacy = listOf(Keyframe(0L, x = 1f), Keyframe(1_000L, x = 5f, opacity = .25f))
        val loaded = reload(EditorUiState(clips = listOf(clip(legacy = legacy)))).clips.single()
        assertEquals(legacy, loaded.keyframes)
    }

    @Test fun `interpolation stored on the destination keyframe by versions up to 4 moves to the source keyframe`() {
        val v4 = """{"version":4,"clips":[{"id":1,"uri":"content://video/1","start":0,"end":2000,"sourceDuration":2000,
            "animation":{"x":[{"time":0,"value":0,"easing":"LINEAR"},{"time":1000,"value":10,"easing":"EASE_IN"}]}}]}"""
        val x = EditorStateCodec.decode(v4).clips.single().animation.x
        assertEquals(Easing.EASE_IN, x[0].easingToNext)
        assertEquals(Easing.LINEAR, x[1].easingToNext)
    }

    @Test fun `a project written by a newer version is refused, not half read`() {
        try {
            EditorStateCodec.decode("""{"version":11}""")
            fail("expected a refusal")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun `an unreadable document raises instead of producing an empty project`() {
        try {
            EditorStateCodec.decode("{ this is not json")
            fail("expected a decoding error")
        } catch (_: Exception) {
        }
        assertTrue(EditorStateCodec.decode("""{"version":10}""").clips.isEmpty())
    }
}
