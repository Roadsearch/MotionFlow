package com.roadsearch.openeditvideo.core

import com.roadsearch.openeditvideo.core.editor.TimelineOps
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TextOverlay
import kotlin.math.abs

enum class IssueSeverity { ERROR, WARNING }

data class TimelineIssue(val severity: IssueSeverity, val code: String, val message: String)

/** Integrity checks for a project. Pure and Android-free. [repair] fixes what can be fixed without guessing. */
object TimelineValidator {
    /** Two texts closer than this (on the -1..1 vertical axis) print over each other. */
    private const val TEXT_COLLISION_POS_Y = 0.15f

    fun validate(state: EditorUiState): List<TimelineIssue> = buildList {
        fun error(code: String, message: String) = add(TimelineIssue(IssueSeverity.ERROR, code, message))
        fun warning(code: String, message: String) = add(TimelineIssue(IssueSeverity.WARNING, code, message))

        if (state.clips.map { it.id }.let { it.size != it.toSet().size }) error("DUPLICATE_CLIP_ID", "Deux clips vidéo partagent le même identifiant.")
        if (state.audioClips.map { it.id }.let { it.size != it.toSet().size }) error("DUPLICATE_AUDIO_ID", "Deux pistes audio partagent le même identifiant.")
        if (state.textOverlays.map { it.id }.let { it.size != it.toSet().size }) error("DUPLICATE_TEXT_ID", "Deux textes partagent le même identifiant.")
        if (state.nullObjects.map { it.id }.let { it.size != it.toSet().size }) error("DUPLICATE_NULL_ID", "Deux objets de contrôle partagent le même identifiant.")

        val clipIds = state.clips.map { it.id }.toSet()
        val nullIds = state.nullObjects.map { it.id }.toSet()

        state.clips.forEach { c ->
            if (c.timelineStartMs < 0L || c.startMs < 0L) error("NEGATIVE_START", "Le clip « ${c.name} » commence avant 0.")
            if (c.endMs > 0L && c.endMs <= c.startMs) error("INVALID_TRIM", "Le clip « ${c.name} » a une fin antérieure à son début.")
            val parent = c.parentId
            if (parent != null && parent !in nullIds) error("UNKNOWN_PARENT", "Le clip « ${c.name} » dépend d'un objet inexistant.")
        }
        state.audioClips.forEach { a ->
            if (a.timelineStartMs < 0L || a.startMs < 0L) error("NEGATIVE_START", "L'audio « ${a.name} » commence avant 0.")
            if (a.endMs > 0L && a.endMs <= a.startMs) error("INVALID_TRIM", "L'audio « ${a.name} » a une fin antérieure à son début.")
        }
        state.textOverlays.forEach { t ->
            if (t.startMs < 0L) error("NEGATIVE_START", "Un texte commence avant 0.")
            if (t.endMs <= t.startMs) error("INVALID_TEXT_RANGE", "Le texte « ${t.text} » a une durée nulle ou négative.")
            val parent = t.parentId
            if (parent != null && parent !in nullIds) error("UNKNOWN_PARENT", "Le texte « ${t.text} » dépend d'un objet inexistant.")
        }

        state.transitions.forEach { t ->
            if (t.fromClipId !in clipIds || t.toClipId !in clipIds) error("DANGLING_TRANSITION", "Une transition pointe vers un clip supprimé.")
        }
        val orphanState = (state.masks.keys + state.blendModes.keys + state.chromaKeys.keys).filter { it !in clipIds }.toSet()
        if (orphanState.isNotEmpty()) warning("ORPHAN_CLIP_SETTINGS", "${orphanState.size} réglage(s) de masque, fusion ou fond vert sans clip.")

        val groupSizes = HashMap<Long, Int>()
        state.clips.forEach { c -> c.groupId?.let { groupSizes[it] = (groupSizes[it] ?: 0) + 1 } }
        state.audioClips.forEach { a -> a.groupId?.let { groupSizes[it] = (groupSizes[it] ?: 0) + 1 } }
        state.textOverlays.forEach { t -> t.groupId?.let { groupSizes[it] = (groupSizes[it] ?: 0) + 1 } }
        if (groupSizes.any { it.value < 2 }) warning("SINGLE_MEMBER_GROUP", "Un groupe ne contient qu'un seul élément.")

        state.clips.groupBy { it.track }.forEach { (track, onTrack) ->
            val sorted = onTrack.sortedBy { it.timelineStartMs }
            sorted.zipWithNext().forEach { (a, b) ->
                val aEnd = a.timelineStartMs + TimelineMath.duration(a, a.sourceDurationMs.coerceAtLeast(state.durationMs))
                if (b.timelineStartMs < aEnd) warning("CLIP_OVERLAP", "Deux clips se chevauchent sur la piste $track.")
            }
        }

        textOverlaps(state).forEach { (a, b) ->
            warning("TEXT_OVERLAP", "Les textes « ${a.text} » et « ${b.text} » s'affichent en même temps au même endroit.")
        }
    }

    /** Pairs of texts that are visible at the same time, at the same height, under the same parent. */
    fun textOverlaps(state: EditorUiState): List<Pair<TextOverlay, TextOverlay>> {
        val texts = state.textOverlays
        return buildList {
            for (i in texts.indices) for (j in i + 1 until texts.size) {
                val a = texts[i]
                val b = texts[j]
                val simultaneous = a.startMs < b.endMs && b.startMs < a.endMs
                val sameSpot = a.parentId == b.parentId && abs(a.style.posY - b.style.posY) < TEXT_COLLISION_POS_Y
                if (simultaneous && sameSpot) add(a to b)
            }
        }
    }

    /**
     * Drops references to things that no longer exist (transitions, per-clip settings, parents) and releases
     * one-member groups. Projects saved by older builds could carry such leftovers.
     */
    fun repair(state: EditorUiState): EditorUiState {
        val clipIds = state.clips.map { it.id }.toSet()
        val nullIds = state.nullObjects.map { it.id }.toSet()
        val repaired = state.copy(
            clips = state.clips.map { c ->
                val parent = c.parentId
                if (parent != null && parent !in nullIds) c.copy(parentId = null) else c
            },
            textOverlays = state.textOverlays.map { t ->
                val parent = t.parentId
                if (parent != null && parent !in nullIds) t.copy(parentId = null) else t
            },
            transitions = state.transitions.filter { it.fromClipId in clipIds && it.toClipId in clipIds },
            masks = state.masks.filterKeys { it in clipIds },
            blendModes = state.blendModes.filterKeys { it in clipIds },
            chromaKeys = state.chromaKeys.filterKeys { it in clipIds },
        )
        return TimelineOps.normalizeGroups(repaired)
    }
}
