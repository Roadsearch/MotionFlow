package com.roadsearch.openeditvideo.core.editor

import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.VideoClip

/**
 * State-level timeline commands. [TimelineEditor] only knows about clips; these operations also keep everything that
 * points at a clip (transitions, masks, blend modes, chroma keys, link groups) consistent. Pure and Android-free, so
 * the ViewModel only adds undo, track locks and persistence on top.
 */
object TimelineOps {
    /** Track that plays the role of the main (narrative) track. */
    const val MAIN_TRACK = 0

    private val editor = TimelineEditor()

    private fun <V> Map<Long, V>.copiedTo(from: Long, to: Long): Map<Long, V> {
        val value = this[from] ?: return this
        return this + (to to value)
    }

    private fun Long?.isIn(set: Set<Long>): Boolean = this != null && this in set

    /**
     * Cuts a clip at [positionMs]. Null when the cut is refused (playhead outside the clip or too close to an edge).
     * The right half gets [newId] and inherits the per-clip settings; transitions leaving the clip now leave the right half.
     */
    fun split(state: EditorUiState, clipId: Long, positionMs: Long, newId: Long): EditorUiState? {
        val clips = editor.split(state.clips, clipId, positionMs, newId)
        if (clips === state.clips) return null
        return state.copy(
            clips = clips,
            masks = state.masks.copiedTo(clipId, newId),
            blendModes = state.blendModes.copiedTo(clipId, newId),
            chromaKeys = state.chromaKeys.copiedTo(clipId, newId),
            transitions = state.transitions.map { if (it.fromClipId == clipId) it.copy(fromClipId = newId) else it },
            selectedClipId = newId,
            selectedClipIds = setOf(newId),
        )
    }

    /** Copies a clip right after itself (main track: later clips are pushed back). The copy leaves the link group. */
    fun duplicate(state: EditorUiState, clipId: Long, newId: Long): EditorUiState? {
        val clip = state.clips.firstOrNull { it.id == clipId } ?: return null
        if (state.clips.any { it.id == newId }) return null
        val length = TimelineMath.duration(clip, clip.sourceDurationMs.coerceAtLeast(state.durationMs))
        val copyStart = clip.timelineStartMs + length
        val copy = clip.copy(id = newId, name = "${clip.name} (copie)", timelineStartMs = copyStart, groupId = null)
        val shifted = state.clips.map { c ->
            if (clip.track == MAIN_TRACK && c.track == MAIN_TRACK && c.id != clip.id && c.timelineStartMs >= copyStart) {
                c.copy(timelineStartMs = c.timelineStartMs + length)
            } else c
        }
        val index = shifted.indexOfFirst { it.id == clip.id }
        return state.copy(
            clips = shifted.toMutableList().apply { add(index + 1, copy) },
            masks = state.masks.copiedTo(clipId, newId),
            blendModes = state.blendModes.copiedTo(clipId, newId),
            chromaKeys = state.chromaKeys.copiedTo(clipId, newId),
            selectedClipId = newId,
            selectedClipIds = setOf(newId),
            durationMs = maxOf(state.durationMs, copyStart + length),
        )
    }

    /**
     * Deletes clips. [ripple] = true closes the gap on the main track (Ripple Delete); false leaves a hole (Delete).
     * Transitions, masks, blend modes and chroma keys that belonged to the clips go with them.
     */
    fun delete(state: EditorUiState, ids: Set<Long>, ripple: Boolean): EditorUiState {
        val removed = state.clips.filter { it.id in ids }
        if (removed.isEmpty()) return state
        val remaining = (if (ripple) editor.rippleDelete(state.clips, ids, MAIN_TRACK) else state.clips)
            .filterNot { it.id in ids }
        val anchor = removed.minOf { it.timelineStartMs }
        val main = remaining.filter { it.track == MAIN_TRACK }.sortedBy { it.timelineStartMs }
        val next = main.firstOrNull { it.timelineStartMs >= anchor } ?: main.lastOrNull()
        return normalizeGroups(
            state.copy(
                clips = remaining,
                transitions = state.transitions.filterNot { it.fromClipId in ids || it.toClipId in ids },
                masks = state.masks - ids,
                blendModes = state.blendModes - ids,
                chromaKeys = state.chromaKeys - ids,
                selectedClipId = next?.id,
                selectedClipIds = next?.let { setOf(it.id) } ?: emptySet(),
            )
        )
    }

    /** Lowest overlay track (> main) where a clip of [lengthMs] starting at [startMs] does not collide with another clip. */
    fun freeOverlayTrack(clips: List<VideoClip>, startMs: Long, lengthMs: Long, fallbackDurationMs: Long): Int {
        var track = MAIN_TRACK + 1
        while (true) {
            val busy = clips.any { c ->
                val cStart = c.timelineStartMs
                val cEnd = cStart + TimelineMath.duration(c, c.sourceDurationMs.coerceAtLeast(fallbackDurationMs))
                c.track == track && cStart < startMs + lengthMs && startMs < cEnd
            }
            if (!busy) return track
            track++
        }
    }

    // ---- Link groups ----------------------------------------------------------------------------------------

    /** Group the link group of whatever is selected (text, then audio, then video clip), if any. */
    fun selectedGroupId(state: EditorUiState): Long? {
        state.selectedTextId?.let { id -> return state.textOverlays.firstOrNull { it.id == id }?.groupId }
        state.selectedAudioId?.let { id -> return state.audioClips.firstOrNull { it.id == id }?.groupId }
        return state.clips.firstOrNull { it.id == state.selectedClipId }?.groupId
    }

    /** Links the given elements into group [groupId]. Fewer than two existing members: nothing changes. */
    fun group(state: EditorUiState, clipIds: Set<Long>, audioIds: Set<Long>, textIds: Set<Long>, groupId: Long): EditorUiState {
        val members = state.clips.count { it.id in clipIds } +
            state.audioClips.count { it.id in audioIds } +
            state.textOverlays.count { it.id in textIds }
        if (members < 2) return state
        return normalizeGroups(
            state.copy(
                clips = state.clips.map { if (it.id in clipIds) it.copy(groupId = groupId) else it },
                audioClips = state.audioClips.map { if (it.id in audioIds) it.copy(groupId = groupId) else it },
                textOverlays = state.textOverlays.map { if (it.id in textIds) it.copy(groupId = groupId) else it },
            )
        )
    }

    fun ungroup(state: EditorUiState, groupId: Long): EditorUiState = state.copy(
        clips = state.clips.map { if (it.groupId == groupId) it.copy(groupId = null) else it },
        audioClips = state.audioClips.map { if (it.groupId == groupId) it.copy(groupId = null) else it },
        textOverlays = state.textOverlays.map { if (it.groupId == groupId) it.copy(groupId = null) else it },
    )

    /** A group with fewer than two members is meaningless: its remaining member is released. */
    fun normalizeGroups(state: EditorUiState): EditorUiState {
        val counts = HashMap<Long, Int>()
        state.clips.forEach { c -> c.groupId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        state.audioClips.forEach { a -> a.groupId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        state.textOverlays.forEach { t -> t.groupId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        val orphans = counts.filterValues { it < 2 }.keys
        if (orphans.isEmpty()) return state
        return state.copy(
            clips = state.clips.map { if (it.groupId.isIn(orphans)) it.copy(groupId = null) else it },
            audioClips = state.audioClips.map { if (it.groupId.isIn(orphans)) it.copy(groupId = null) else it },
            textOverlays = state.textOverlays.map { if (it.groupId.isIn(orphans)) it.copy(groupId = null) else it },
        )
    }

    /**
     * False when moving the followers of [groupId] by [deltaMs] would land a video clip on a clip that is not part of
     * the move (same track, overlapping in time). Audio and text may overlap freely, so they never block a move.
     */
    fun followersCanShift(
        state: EditorUiState,
        groupId: Long,
        deltaMs: Long,
        skipClipId: Long? = null,
        lockedTracks: Set<Int> = emptySet(),
    ): Boolean {
        if (deltaMs == 0L) return true
        val moving = state.clips.filter { it.groupId == groupId && it.track !in lockedTracks }
        val movingIds = moving.map { it.id }.toSet() + listOfNotNull(skipClipId)
        fun length(c: VideoClip) = TimelineMath.duration(c, c.sourceDurationMs.coerceAtLeast(state.durationMs))
        return moving.filter { it.id != skipClipId }.all { f ->
            val start = (f.timelineStartMs + deltaMs).coerceAtLeast(0L)
            val end = start + length(f)
            state.clips.none { o -> o.id !in movingIds && o.track == f.track && o.timelineStartMs < end && start < o.timelineStartMs + length(o) }
        }
    }

    /**
     * Moves every follower of [groupId] by [deltaMs] (never before 0; clips on [lockedTracks] stay put). The element
     * the user is dragging is skipped: the caller has already moved it.
     */
    fun shiftMates(
        state: EditorUiState,
        groupId: Long,
        deltaMs: Long,
        skipClipId: Long? = null,
        skipAudioId: Long? = null,
        skipTextId: Long? = null,
        lockedTracks: Set<Int> = emptySet(),
    ): EditorUiState {
        if (deltaMs == 0L) return state
        return state.copy(
            clips = state.clips.map { c ->
                if (c.groupId == groupId && c.id != skipClipId && c.track !in lockedTracks) {
                    c.copy(timelineStartMs = (c.timelineStartMs + deltaMs).coerceAtLeast(0L))
                } else c
            },
            audioClips = state.audioClips.map { a ->
                if (a.groupId == groupId && a.id != skipAudioId) a.copy(timelineStartMs = (a.timelineStartMs + deltaMs).coerceAtLeast(0L)) else a
            },
            textOverlays = state.textOverlays.map { t ->
                if (t.groupId == groupId && t.id != skipTextId) {
                    val start = (t.startMs + deltaMs).coerceAtLeast(0L)
                    t.copy(startMs = start, endMs = start + (t.endMs - t.startMs))
                } else t
            },
        )
    }
}
