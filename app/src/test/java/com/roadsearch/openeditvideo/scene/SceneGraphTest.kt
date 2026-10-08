package com.roadsearch.openeditvideo.scene

import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneGraphTest {
    private fun key(v: Float) = listOf(AnimatedKeyframe(0L, v, Easing.LINEAR))
    private fun anim(x: Float = 0f, y: Float = 0f, scale: Float = 1f, rot: Float = 0f, op: Float = 1f) =
        TransformAnimation(key(x), key(y), key(scale), key(rot), key(op))
    private fun node(id: Long, parent: Long? = null, a: TransformAnimation = anim()) = NullObject(id, "n$id", parent, a)

    @Test fun `no parent returns the local transform untouched`() {
        val local = Keyframe(0, 10f, 5f, 2f, 30f, .5f)
        assertSame(local, SceneGraph.resolve(local, null, listOf(node(1)), 0))
    }

    @Test fun `parent translation offsets the child`() {
        val r = SceneGraph.resolve(Keyframe(0, 10f, 5f), 1, listOf(node(1, a = anim(x = 50f, y = 20f))), 0)
        assertEquals(60f, r.x, .001f); assertEquals(25f, r.y, .001f)
    }

    @Test fun `parent scale scales the child offset and size`() {
        val r = SceneGraph.resolve(Keyframe(0, 10f, 5f, scale = 1.5f), 1, listOf(node(1, a = anim(scale = 2f))), 0)
        assertEquals(20f, r.x, .001f); assertEquals(10f, r.y, .001f); assertEquals(3f, r.scale, .001f)
    }

    @Test fun `parent rotation turns the child counter clockwise on screen`() {
        val r = SceneGraph.resolve(Keyframe(0, 100f, 0f, rotation = 10f), 1, listOf(node(1, a = anim(rot = 90f))), 0)
        assertEquals(0f, r.x, .01f); assertEquals(-100f, r.y, .01f); assertEquals(100f, r.rotation, .001f)
    }

    @Test fun `opacity multiplies down the chain`() {
        val r = SceneGraph.resolve(Keyframe(0, opacity = .5f), 2, listOf(node(1, a = anim(op = .5f)), node(2, 1, anim(op = .5f))), 0)
        assertEquals(.125f, r.opacity, .001f)
    }

    @Test fun `two level chain composes translations`() {
        val nulls = listOf(node(1, a = anim(x = 100f)), node(2, 1, anim(x = 10f, y = 4f)))
        val r = SceneGraph.resolve(Keyframe(0, 1f, 1f), 2, nulls, 0)
        assertEquals(111f, r.x, .001f); assertEquals(5f, r.y, .001f)
    }

    @Test fun `missing parent is ignored`() {
        val local = Keyframe(0, 7f, 7f)
        val r = SceneGraph.resolve(local, 99, listOf(node(1, a = anim(x = 100f))), 0)
        assertEquals(7f, r.x, .001f)
    }

    @Test fun `cycles do not loop forever and are detected`() {
        val nulls = listOf(node(1, 2), node(2, 1))
        SceneGraph.resolve(Keyframe(0, 1f, 1f), 1, nulls, 0)
        assertTrue(SceneGraph.wouldCreateCycle(1, 2, listOf(node(1), node(2, 1))))
        assertTrue(SceneGraph.wouldCreateCycle(1, 1, listOf(node(1))))
        assertFalse(SceneGraph.wouldCreateCycle(2, 1, listOf(node(1), node(2))))
        assertFalse(SceneGraph.wouldCreateCycle(1, null, listOf(node(1))))
    }

    @Test fun `graph validation reports missing clip parents`() {
        val clip = VideoClip(7, android.net.Uri.parse("content://clip/7"), "child", parentId = 99)
        assertTrue(SceneGraph.validationErrors(listOf(clip), listOf(node(1))).any { it.contains("parent 99") })
    }

    @Test fun `graph validation reports cyclic null controllers`() {
        val cyclic = listOf(node(1, 2), node(2, 1))
        assertTrue(SceneGraph.validationErrors(emptyList(), cyclic).any { it.contains("cycle") })
        assertFalse(SceneGraph.canParentClip(1, cyclic))
    }

    @Test fun `clips can only be parented to a known null in a valid graph`() {
        val valid = listOf(node(1), node(2, 1))
        assertTrue(SceneGraph.canParentClip(2, valid))
        assertFalse(SceneGraph.canParentClip(3, valid))
        assertTrue(SceneGraph.canParentClip(null, valid))
    }

    @Test fun `null parenting rejects cycles and accepts detaching`() {
        val valid = listOf(node(1), node(2, 1), node(3))
        assertFalse(SceneGraph.canParentNull(1, 2, valid))
        assertTrue(SceneGraph.canParentNull(3, 2, valid))
        assertTrue(SceneGraph.canParentNull(2, null, valid))
        assertFalse(SceneGraph.canParentNull(3, 99, valid))
    }

    @Test fun `deep null hierarchies are rejected`() {
        val chain = (1L..(SceneGraph.MAX_DEPTH + 2L)).map { id -> node(id, if (id == 1L) null else id - 1L) }
        assertTrue(SceneGraph.validationErrors(emptyList(), chain).any { it.contains("profondeur maximale") })
    }
}
