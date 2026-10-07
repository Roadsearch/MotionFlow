package com.roadsearch.openeditvideo.data

import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.AspectRatio
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.MaskSettings
import com.roadsearch.openeditvideo.model.MaskType
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.TextOverlay
import com.roadsearch.openeditvideo.model.TextPreset
import com.roadsearch.openeditvideo.model.TextStyleSpec
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Save -> reload guard for the project-level fields (clips need android.net.Uri, so they are covered on device). */
class EditorStateCodecTest {
    @Test fun `round trip keeps the project level fields`() {
        val key = { v: Float -> listOf(AnimatedKeyframe(0L, v, Easing.LINEAR)) }
        val state = EditorUiState(
            aspect = AspectRatio.LANDSCAPE,
            coverMs = 1234L,
            textOverlays = listOf(TextOverlay(1, "Salut", 100, 900, TextStyleSpec(TextPreset.NEON, "bebas", 0xFFFF4FD8.toInt(), 80f, .4f))),
            transitions = listOf(Transition(7, 1, 2, 600, TransitionType.CROSS_FADE)),
            nullObjects = listOf(
                NullObject(5, "Master", null, TransformAnimation(key(10f), key(20f), key(1.5f), key(30f), key(.8f))),
                NullObject(6, "Child", 5),
            ),
            masks = mapOf(3L to MaskSettings(enabled = true, type = MaskType.ELLIPSE, feather = .1f, invert = true)),
        )
        val back = EditorStateCodec.decode(EditorStateCodec.encode(state))
        assertEquals(state.aspect, back.aspect)
        assertEquals(state.coverMs, back.coverMs)
        assertEquals(state.textOverlays, back.textOverlays)
        assertEquals(state.transitions, back.transitions)
        assertEquals(state.nullObjects, back.nullObjects)
        assertEquals(state.masks, back.masks)
    }

    @Test fun `projects written before the new fields still load with defaults`() {
        val old = EditorStateCodec.decode("""{"version":4}""")
        assertEquals(AspectRatio.PORTRAIT, old.aspect)
        assertEquals(0L, old.coverMs)
        assertTrue(old.nullObjects.isEmpty())
    }

    @Test fun `newer project versions are refused instead of being half read`() {
        try {
            EditorStateCodec.decode("""{"version":999}""")
            fail("expected a refusal")
        } catch (_: IllegalArgumentException) {
        }
    }
}
