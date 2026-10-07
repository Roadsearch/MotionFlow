package com.roadsearch.openeditvideo.scene

import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.at
import kotlin.math.cos
import kotlin.math.sin

/**
 * Parent -> child transform inheritance. A layer's transform is expressed relative to its parent null; resolving it
 * composes the whole ancestor chain. Pure math, shared by the exporter (and later the preview).
 *
 * Conventions (same as the compositor): design-space pixels, y down, rotation in degrees counter-clockwise on screen.
 */
object SceneGraph {
    const val MAX_DEPTH = 16

    /** Ancestors of a node whose parent is [parentId], nearest first. Stops on a missing parent or a cycle. */
    fun ancestors(parentId: Long?, nulls: List<NullObject>): List<NullObject> {
        val byId = nulls.associateBy { it.id }
        val chain = ArrayList<NullObject>()
        val seen = HashSet<Long>()
        var next = parentId
        while (next != null && chain.size < MAX_DEPTH) {
            if (!seen.add(next)) break
            val node = byId[next] ?: break
            chain += node
            next = node.parentId
        }
        return chain
    }

    /** [local] composed with every ancestor's transform at timeline time [timeMs]. No parent: [local] unchanged. */
    fun resolve(local: Keyframe, parentId: Long?, nulls: List<NullObject>, timeMs: Long): Keyframe {
        if (parentId == null || nulls.isEmpty()) return local
        var acc = local
        for (node in ancestors(parentId, nulls)) acc = compose(acc, node.animation.at(timeMs), timeMs)
        return acc
    }

    /** One level: the child's transform (in the parent's space) expressed in the parent's parent space. */
    internal fun compose(child: Keyframe, parent: Keyframe, timeMs: Long): Keyframe {
        val r = Math.toRadians(parent.rotation.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val sx = parent.scale * child.x
        val sy = parent.scale * child.y
        return Keyframe(
            timeMs = timeMs,
            x = parent.x + sx * c + sy * s,
            y = parent.y - sx * s + sy * c,
            scale = parent.scale * child.scale,
            rotation = child.rotation + parent.rotation,
            opacity = (child.opacity * parent.opacity).coerceIn(0f, 1f),
        )
    }

    /** True if making [nodeId] a child of [newParentId] would create a loop. */
    fun wouldCreateCycle(nodeId: Long, newParentId: Long?, nulls: List<NullObject>): Boolean {
        if (newParentId == null) return false
        if (newParentId == nodeId) return true
        return ancestors(newParentId, nulls).any { it.id == nodeId }
    }
}
