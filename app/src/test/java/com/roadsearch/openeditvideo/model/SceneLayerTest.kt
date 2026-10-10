package com.roadsearch.openeditvideo.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneLayerTest {
    @Test
    fun `scene projection normalizes timeline items and null controllers`() {
        val state = EditorUiState(
            clips = listOf(VideoClip(1, Uri.parse("file:///video.mp4"), "Video", startMs = 100, endMs = 1_100, track = 2, timelineStartMs = 400, parentId = 9)),
            audioClips = listOf(AudioClip(2, Uri.parse("file:///audio.mp3"), "Audio", startMs = 200, endMs = 1_200, timelineStartMs = 800)),
            textOverlays = listOf(TextOverlay(3, "Titre", 1_000, 2_000, parentId = 9)),
            nullObjects = listOf(NullObject(9, "Contrôleur")),
        )

        val layers = state.sceneLayers()
        assertEquals(listOf(SceneLayerKind.VIDEO, SceneLayerKind.AUDIO, SceneLayerKind.TEXT, SceneLayerKind.NULL), layers.map { it.kind })
        assertEquals(400L, layers[0].timelineStartMs)
        assertEquals(1_000L, layers[0].durationMs)
        assertEquals(2, layers[0].track)
        assertEquals(9L, layers[0].parentId)
        assertEquals(1_000L, layers[1].durationMs)
        assertEquals(1_000L, layers[2].durationMs)
        assertEquals(9L, layers[2].parentId)
        assertNull(layers[3].timelineStartMs)
        assertNull(layers[3].durationMs)
    }
}
