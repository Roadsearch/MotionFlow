package com.roadsearch.openeditvideo.core.editor

import android.net.Uri
import com.roadsearch.openeditvideo.model.AudioClip
import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.ChromaKeySettings
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.MaskSettings
import com.roadsearch.openeditvideo.model.TextOverlay
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOpsTest {
    private fun clip(id: Long, timeline: Long, length: Long = 1_000L, track: Int = 0, group: Long? = null) = VideoClip(
        id = id,
        uri = Uri.parse("content://video/$id"),
        name = "c$id",
        startMs = 0L,
        endMs = length,
        sourceDurationMs = length,
        timelineStartMs = timeline,
        track = track,
        groupId = group,
    )

    private fun audio(id: Long, timeline: Long, group: Long? = null) = AudioClip(
        id = id, uri = Uri.parse("content://audio/$id"), name = "a$id",
        startMs = 0L, endMs = 1_000L, sourceDurationMs = 1_000L, timelineStartMs = timeline, groupId = group,
    )

    private fun state(vararg clips: VideoClip) = EditorUiState(clips = clips.toList())

    // ---- split ----------------------------------------------------------------------------------------------

    @Test fun splitRewiresTransitionsAndCopiesPerClipSettings() {
        val s = state(clip(1, 0, 4_000), clip(2, 4_000, 4_000), clip(3, 8_000, 4_000)).copy(
            transitions = listOf(Transition(10, 1, 2), Transition(11, 2, 3)),
            masks = mapOf(2L to MaskSettings(enabled = true)),
            blendModes = mapOf(2L to BlendMode.ADD),
            chromaKeys = mapOf(2L to ChromaKeySettings(enabled = true)),
        )
        val result = TimelineOps.split(s, 2, 5_000, newId = 99)!!
        assertEquals(listOf(1L, 2L, 99L, 3L), result.clips.map { it.id })
        assertEquals(99L, result.selectedClipId)
        // The transition entering the clip still ends on the left half; the one leaving it now starts at the right half.
        assertEquals(2L, result.transitions.first { it.id == 10L }.toClipId)
        assertEquals(99L, result.transitions.first { it.id == 11L }.fromClipId)
        assertTrue(result.masks.containsKey(99L))
        assertEquals(BlendMode.ADD, result.blendModes[99L])
        assertTrue(result.chromaKeys.getValue(99L).enabled)
    }

    @Test fun splitIsRefusedOutsideTheClipOrTooCloseToAnEdge() {
        val s = state(clip(1, 1_000, 4_000))
        assertNull(TimelineOps.split(s, 1, 0, 50))        // playhead before the clip
        assertNull(TimelineOps.split(s, 1, 1_000, 50))    // exactly on the start
        assertNull(TimelineOps.split(s, 1, 1_100, 50))    // closer than the minimum duration
        assertNull(TimelineOps.split(s, 1, 5_000, 50))    // exactly on the end
        assertNull(TimelineOps.split(s, 1, 60_000, 50))   // far after the clip
        assertNotNull(TimelineOps.split(s, 1, 3_000, 50))
    }

    @Test fun splitOfAVeryShortClipDoesNotThrow() {
        assertNull(TimelineOps.split(state(clip(1, 0, 100)), 1, 50, 2))
    }

    @Test fun repeatedSplitsKeepIdsUnique() {
        var s = state(clip(1, 0, 4_000))
        s = TimelineOps.split(s, 1, 1_000, 100)!!
        s = TimelineOps.split(s, 100, 2_500, 101)!!
        s = TimelineOps.split(s, 101, 3_300, 102)!!
        val ids = s.clips.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(4, ids.size)
    }

    @Test fun splitRefusesAnIdThatIsAlreadyUsed() {
        assertNull(TimelineOps.split(state(clip(1, 0, 4_000), clip(2, 4_000)), 1, 2_000, newId = 2))
    }

    // ---- delete / ripple delete --------------------------------------------------------------------------------

    @Test fun rippleDeleteOfTwoSeparatedClipsShiftsSurvivorsCorrectly() {
        val s = state(clip(1, 0), clip(2, 1_000), clip(3, 2_000), clip(4, 3_000))
        val result = TimelineOps.delete(s, setOf(1, 3), ripple = true)
        assertEquals(listOf(2L, 4L), result.clips.map { it.id })
        assertEquals(0L, result.clips[0].timelineStartMs)
        assertEquals(1_000L, result.clips[1].timelineStartMs)
    }

    @Test fun deleteWithoutRippleLeavesAGap() {
        val s = state(clip(1, 0), clip(2, 1_000), clip(3, 2_000))
        val result = TimelineOps.delete(s, setOf(2), ripple = false)
        assertEquals(listOf(1L, 3L), result.clips.map { it.id })
        assertEquals(2_000L, result.clips[1].timelineStartMs)
    }

    @Test fun deletingAnOverlayClipActuallyRemovesIt() {
        val s = state(clip(1, 0), clip(2, 0, track = 1))
        val result = TimelineOps.delete(s, setOf(2), ripple = true)
        assertEquals(listOf(1L), result.clips.map { it.id })
    }

    @Test fun deletePurgesEverythingThatPointedAtTheClip() {
        val s = state(clip(1, 0), clip(2, 1_000), clip(3, 2_000)).copy(
            transitions = listOf(Transition(10, 1, 2), Transition(11, 2, 3)),
            masks = mapOf(2L to MaskSettings(enabled = true), 3L to MaskSettings(enabled = true)),
            blendModes = mapOf(2L to BlendMode.SCREEN),
            chromaKeys = mapOf(2L to ChromaKeySettings(enabled = true)),
        )
        val result = TimelineOps.delete(s, setOf(2), ripple = true)
        assertTrue(result.transitions.isEmpty())
        assertEquals(setOf(3L), result.masks.keys)
        assertTrue(result.blendModes.isEmpty())
        assertTrue(result.chromaKeys.isEmpty())
    }

    @Test fun deleteSelectsTheClipThatTakesItsPlace() {
        val s = state(clip(1, 0), clip(2, 1_000), clip(3, 2_000))
        val result = TimelineOps.delete(s, setOf(2), ripple = true)
        assertEquals(3L, result.selectedClipId)
        assertNull(TimelineOps.delete(state(clip(1, 0)), setOf(1), ripple = true).selectedClipId)
    }

    // ---- duplicate ---------------------------------------------------------------------------------------------

    @Test fun duplicateCopiesSettingsAndPushesFollowersBack() {
        val s = state(clip(1, 0, group = 7), clip(2, 1_000)).copy(masks = mapOf(1L to MaskSettings(enabled = true)))
        val result = TimelineOps.duplicate(s, 1, newId = 50)!!
        assertEquals(listOf(1L, 50L, 2L), result.clips.map { it.id })
        assertEquals(1_000L, result.clips[1].timelineStartMs)
        assertEquals(2_000L, result.clips[2].timelineStartMs)
        assertTrue(result.masks.containsKey(50L))
        assertNull(result.clips[1].groupId)
        assertEquals(50L, result.selectedClipId)
    }

    // ---- overlay placement ---------------------------------------------------------------------------------------

    @Test fun overlayGoesToTheFirstTrackWithoutACollision() {
        val clips = listOf(clip(1, 0, 5_000), clip(2, 0, 1_000, track = 1))
        assertEquals(2, TimelineOps.freeOverlayTrack(clips, 500, 1_000, 0))
        assertEquals(1, TimelineOps.freeOverlayTrack(clips, 1_000, 1_000, 0))
        assertEquals(1, TimelineOps.freeOverlayTrack(listOf(clip(1, 0, 5_000)), 0, 1_000, 0))
    }

    // ---- link groups -------------------------------------------------------------------------------------------

    @Test fun groupLinksVideoAudioAndTextAndMatesFollow() {
        val s = state(clip(1, 0)).copy(
            audioClips = listOf(audio(5, 0)),
            textOverlays = listOf(TextOverlay(9, "hi", 0, 500)),
        )
        val grouped = TimelineOps.group(s, setOf(1), setOf(5), setOf(9), groupId = 77)
        assertEquals(77L, grouped.clips[0].groupId)
        assertEquals(77L, grouped.audioClips[0].groupId)
        assertEquals(77L, grouped.textOverlays[0].groupId)

        val moved = TimelineOps.shiftMates(grouped, 77, 300, skipClipId = 1)
        assertEquals(0L, moved.clips[0].timelineStartMs)
        assertEquals(300L, moved.audioClips[0].timelineStartMs)
        assertEquals(300L, moved.textOverlays[0].startMs)
        assertEquals(800L, moved.textOverlays[0].endMs)
    }

    @Test fun matesNeverGoBeforeZeroAndLockedTracksStayPut() {
        val s = state(clip(1, 400, group = 7), clip(2, 400, track = 1, group = 7)).copy(audioClips = listOf(audio(5, 300, group = 7)))
        val moved = TimelineOps.shiftMates(s, 7, -500, skipClipId = 1, lockedTracks = setOf(1))
        assertEquals(400L, moved.clips[0].timelineStartMs)   // skipped: the caller moved it
        assertEquals(400L, moved.clips[1].timelineStartMs)   // locked track
        assertEquals(0L, moved.audioClips[0].timelineStartMs)
    }

    @Test fun groupNeedsTwoMembers() {
        val s = state(clip(1, 0))
        assertNull(TimelineOps.group(s, setOf(1), emptySet(), emptySet(), 7).clips[0].groupId)
    }

    @Test fun deletingOneMemberReleasesTheLastOne() {
        val s = state(clip(1, 0, group = 7), clip(2, 1_000, group = 7))
        val result = TimelineOps.delete(s, setOf(2), ripple = false)
        assertNull(result.clips.single().groupId)
    }

    @Test fun ungroupReleasesEveryone() {
        val s = state(clip(1, 0, group = 7), clip(2, 1_000, group = 7))
        val result = TimelineOps.ungroup(s, 7)
        assertTrue(result.clips.all { it.groupId == null })
    }

    @Test fun selectedGroupIdFollowsTheSelection() {
        val s = state(clip(1, 0, group = 7)).copy(selectedClipId = 1)
        assertEquals(7L, TimelineOps.selectedGroupId(s))
        assertNull(TimelineOps.selectedGroupId(s.copy(selectedClipId = null)))
    }

    // ---- group moves and collisions ----------------------------------------------------------------------------

    @Test fun aGroupMoveIsRefusedWhenAFollowerWouldLandOnAnotherClip() {
        val s = state(
            clip(1, 0, group = 7),
            clip(2, 0, track = 1, group = 7),
            clip(3, 1_500, track = 1),
        )
        assertTrue(TimelineOps.followersCanShift(s, 7, 400, skipClipId = 1))    // 400..1400 stays clear of 1500
        assertTrue(!TimelineOps.followersCanShift(s, 7, 800, skipClipId = 1))   // 800..1800 hits the clip at 1500
    }

    @Test fun lockedTracksAndZeroMovesNeverBlock() {
        val s = state(clip(1, 0, group = 7), clip(2, 0, track = 1, group = 7), clip(3, 500, track = 1))
        assertTrue(TimelineOps.followersCanShift(s, 7, 0, skipClipId = 1))
        assertTrue(TimelineOps.followersCanShift(s, 7, 300, skipClipId = 1, lockedTracks = setOf(1)))
    }

    @Test fun followersDoNotBlockEachOtherOrTheDraggedClip() {
        val s = state(clip(1, 0, group = 7), clip(2, 1_000, group = 7), clip(3, 2_000, group = 7))
        assertTrue(TimelineOps.followersCanShift(s, 7, 600, skipClipId = 1))
    }
}
