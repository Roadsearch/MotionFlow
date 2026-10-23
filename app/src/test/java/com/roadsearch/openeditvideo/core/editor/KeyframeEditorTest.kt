package com.roadsearch.openeditvideo.core.editor

import android.net.Uri
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.AnimatedProperty
import com.roadsearch.openeditvideo.model.AnimatedProperty.OPACITY
import com.roadsearch.openeditvideo.model.AnimatedProperty.ROTATION
import com.roadsearch.openeditvideo.model.AnimatedProperty.SCALE
import com.roadsearch.openeditvideo.model.AnimatedProperty.X
import com.roadsearch.openeditvideo.model.AnimatedProperty.Y
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.effectiveAnimation
import com.roadsearch.openeditvideo.model.keyframes
import com.roadsearch.openeditvideo.model.staticValue
import com.roadsearch.openeditvideo.model.value
import com.roadsearch.openeditvideo.model.withKeyframes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour of the keyframe commands. Test clip: source 2 000..6 000 ms (trimmed from a 10 s file) placed at
 * 10 000 ms on the timeline, so timeline 10 000..14 000 ms <-> source 2 000..6 000 ms (local = timeline - 8 000).
 */
class KeyframeEditorTest {
    private fun testClip(
        id: Long = 1L,
        at: Long = 10_000L,
        animation: TransformAnimation = TransformAnimation(),
        legacy: List<Keyframe> = emptyList(),
    ) = VideoClip(
        id = id, uri = Uri.parse("content://video/$id"), name = "clip$id",
        startMs = 2_000L, endMs = 6_000L, sourceDurationMs = 10_000L, timelineStartMs = at,
        animation = animation, keyframes = legacy,
    )

    private fun testState(
        clip: VideoClip = testClip(),
        positionMs: Long = 11_000L,
        auto: Boolean = false,
        easing: Easing = Easing.LINEAR,
    ) = EditorUiState(
        clips = listOf(clip), selectedClipId = clip.id, selectedClipIds = setOf(clip.id),
        positionMs = positionMs, durationMs = 10_000L, autoKeyframe = auto, easing = easing,
    )

    private fun EditorUiState.lane(p: AnimatedProperty) = clips.first().effectiveAnimation().keyframes(p)
    private fun EditorUiState.times(p: AnimatedProperty) = lane(p).map { it.timeMs }
    private fun key(t: Long, v: Float, e: Easing = Easing.LINEAR) = AnimatedKeyframe(t, v, e)

    // ---- time conversion ---------------------------------------------------------------------------------------

    @Test fun `timeline time converts to clip source time and back`() {
        val clip = testClip()
        assertEquals(3_000L, KeyframeEditor.localTimeAt(clip, 11_000L, 10_000L))
        assertEquals(11_000L, KeyframeEditor.timelinePositionOf(clip, 3_000L))
    }

    @Test fun `playhead outside the trimmed clip has no local time`() {
        val clip = testClip()
        assertNull(KeyframeEditor.localTimeAt(clip, 9_999L, 10_000L))
        assertNull(KeyframeEditor.localTimeAt(clip, 14_001L, 10_000L))
        assertEquals(2_000L, KeyframeEditor.localTimeAt(clip, 10_000L, 10_000L))
        assertEquals(6_000L, KeyframeEditor.localTimeAt(clip, 14_000L, 10_000L))
    }

    @Test fun `keyframes follow the footage when the clip is moved on the timeline`() {
        val clip = testClip(at = 20_000L, animation = TransformAnimation(x = listOf(key(3_000L, 10f))))
        val state = testState(clip, positionMs = 20_000L)
        assertEquals(21_000L, KeyframeEditor.adjacentKeyframePosition(state, X, forward = true))
    }

    // ---- ◆+ ----------------------------------------------------------------------------------------------------

    @Test fun `add creates exactly one keyframe on the active property only`() {
        val result = KeyframeEditor.addKeyframe(testState(), X)!!
        assertEquals(listOf(key(3_000L, 0f)), result.lane(X))
        listOf(Y, SCALE, ROTATION, OPACITY).forEach { assertTrue("$it must stay empty", result.lane(it).isEmpty()) }
    }

    @Test fun `add holds the value really shown at the playhead`() {
        val animated = TransformAnimation(x = listOf(key(2_000L, 0f), key(6_000L, 100f)), baseScale = 2f)
        val state = testState(testClip(animation = animated), positionMs = 12_000L) // local 4 000 = halfway
        val onX = KeyframeEditor.addKeyframe(state, X)!!
        assertEquals(listOf(2_000L, 4_000L, 6_000L), onX.times(X))
        assertEquals(50f, onX.lane(X)[1].value, 0.001f)
        assertEquals(2f, KeyframeEditor.addKeyframe(state, SCALE)!!.lane(SCALE).single().value, 0.001f)
    }

    @Test fun `add never creates a duplicate at the same instant`() {
        val once = KeyframeEditor.addKeyframe(testState(), X)!!
        assertNull(KeyframeEditor.addKeyframe(once, X))
        assertEquals(1, once.lane(X).size)
    }

    @Test fun `add refuses a playhead outside the visible clip`() {
        assertNull(KeyframeEditor.addKeyframe(testState(positionMs = 9_000L), X))
        assertNull(KeyframeEditor.addKeyframe(testState(positionMs = 15_000L), X))
    }

    @Test fun `add keeps keyframes in chronological order whatever the insertion order`() {
        var state = KeyframeEditor.addKeyframe(testState(positionMs = 13_000L), X)!!
        state = KeyframeEditor.addKeyframe(state.copy(positionMs = 11_000L), X)!!
        state = KeyframeEditor.addKeyframe(state.copy(positionMs = 12_000L), X)!!
        assertEquals(listOf(3_000L, 4_000L, 5_000L), state.times(X))
    }

    @Test fun `add uses the default interpolation`() {
        val result = KeyframeEditor.addKeyframe(testState(easing = Easing.EASE_OUT), X)!!
        assertEquals(Easing.EASE_OUT, result.lane(X).single().easingToNext)
    }

    @Test fun `adding on X never changes the other properties`() {
        val others = TransformAnimation(
            y = listOf(key(3_000L, 7f)), scale = listOf(key(2_000L, 1f), key(6_000L, 2f)),
            rotation = listOf(key(4_000L, 45f)), opacity = listOf(key(2_000L, 1f)),
        )
        val before = testState(testClip(animation = others))
        val after = KeyframeEditor.addKeyframe(before, X)!!
        listOf(Y, SCALE, ROTATION, OPACITY).forEach { assertEquals(before.lane(it), after.lane(it)) }
    }

    @Test fun `add on all properties keys every lane that lacks a keyframe`() {
        val state = testState(testClip(animation = TransformAnimation(x = listOf(key(3_000L, 5f)))))
        val result = KeyframeEditor.addKeyframeOnAllProperties(state)!!
        AnimatedProperty.values().forEach { assertEquals("$it", 1, result.times(it).count { t -> t == 3_000L }) }
        assertEquals(1, result.lane(X).size) // X already had one: no duplicate
        assertNull(KeyframeEditor.addKeyframeOnAllProperties(result))
    }

    // ---- ◆− ----------------------------------------------------------------------------------------------------

    @Test fun `remove deletes only the keyframe under the playhead`() {
        val animation = TransformAnimation(x = listOf(key(3_000L, 1f), key(5_000L, 2f)), y = listOf(key(3_000L, 9f)))
        val result = KeyframeEditor.removeKeyframe(testState(testClip(animation = animation)), X)!!
        assertEquals(listOf(5_000L), result.times(X))
        assertEquals(listOf(3_000L), result.times(Y))
    }

    @Test fun `remove does nothing when there is no keyframe at the playhead`() {
        val animation = TransformAnimation(x = listOf(key(3_000L, 1f)))
        assertNull(KeyframeEditor.removeKeyframe(testState(testClip(animation = animation), positionMs = 12_000L), X))
        assertNull(KeyframeEditor.removeKeyframe(testState(testClip(animation = animation)), Y))
    }

    @Test fun `removing the last keyframe leaves a valid static clip holding that value`() {
        val animation = TransformAnimation(x = listOf(key(3_000L, 42f)), scale = listOf(key(3_000L, 1.5f), key(5_000L, 2f)))
        val result = KeyframeEditor.removeKeyframe(testState(testClip(animation = animation)), X)!!
        val clip = result.clips.single()
        assertTrue(clip.effectiveAnimation().x.isEmpty())
        assertEquals(42f, clip.animation.staticValue(X), 0f)
        listOf(0L, 3_000L, 9_999L).forEach { assertEquals(42f, clip.effectiveAnimation().value(X, it), 0f) }
        assertEquals(2, result.lane(SCALE).size)
    }

    @Test fun `remove on all properties only touches keyframes exactly at the playhead`() {
        val animation = TransformAnimation(x = listOf(key(3_000L, 1f), key(5_000L, 2f)), y = listOf(key(3_000L, 3f)), opacity = listOf(key(5_000L, .5f)))
        val result = KeyframeEditor.removeKeyframesOnAllProperties(testState(testClip(animation = animation)))!!
        assertEquals(listOf(5_000L), result.times(X))
        assertTrue(result.lane(Y).isEmpty())
        assertEquals(listOf(5_000L), result.times(OPACITY))
    }

    @Test fun `clearing one property keeps the displayed value and the other properties`() {
        val animation = TransformAnimation(x = listOf(key(2_000L, 0f), key(6_000L, 100f)), y = listOf(key(3_000L, 3f)))
        val state = testState(testClip(animation = animation), positionMs = 12_000L)
        val cleared = KeyframeEditor.clearKeyframes(state, X)!!
        assertTrue(cleared.lane(X).isEmpty())
        assertEquals(50f, cleared.clips.single().animation.baseX, 0.001f)
        assertEquals(1, cleared.lane(Y).size)
        assertNull(KeyframeEditor.clearKeyframes(cleared, X))
    }

    // ---- hybrid CapCut / Alight Motion value editing -----------------------------------------------------------

    @Test fun `without keyframe and without auto-keyframe an edit is a static change`() {
        val result = KeyframeEditor.editValue(testState(), X, 120f)!!
        assertTrue(result.lane(X).isEmpty())
        assertEquals(120f, result.clips.single().animation.baseX, 0f)
    }

    @Test fun `auto-keyframe turns the first edit into a keyframe`() {
        val result = KeyframeEditor.editValue(testState(auto = true), X, 120f)!!
        assertEquals(listOf(key(3_000L, 120f)), result.lane(X))
    }

    @Test fun `once a keyframe exists an edit at another instant creates a new keyframe`() {
        val state = testState(testClip(animation = TransformAnimation(x = listOf(key(3_000L, 10f)))), positionMs = 13_000L)
        val result = KeyframeEditor.editValue(state, X, 80f)!!
        assertEquals(listOf(3_000L, 5_000L), result.times(X))
        assertEquals(80f, result.lane(X)[1].value, 0f)
        assertEquals(10f, result.lane(X)[0].value, 0f)
    }

    @Test fun `an edit on an existing keyframe updates it and keeps its curve`() {
        val curved = AnimatedKeyframe(3_000L, 10f, Easing.CUBIC_BEZIER, 0.1f, 0.2f, 0.3f, 0.4f)
        val state = testState(testClip(animation = TransformAnimation(x = listOf(curved))))
        val result = KeyframeEditor.editValue(state, X, 55f)!!
        assertEquals(curved.copy(value = 55f), result.lane(X).single())
    }

    @Test fun `a continuous drag at a fixed playhead never creates more than one keyframe`() {
        var state = testState(auto = true)
        for (step in 1..200) state = KeyframeEditor.editValue(state, X, step.toFloat()) ?: state
        assertEquals(1, state.lane(X).size)
        assertEquals(200f, state.lane(X).single().value, 0f)
    }

    @Test fun `editing one property leaves the others untouched`() {
        val animation = TransformAnimation(y = listOf(key(3_000L, 3f)), baseScale = 1.5f)
        val before = testState(testClip(animation = animation), auto = true)
        val after = KeyframeEditor.editValue(before, X, 9f)!!
        assertEquals(before.lane(Y), after.lane(Y))
        assertEquals(1.5f, after.clips.single().animation.baseScale, 0f)
    }

    @Test fun `edited values stay inside what the renderer supports`() {
        val half = testState(testClip(animation = TransformAnimation(baseOpacity = .5f)))
        assertEquals(1f, KeyframeEditor.editValue(half, OPACITY, 7f)!!.clips.single().animation.baseOpacity, 0f)
        assertEquals(0f, KeyframeEditor.editValue(half, OPACITY, -3f)!!.clips.single().animation.baseOpacity, 0f)
        assertEquals(20f, KeyframeEditor.editValue(testState(), SCALE, 99f)!!.clips.single().animation.baseScale, 0f)
        assertNull(KeyframeEditor.editValue(testState(), X, Float.NaN))
    }

    @Test fun `an unchanged value is not an edit`() {
        assertNull(KeyframeEditor.editValue(testState(), X, 0f))
    }

    @Test fun `with keyframes the playhead must be on the clip to edit`() {
        val state = testState(testClip(animation = TransformAnimation(x = listOf(key(3_000L, 1f)))), positionMs = 20_000L)
        assertNull(KeyframeEditor.editValue(state, X, 5f))
    }

    // ---- navigation --------------------------------------------------------------------------------------------

    @Test fun `previous and next walk the active property inside the trimmed clip`() {
        // 1 000 and 7 000 lie outside the visible 2 000..6 000 window.
        val animation = TransformAnimation(x = listOf(key(1_000L, 0f), key(3_000L, 1f), key(5_000L, 2f), key(7_000L, 3f)))
        val clip = testClip(animation = animation)
        assertEquals(11_000L, KeyframeEditor.adjacentKeyframePosition(testState(clip, 10_000L), X, forward = true))
        assertNull(KeyframeEditor.adjacentKeyframePosition(testState(clip, 10_000L), X, forward = false))
        assertEquals(13_000L, KeyframeEditor.adjacentKeyframePosition(testState(clip, 11_000L), X, forward = true))
        assertEquals(11_000L, KeyframeEditor.adjacentKeyframePosition(testState(clip, 13_000L), X, forward = false))
        assertNull(KeyframeEditor.adjacentKeyframePosition(testState(clip, 13_000L), X, forward = true))
    }

    @Test fun `navigation only considers the selected property`() {
        val animation = TransformAnimation(x = listOf(key(3_000L, 1f)), y = listOf(key(5_000L, 1f)))
        val state = testState(testClip(animation = animation), positionMs = 10_000L)
        assertEquals(11_000L, KeyframeEditor.adjacentKeyframePosition(state, X, forward = true))
        assertEquals(13_000L, KeyframeEditor.adjacentKeyframePosition(state, Y, forward = true))
        assertNull(KeyframeEditor.adjacentKeyframePosition(state, OPACITY, forward = true))
    }

    @Test fun `with no neighbouring keyframe navigation reports nothing to do`() {
        val state = testState(testClip(animation = TransformAnimation(x = listOf(key(3_000L, 1f)))), positionMs = 11_000L)
        assertNull(KeyframeEditor.adjacentKeyframePosition(state, X, forward = true))
        assertNull(KeyframeEditor.adjacentKeyframePosition(state, X, forward = false))
    }

    @Test fun `button states reflect the keyframes around the playhead`() {
        val animation = TransformAnimation(x = listOf(key(3_000L, 1f), key(5_000L, 2f)))
        val onKey = KeyframeEditor.info(testState(testClip(animation = animation), 11_000L), X)
        assertTrue(onKey.keyAtPlayhead && onKey.canRemove && !onKey.canAdd && onKey.canGoNext && !onKey.canGoPrevious)
        val between = KeyframeEditor.info(testState(testClip(animation = animation), 12_000L), X)
        assertTrue(between.canAdd && !between.canRemove && between.canGoNext && between.canGoPrevious)
        assertEquals(1.5f, between.value, 0.001f)
        val outside = KeyframeEditor.info(testState(testClip(animation = animation), 9_000L), X)
        assertFalse(outside.canAdd)
        assertFalse(KeyframeEditor.info(EditorUiState(), X).hasClip)
    }

    // ---- curves ------------------------------------------------------------------------------------------------

    @Test fun `a curve applies to its segment only and to its own property only`() {
        val animation = TransformAnimation(
            x = listOf(key(2_000L, 0f), key(4_000L, 10f), key(6_000L, 20f)),
            y = listOf(key(2_000L, 0f), key(6_000L, 5f)),
        )
        val state = testState(testClip(animation = animation))
        val result = KeyframeEditor.setSegmentCurve(state, X, 4_000L, Easing.CUBIC_BEZIER, 0.2f, 0.8f, 0.7f, 0.1f)!!
        assertEquals(Easing.LINEAR, result.lane(X)[0].easingToNext)
        assertEquals(AnimatedKeyframe(4_000L, 10f, Easing.CUBIC_BEZIER, 0.2f, 0.8f, 0.7f, 0.1f), result.lane(X)[1])
        assertEquals(state.lane(Y), result.lane(Y))
    }

    @Test fun `curve handles are clamped and a missing keyframe is refused`() {
        val state = testState(testClip(animation = TransformAnimation(x = listOf(key(2_000L, 0f), key(6_000L, 1f)))))
        val clamped = KeyframeEditor.setSegmentCurve(state, X, 2_000L, Easing.CUBIC_BEZIER, -5f, -9f, 5f, 9f)!!.lane(X)[0]
        assertEquals(0f, clamped.curveX1, 0f); assertEquals(-2f, clamped.curveY1, 0f)
        assertEquals(1f, clamped.curveX2, 0f); assertEquals(3f, clamped.curveY2, 0f)
        assertNull(KeyframeEditor.setSegmentCurve(state, X, 3_333L, Easing.HOLD, 0f, 0f, 1f, 1f))
        assertNull(KeyframeEditor.setSegmentCurve(state, X, 2_000L, Easing.HOLD, Float.NaN, 0f, 1f, 1f))
    }

    @Test fun `the segment around the playhead is the one the presets edit`() {
        val animation = TransformAnimation(x = listOf(key(2_000L, 0f), key(4_000L, 1f), key(6_000L, 2f)))
        assertEquals(2_000L, KeyframeEditor.info(testState(testClip(animation = animation), 11_000L), X).segmentStart?.timeMs)
        assertEquals(4_000L, KeyframeEditor.info(testState(testClip(animation = animation), 13_000L), X).segmentStart?.timeMs)
        // on the last keyframe the segment that ENDS there is shown
        assertEquals(4_000L, KeyframeEditor.info(testState(testClip(animation = animation), 14_000L), X).segmentStart?.timeMs)
        assertNull(KeyframeEditor.info(testState(testClip(animation = TransformAnimation(x = listOf(key(3_000L, 1f))))), X).segmentStart)
    }

    // ---- legacy keyframes --------------------------------------------------------------------------------------

    @Test fun `editing one property migrates legacy keyframes of the others instead of dropping them`() {
        val legacy = listOf(Keyframe(2_000L, x = 5f, scale = 1f), Keyframe(6_000L, x = 10f, scale = 2f, opacity = .5f))
        val state = testState(testClip(legacy = legacy))
        val result = KeyframeEditor.addKeyframe(state, X)!!
        val clip = result.clips.single()
        assertTrue(clip.keyframes.isEmpty())
        assertEquals(3, clip.animation.x.size)
        assertEquals(listOf(2_000L, 6_000L), clip.animation.scale.map { it.timeMs })
        assertEquals(listOf(1f, 2f), clip.animation.scale.map { it.value })
        assertEquals(listOf(1f, .5f), clip.animation.opacity.map { it.value })
    }

    // ---- undo / redo -------------------------------------------------------------------------------------------

    @Test fun `undo and redo restore additions removals and curves`() {
        val history = UndoHistory<EditorUiState>(100)
        val s0 = testState(testClip(animation = TransformAnimation(x = listOf(key(2_000L, 0f), key(6_000L, 10f)))), 11_000L)

        history.record(s0); val s1 = KeyframeEditor.addKeyframe(s0, X)!!
        history.record(s1); val s2 = KeyframeEditor.setSegmentCurve(s1, X, 3_000L, Easing.CUBIC_BEZIER, .1f, .2f, .3f, .4f)!!
        history.record(s2); val s3 = KeyframeEditor.removeKeyframe(s2, X)!!
        assertEquals(2, s3.lane(X).size)

        assertEquals(s2, history.undo(s3)); assertEquals(Easing.CUBIC_BEZIER, s2.lane(X)[1].easingToNext)
        assertEquals(s1, history.undo(s2)); assertEquals(Easing.LINEAR, s1.lane(X)[1].easingToNext)
        assertEquals(s0, history.undo(s1))
        assertNull(history.undo(s0))

        assertEquals(s1, history.redo(s0))
        assertEquals(s2, history.redo(s1))
        assertEquals(s3, history.redo(s2))
        assertNull(history.redo(s3))
    }

    @Test fun `a new edit invalidates the redo branch`() {
        val history = UndoHistory<Int>(10)
        history.record(0); history.record(1)
        assertEquals(1, history.undo(2))
        assertTrue(history.canRedo)
        history.record(5)
        assertFalse(history.canRedo)
    }

    @Test fun `history is bounded and a cancelled gesture is discarded`() {
        val history = UndoHistory<Int>(3)
        (1..10).forEach { history.record(it) }
        assertEquals(10, history.discardLast())
        assertEquals(9, history.undo(0))
        assertEquals(8, history.undo(9))
        assertNull(history.undo(8))
        assertNotNull(history.redo(8))
    }
}
