package com.roadsearch.openeditvideo.data

import android.net.Uri
import com.roadsearch.openeditvideo.model.AudioClip
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TextOverlay
import com.roadsearch.openeditvideo.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Link groups must survive save -> reload; projects saved before they existed must still load. */
class EditorStateCodecGroupTest {
    private val video = VideoClip(1, Uri.parse("content://v/1"), "v", endMs = 1_000L, sourceDurationMs = 1_000L)
    private val audio = AudioClip(2, Uri.parse("content://a/2"), "a", endMs = 1_000L, sourceDurationMs = 1_000L)
    private val text = TextOverlay(3, "t", 0, 500)

    @Test fun groupIdsRoundTripOnClipsAudioAndText() {
        val state = EditorUiState(
            clips = listOf(video.copy(groupId = 42)),
            audioClips = listOf(audio.copy(groupId = 42)),
            textOverlays = listOf(text.copy(groupId = 42)),
        )
        val decoded = EditorStateCodec.decode(EditorStateCodec.encode(state))
        assertEquals(42L, decoded.clips.single().groupId)
        assertEquals(42L, decoded.audioClips.single().groupId)
        assertEquals(42L, decoded.textOverlays.single().groupId)
    }

    @Test fun elementsWithoutAGroupStayUngrouped() {
        val state = EditorUiState(clips = listOf(video), audioClips = listOf(audio), textOverlays = listOf(text))
        val decoded = EditorStateCodec.decode(EditorStateCodec.encode(state))
        assertNull(decoded.clips.single().groupId)
        assertNull(decoded.audioClips.single().groupId)
        assertNull(decoded.textOverlays.single().groupId)
    }
}
