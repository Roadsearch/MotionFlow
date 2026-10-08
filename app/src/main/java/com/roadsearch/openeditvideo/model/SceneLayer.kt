package com.roadsearch.openeditvideo.model

import kotlinx.serialization.Serializable

/** Common read model for timeline items and transform controllers. The editable legacy models remain authoritative. */
@Serializable
enum class SceneLayerKind { VIDEO, AUDIO, TEXT, NULL }

/** A normalized view used by scene and composition code without changing the persisted clip/audio/text schemas. */
@Serializable
data class SceneLayer(
    val id: Long,
    val name: String,
    val kind: SceneLayerKind,
    val timelineStartMs: Long? = null,
    val durationMs: Long? = null,
    val track: Int? = null,
    val parentId: Long? = null,
    val animation: TransformAnimation = TransformAnimation(),
)

/** Build a stable common layer projection from existing editor state. */
fun EditorUiState.sceneLayers(): List<SceneLayer> = buildList {
    clips.forEach { clip ->
        add(SceneLayer(
            id = clip.id,
            name = clip.name,
            kind = SceneLayerKind.VIDEO,
            timelineStartMs = clip.timelineStartMs,
            durationMs = (clip.end(durationMs) - clip.startMs).coerceAtLeast(0L),
            track = clip.track,
            parentId = clip.parentId,
            animation = clip.animation,
        ))
    }
    audioClips.forEach { audio ->
        add(SceneLayer(
            id = audio.id,
            name = audio.name,
            kind = SceneLayerKind.AUDIO,
            timelineStartMs = audio.timelineStartMs,
            durationMs = audio.lengthMs(),
        ))
    }
    textOverlays.forEach { overlay ->
        add(SceneLayer(
            id = overlay.id,
            name = overlay.text,
            kind = SceneLayerKind.TEXT,
            timelineStartMs = overlay.startMs,
            durationMs = (overlay.endMs - overlay.startMs).coerceAtLeast(0L),
            parentId = overlay.parentId,
            animation = overlay.animation,
        ))
    }
    nullObjects.forEach { node ->
        add(SceneLayer(
            id = node.id,
            name = node.name,
            kind = SceneLayerKind.NULL,
            parentId = node.parentId,
            animation = node.animation,
        ))
    }
}
