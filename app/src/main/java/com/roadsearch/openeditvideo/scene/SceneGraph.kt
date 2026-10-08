package com.roadsearch.openeditvideo.scene

import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.TextOverlay
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

    /** Validate the controller hierarchy and clip-to-controller references before rendering or editing. */
    fun validationErrors(clips: List<VideoClip>, nulls: List<NullObject>, texts: List<TextOverlay> = emptyList()): List<String> = buildList {
        val nullIds = nulls.map { it.id }
        if (nullIds.distinct().size != nullIds.size) add("Les identifiants des objets Null doivent être uniques.")
        val knownIds = nullIds.toSet()

        nulls.forEach { node ->
            val parentId = node.parentId ?: return@forEach
            if (parentId !in knownIds) {
                add("Le parent ${parentId} de l'objet Null ${node.id} est introuvable.")
            } else if (wouldCreateCycle(node.id, parentId, nulls)) {
                add("Le parentage des objets Null contient un cycle près de ${node.id}.")
            }
            val chain = ancestors(parentId, nulls)
            if (chain.size >= MAX_DEPTH && chain.lastOrNull()?.parentId != null) {
                add("La hiérarchie des objets Null dépasse la profondeur maximale de $MAX_DEPTH.")
            }
        }

        clips.forEach { clip ->
            val parentId = clip.parentId ?: return@forEach
            if (parentId !in knownIds) {
                add("Le parent ${parentId} du clip ${clip.id} est introuvable.")
            } else {
                val chain = ancestors(parentId, nulls)
                if (chain.size >= MAX_DEPTH && chain.lastOrNull()?.parentId != null) {
                    add("La hiérarchie du clip ${clip.id} dépasse la profondeur maximale de $MAX_DEPTH.")
                }
            }
        }

        texts.forEach { text ->
            val parentId = text.parentId ?: return@forEach
            if (parentId !in knownIds) {
                add("Le parent $parentId du texte ${text.id} est introuvable.")
            } else {
                val chain = ancestors(parentId, nulls)
                if (chain.size >= MAX_DEPTH && chain.lastOrNull()?.parentId != null) {
                    add("La hiérarchie du texte ${text.id} dépasse la profondeur maximale de $MAX_DEPTH.")
                }
            }
        }
    }.distinct()

    fun canParentClip(parentId: Long?, nulls: List<NullObject>): Boolean =
        parentId == null || (
            parentId in nulls.map { it.id }.toSet() &&
                validationErrors(emptyList(), nulls).isEmpty()
            )

    /** A Null can only be parented to a node in a complete, acyclic, bounded controller chain. */
    fun canParentNull(nodeId: Long, parentId: Long?, nulls: List<NullObject>): Boolean {
        if (nulls.map { it.id }.distinct().size != nulls.size) return false
        if (nulls.none { it.id == nodeId }) return false
        if (parentId == null) return true
        if (nulls.none { it.id == parentId } || wouldCreateCycle(nodeId, parentId, nulls)) return false

        val byId = nulls.associateBy { it.id }
        val seen = HashSet<Long>()
        var current = parentId
        var depth = 0
        while (current != null) {
            if (!seen.add(current)) return false
            val node = byId[current] ?: return false
            depth += 1
            if (depth > MAX_DEPTH) return false
            current = node.parentId
        }
        return true
    }
}
